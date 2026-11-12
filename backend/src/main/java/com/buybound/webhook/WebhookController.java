package com.buybound.webhook;

import com.buybound.audit.AuditService;
import com.buybound.common.exception.ConflictException;
import com.buybound.common.exception.UnauthorizedException;
import com.buybound.payment.PayPalPaymentAdapter;
import com.buybound.purchase.domain.IntentStatus;
import com.buybound.purchase.domain.PurchaseIntent;
import com.buybound.purchase.repository.PurchaseIntentRepository;
import com.buybound.watch.domain.Watch;
import com.buybound.watch.domain.WatchStatus;
import com.buybound.watch.repository.WatchRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * PayPal webhook receiver with signature verification and event deduplication.
 *
 * <p>Verifies webhook signatures via PayPal's notification verification API.
 * Deduplicates events by provider event ID using the database UNIQUE constraint.
 * Processes PAYMENT.CAPTURE.COMPLETED (reconciles intent) and
 * VAULT.PAYMENT-TOKEN.DELETED (suspends watches) events.
 */
@RestController
@RequestMapping("/api/webhooks/paypal")
public class WebhookController {
  private static final Logger log = LoggerFactory.getLogger(WebhookController.class);
  private static final long MAX_WEBHOOK_AGE_SECONDS = 300;

  private final ProviderWebhookEventRepository repository;
  private final PurchaseIntentRepository intents;
  private final WatchRepository watches;
  private final AuditService audit;
  private final Optional<PayPalPaymentAdapter> payPalAdapter;

  @Value("${app.paypal.webhook-id:}")
  private String webhookId;

  @Value("${app.paypal.base-url:https://api-m.sandbox.paypal.com}")
  private String paypalBaseUrl;

  @Value("${app.paypal.webhook-test-hmac-secret:}")
  private String webhookTestHmacSecret;

  public WebhookController(ProviderWebhookEventRepository repository,
                            PurchaseIntentRepository intents,
                            WatchRepository watches,
                            AuditService audit) {
    this(repository, intents, watches, audit, Optional.empty());
  }

  @Autowired
  public WebhookController(ProviderWebhookEventRepository repository,
                            PurchaseIntentRepository intents,
                            WatchRepository watches,
                            AuditService audit,
                            Optional<PayPalPaymentAdapter> payPalAdapter) {
    this.repository = repository;
    this.intents = intents;
    this.watches = watches;
    this.audit = audit;
    this.payPalAdapter = payPalAdapter != null ? payPalAdapter : Optional.empty();
  }

  @PostMapping
  public ResponseEntity<Map<String, Object>> receive(
      @RequestBody Map<String, Object> payload,
      @RequestHeader(value = "PAYPAL-TRANSMISSION-ID", required = false) String transmissionId,
      @RequestHeader(value = "PAYPAL-TRANSMISSION-TIME", required = false) String transmissionTime,
      @RequestHeader(value = "PAYPAL-TRANSMISSION-SIG", required = false) String transmissionSig,
      @RequestHeader(value = "PAYPAL-CERT-URL", required = false) String certUrl,
      @RequestHeader(value = "PAYPAL-AUTH-ALGO", required = false) String authAlgo,
      @RequestHeader(value = "X-BuyBound-Webhook-Test-Sig", required = false) String testMockSig) {

    // Gate check: webhook must be configured
    if (webhookId == null || webhookId.isBlank()) {
      throw new ConflictException(
          "PayPal webhook not configured: set PAYPAL_WEBHOOK_ID and complete Gate 0 "
              + "signature-verification before accepting events.");
    }

    // Extract event ID
    Object eventId = payload.get("id");
    if (eventId == null || eventId.toString().isBlank()) {
      throw new IllegalArgumentException("Webhook payload missing provider event id");
    }
    String providerEventId = eventId.toString();

    // Extract event type
    Object eventTypeObj = payload.get("event_type");
    String eventType = eventTypeObj != null ? eventTypeObj.toString() : "UNKNOWN";

    // Deduplication: skip if already received
    Optional<ProviderWebhookEvent> existing = repository.findByProviderEventId(providerEventId);
    if (existing.isPresent()) {
      log.info("Duplicate webhook event {} — already processed, skipping", providerEventId);
      return ResponseEntity.ok(Map.of(
          "status", "already_processed",
          "eventId", providerEventId));
    }

    // Verify signature via PayPal verification API (fail closed)
    if (!verifySignature(transmissionId, transmissionTime, transmissionSig,
        certUrl, authAlgo, testMockSig, payload)) {
      log.warn("Webhook signature verification FAILED for event {}", providerEventId);
      throw new UnauthorizedException(
          "Webhook signature verification failed. Event not trusted.");
    }

    // Persist event before processing
    ProviderWebhookEvent event = new ProviderWebhookEvent(
        providerEventId, eventType, payload.toString());
    repository.save(event);

    // Process based on event type
    processEvent(eventType, payload, providerEventId);

    event.setProcessed(true);
    repository.save(event);

    audit.record("webhook.processed", "webhook_event", providerEventId,
        providerEventId, null, null,
        "PayPal webhook " + eventType + " processed successfully");

    return ResponseEntity.ok(Map.of(
        "status", "processed",
        "eventId", providerEventId,
        "eventType", eventType));
  }

  // ------------------------------------------------------------------ signature verification

  /**
   * Verifies webhook signature using PayPal's notification verification endpoint with OAuth2 Bearer auth
   * and enforces a 5-minute replay window on PAYPAL-TRANSMISSION-TIME. Strictly fails closed.
   */
  private boolean verifySignature(String transmissionId, String transmissionTime,
                                   String transmissionSig, String certUrl,
                                   String authAlgo, String testMockSig, Map<String, Object> payload) {
    String eventId = payload.getOrDefault("id", "unknown").toString();

    if (isValidTestMockSignature(eventId, transmissionTime, testMockSig)) {
      return true;
    }

    // Fail closed if any PayPal signature header is missing.
    if (isBlank(transmissionId) || isBlank(transmissionTime)
        || isBlank(transmissionSig) || isBlank(certUrl) || isBlank(authAlgo)) {
      log.warn("Webhook rejected (fail-closed): incomplete PayPal signature headers for event {}", eventId);
      audit.record("webhook.signature_incomplete", "webhook_event",
          eventId, eventId, "WEBHOOK_SIGNATURE_MISSING", null,
          "Rejected webhook received without complete PayPal signature headers.");
      return false;
    }

    // Replay defense: reject transmission timestamps older than 5 minutes (300 seconds).
    Instant transmittedAt = parseTransmissionTime(transmissionTime);
    if (transmittedAt == null) {
      log.warn("Webhook rejected (fail-closed): unparseable PAYPAL-TRANSMISSION-TIME '{}'", transmissionTime);
      audit.record("webhook.replay_rejected", "webhook_event",
          eventId, eventId, "WEBHOOK_TIMESTAMP_INVALID", null,
          "Rejected webhook with invalid PAYPAL-TRANSMISSION-TIME header.");
      return false;
    }
    long ageSeconds = Math.abs(Duration.between(transmittedAt, Instant.now()).getSeconds());
    if (ageSeconds > MAX_WEBHOOK_AGE_SECONDS) {
      log.warn("Webhook rejected (fail-closed): replay window exceeded ({}s > {}s) for event {}",
          ageSeconds, MAX_WEBHOOK_AGE_SECONDS, eventId);
      audit.record("webhook.replay_rejected", "webhook_event",
          eventId, eventId, "WEBHOOK_REPLAY_EXPIRED", null,
          "Rejected stale webhook outside 300s replay window (age=" + ageSeconds + "s).");
      return false;
    }

    String accessToken = payPalAdapter.map(PayPalPaymentAdapter::fetchAccessToken).orElse(null);
    if (isBlank(accessToken)) {
      log.error("Webhook rejected (fail-closed): PayPal OAuth2 Bearer token unavailable for verification");
      audit.record("webhook.verification_error", "webhook_event",
          eventId, eventId, "PAYPAL_OAUTH_UNAVAILABLE", null,
          "Webhook verification failed closed because PayPal OAuth2 token was unavailable.");
      return false;
    }

    try {
      RestClient client = RestClient.builder().baseUrl(paypalBaseUrl).build();

      Map<String, Object> verifyRequest = Map.of(
          "transmission_id", transmissionId,
          "transmission_time", transmissionTime,
          "transmission_sig", transmissionSig,
          "cert_url", certUrl,
          "auth_algo", authAlgo,
          "webhook_id", webhookId,
          "webhook_event", payload);

      VerificationResponse response = client.post()
          .uri("/v1/notifications/verify-webhook-signature")
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
          .contentType(MediaType.APPLICATION_JSON)
          .body(verifyRequest)
          .retrieve()
          .onStatus(HttpStatusCode::isError, (req, res) -> {
            log.error("PayPal verification API error: {}", res.getStatusCode());
          })
          .body(VerificationResponse.class);

      if (response != null && "SUCCESS".equals(response.verificationStatus)) {
        log.info("Webhook signature verified successfully for event {}", eventId);
        return true;
      }
      log.warn("Webhook signature verification returned: {}",
          response != null ? response.verificationStatus : "null");
      audit.record("webhook.signature_rejected", "webhook_event",
          eventId, eventId, "WEBHOOK_SIGNATURE_INVALID", null,
          "PayPal webhook signature verification returned non-SUCCESS status.");
      return false;
    } catch (Exception e) {
      log.error("Webhook signature verification API call failed (fail-closed): {}", e.getMessage());
      audit.record("webhook.verification_error", "webhook_event",
          eventId, eventId, "WEBHOOK_VERIFICATION_ERROR", null,
          "Signature verification API failed closed: " + AuditService.redactSensitiveData(e.getMessage()));
      return false;
    }
  }

  private Instant parseTransmissionTime(String raw) {
    try {
      return Instant.parse(raw.trim());
    } catch (Exception ignored) {
      try {
        return OffsetDateTime.parse(raw.trim()).toInstant();
      } catch (Exception ex) {
        return null;
      }
    }
  }

  private boolean isValidTestMockSignature(String eventId, String transmissionTime, String providedSig) {
    if (isBlank(webhookTestHmacSecret) || isBlank(providedSig) || isBlank(transmissionTime)) {
      return false;
    }
    Instant txAt = parseTransmissionTime(transmissionTime);
    if (txAt == null || Math.abs(Duration.between(txAt, Instant.now()).getSeconds()) > MAX_WEBHOOK_AGE_SECONDS) {
      return false;
    }
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(webhookTestHmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] expected = mac.doFinal((eventId + ":" + transmissionTime).getBytes(StandardCharsets.UTF_8));
      String expectedB64 = Base64.getEncoder().encodeToString(expected);
      return MessageDigest.isEqual(
          expectedB64.getBytes(StandardCharsets.UTF_8),
          providedSig.trim().getBytes(StandardCharsets.UTF_8));
    } catch (Exception ex) {
      return false;
    }
  }

  // ------------------------------------------------------------------ event processing

  @SuppressWarnings("unchecked")
  private void processEvent(String eventType, Map<String, Object> payload, String eventId) {
    switch (eventType) {
      case "PAYMENT.CAPTURE.COMPLETED" -> processCaptureCompleted(payload, eventId);
      case "PAYMENT.CAPTURE.DENIED" -> processCaptureDenied(payload, eventId);
      case "VAULT.PAYMENT-TOKEN.DELETED" -> processVaultTokenDeleted(payload, eventId);
      default -> log.info("Unhandled webhook event type: {} (event {})", eventType, eventId);
    }
  }

  @SuppressWarnings("unchecked")
  private void processCaptureCompleted(Map<String, Object> payload, String eventId) {
    try {
      Map<String, Object> resource = (Map<String, Object>) payload.get("resource");
      if (resource == null) return;

      // The custom_id or invoice_id may contain the intent reference
      String orderId = (String) resource.get("id");
      log.info("Capture completed for PayPal order/capture: {} (webhook event: {})", orderId, eventId);

      // Reconcile: find intents with this provider order ID in non-terminal states
      // and transition them to CAPTURED if they are in CAPTURE_PENDING
      intents.findAll().stream()
          .filter(i -> orderId != null && orderId.equals(i.getProviderOrderId()))
          .filter(i -> i.getStatus() == IntentStatus.CAPTURE_PENDING
              || i.getStatus() == IntentStatus.ORDER_CREATED
              || i.getStatus() == IntentStatus.RECONCILIATION_REQUIRED)
          .forEach(intent -> {
            intent.setStatus(IntentStatus.CAPTURED);
            intents.save(intent);
            audit.record("webhook.capture_reconciled", "purchase_intent",
                intent.getId().toString(), eventId, null, null,
                "Intent reconciled to CAPTURED via webhook " + eventId);
            log.info("Intent {} reconciled to CAPTURED via webhook", intent.getId());
          });
    } catch (Exception e) {
      log.error("Error processing PAYMENT.CAPTURE.COMPLETED: {}", e.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  private void processCaptureDenied(Map<String, Object> payload, String eventId) {
    try {
      Map<String, Object> resource = (Map<String, Object>) payload.get("resource");
      if (resource == null) return;

      String orderId = (String) resource.get("id");
      log.warn("Capture DENIED for PayPal order: {} (webhook event: {})", orderId, eventId);

      intents.findAll().stream()
          .filter(i -> orderId != null && orderId.equals(i.getProviderOrderId()))
          .filter(i -> i.getStatus() != IntentStatus.FAILED)
          .forEach(intent -> {
            intent.setStatus(IntentStatus.FAILED);
            intent.setFailureReason("Payment capture denied by PayPal (webhook: " + eventId + ")");
            intents.save(intent);
            audit.record("webhook.capture_denied", "purchase_intent",
                intent.getId().toString(), eventId, null, null,
                "Intent FAILED via webhook capture denial " + eventId);
          });
    } catch (Exception e) {
      log.error("Error processing PAYMENT.CAPTURE.DENIED: {}", e.getMessage());
    }
  }

  private void processVaultTokenDeleted(Map<String, Object> payload, String eventId) {
    log.warn("Vault payment token deleted event received ({}). Suspending all active watches.", eventId);
    List<Watch> activeWatches = watches.findByStatus(WatchStatus.ACTIVE);
    for (Watch watch : activeWatches) {
      watch.setStatus(WatchStatus.PAUSED);
      watches.save(watch);
      audit.record("watch.paused", "watch", watch.getId().toString(),
          eventId, null, null,
          "Watch suspended due to PayPal Vault payment token deletion (webhook: " + eventId + ")");
    }
    audit.record("webhook.vault_token_deleted", "vault_token", eventId,
        eventId, null, null,
        "PayPal vault token deleted: suspended " + activeWatches.size() + " active watches");
  }

  private boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  // ------------------------------------------------------------------ DTOs

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class VerificationResponse {
    @JsonProperty("verification_status")
    String verificationStatus;
  }
}

