package com.buybound.payment;

import com.buybound.purchase.domain.PurchaseIntent;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real PayPal Sandbox adapter. Active when {@code app.payments.mode=sandbox}.
 *
 * <p>Uses PayPal Orders v2 (CAPTURE and AUTHORIZE), Payments v2 (Authorization Capture/Void),
 * and Vault v3 (Setup Tokens / Payment Tokens). All payment parameters come strictly from
 * persisted {@link PurchaseIntent} state — never from browser or LLM inputs.
 */
@Component
@ConditionalOnProperty(name = "app.payments.mode", havingValue = "sandbox", matchIfMissing = true)
public class PayPalPaymentAdapter implements PaymentAdapter {
  private static final Logger log = LoggerFactory.getLogger(PayPalPaymentAdapter.class);

  private final RestClient restClient;
  private final String clientId;
  private final String clientSecret;
  private final String baseUrl;
  private final String frontendUrl;
  private final ObjectMapper errorMapper = new ObjectMapper();

  private volatile String cachedAccessToken;
  private volatile Instant tokenExpiresAt = Instant.EPOCH;

  public PayPalPaymentAdapter(
      String baseUrl,
      String clientId,
      String clientSecret) {
    this(baseUrl, clientId, clientSecret, "http://localhost:5173");
  }

  @Autowired
  public PayPalPaymentAdapter(
      @Value("${app.paypal.base-url:https://api-m.sandbox.paypal.com}") String baseUrl,
      @Value("${app.paypal.client-id:${PAYPAL_CLIENT_ID:}}") String clientId,
      @Value("${app.paypal.client-secret:${PAYPAL_CLIENT_SECRET:}}") String clientSecret,
      @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
    this.baseUrl = baseUrl;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.frontendUrl = normalizeFrontendBaseUrl(frontendUrl);
    this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    log.info("PayPal Sandbox adapter initialized (baseUrl={}, frontendUrl={}, configured={})",
        baseUrl, this.frontendUrl, clientId != null && !clientId.isBlank());
  }

  private static String normalizeFrontendBaseUrl(String raw) {
    if (raw == null || raw.isBlank()) {
      return "http://localhost:5173";
    }
    String trimmed = raw.trim().replaceAll("/+$", "");
    if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
      return "https://" + trimmed;
    }
    return trimmed;
  }

  @Override
  public String name() {
    return "paypal-sandbox";
  }

  public String getClientId() {
    return clientId;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public boolean isConfigured() {
    return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
  }

  @Override
  public PaymentResult capture(PurchaseIntent intent) {
    PayPalExecutionOutcome outcome = executeAllowCapture(intent, null);
    return new PaymentResult(
        outcome.captureId() != null ? outcome.captureId() : outcome.orderId(),
        outcome.status(),
        false,
        outcome.message(),
        outcome.approvalUrl());
  }

  /**
   * Creates an interactive PayPal Sandbox order returning the real sandbox.paypal.com/checkoutnow approve URL
   * without auto-charging via vault tokens, so the shopper signs into PayPal Sandbox directly.
   */
  public PayPalOrderCreatedResult createInteractiveOrder(PurchaseIntent intent, String intentType) {
    String token = requireAccessToken();
    if (token == null) {
      return new PayPalOrderCreatedResult(
          null, "ERROR", null, "PayPal Sandbox OAuth2 token unavailable. Check PAYPAL_CLIENT_ID and PAYPAL_CLIENT_SECRET.");
    }
    try {
      String cleanIntent = "AUTHORIZE".equalsIgnoreCase(intentType) ? "AUTHORIZE" : "CAPTURE";
      JsonNode created = createOrder(intent, cleanIntent, null, token);
      String orderId = created.path("id").asText(null);
      String status = created.path("status").asText("PAYER_ACTION_REQUIRED");
      String approveUrl = extractApprovalUrl(created);
      if ((approveUrl == null || approveUrl.isBlank()) && orderId != null) {
        approveUrl = "https://www.sandbox.paypal.com/checkoutnow?token=" + orderId;
      }
      approveUrl = appendLoginPrompt(approveUrl);
      return new PayPalOrderCreatedResult(
          orderId, status, approveUrl,
          "PayPal Sandbox order " + orderId + " ready for Personal Sandbox buyer approval.");
    } catch (Exception e) {
      log.error("PayPal interactive order creation failed for intent {}: {}", intent.getId(), e.getMessage());
      return new PayPalOrderCreatedResult(
          null, "ERROR", null, "PayPal order creation failed: " + e.getMessage());
    }
  }

  /**
   * Captures a PayPal Sandbox order once the buyer has approved it in checkoutnow / PayPal JS SDK,
   * extracting captureId plus the real buyer email, payer_id, and full name returned by PayPal Sandbox.
   * Supports optional {@code PayPal-Mock-Response} header for Sandbox negative testing (e.g. INSTRUMENT_DECLINED).
   */
  public PayPalCaptureOutcome captureApprovedOrder(String paypalOrderId, String idempotencyKey) {
    return captureApprovedOrder(paypalOrderId, idempotencyKey, null);
  }

  public PayPalCaptureOutcome captureApprovedOrder(
      String paypalOrderId, String idempotencyKey, String mockResponseHeader) {
    String token = requireAccessToken();
    if (token == null) {
      return new PayPalCaptureOutcome(
          paypalOrderId, null, null, null, null, null,
          PaymentResult.Status.FAILED, "PayPal Sandbox OAuth2 token unavailable.");
    }
    try {
      JsonNode order = restClient.get()
          .uri("/v2/checkout/orders/{orderId}", paypalOrderId)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
          .retrieve()
          .onStatus(HttpStatusCode::isError, (req, res) -> {
            throw buildPayPalApiException("/v2/checkout/orders/" + paypalOrderId, res);
          })
          .body(JsonNode.class);
      if (order == null) {
        return new PayPalCaptureOutcome(
            paypalOrderId, null, null, null, null, null,
            PaymentResult.Status.RECONCILIATION_REQUIRED, "Empty PayPal order response.");
      }

      String status = order.path("status").asText("");
      String orderIntent = order.path("intent").asText("CAPTURE");
      String approveUrl = extractApprovalUrl(order);

      if ("COMPLETED".equalsIgnoreCase(status) && (mockResponseHeader == null || mockResponseHeader.isBlank())) {
        return new PayPalCaptureOutcome(
            paypalOrderId,
            extractAuthorizationId(order),
            extractCaptureId(order),
            null,
            extractPayerEmail(order),
            extractPayerName(order),
            extractPayerId(order),
            PaymentResult.Status.CAPTURED,
            "PayPal Sandbox order " + paypalOrderId + " captured.");
      }

      if ("APPROVED".equalsIgnoreCase(status)) {
        if ("AUTHORIZE".equalsIgnoreCase(orderIntent)) {
          JsonNode authNode = authorizeOrderNode(paypalOrderId, idempotencyKey, token);
          String authId = extractAuthorizationId(authNode);
          if (authId != null) {
            JsonNode capRes = restClient.post()
                .uri("/v2/payments/authorizations/{authId}/capture", authId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("PayPal-Request-Id", idempotencyKey + "-auth-cap")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("final_capture", true))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                  throw buildPayPalApiException("/v2/payments/authorizations/" + authId + "/capture", res);
                })
                .body(JsonNode.class);
            String capId = capRes != null ? capRes.path("id").asText(null) : null;
            JsonNode authPayload = authNode != null ? authNode : order;
            return new PayPalCaptureOutcome(
                paypalOrderId, authId, capId, null,
                extractPayerEmail(authPayload),
                extractPayerName(authPayload),
                extractPayerId(authPayload),
                PaymentResult.Status.CAPTURED,
                "PayPal Sandbox authorization " + authId + " captured (" + capId + ").");
          }
        }

        JsonNode captured = captureOrderNode(paypalOrderId, idempotencyKey, token, mockResponseHeader);
        String capStatus = captured != null ? captured.path("status").asText("") : "";
        if ("COMPLETED".equalsIgnoreCase(capStatus)) {
          String capId = extractCaptureId(captured);
          return new PayPalCaptureOutcome(
              paypalOrderId, null, capId, null,
              extractPayerEmail(captured),
              extractPayerName(captured),
              extractPayerId(captured),
              PaymentResult.Status.CAPTURED,
              "PayPal Sandbox payment captured (Capture ID: " + capId + ").");
        }
      } else {
        // Order may still be unapproved, or caller may be testing PayPal-Mock-Response on capture.
        try {
          JsonNode captured = captureOrderNode(paypalOrderId, idempotencyKey, token, mockResponseHeader);
          String capStatus = captured != null ? captured.path("status").asText("") : "";
          if ("COMPLETED".equalsIgnoreCase(capStatus)) {
            String capId = extractCaptureId(captured);
            return new PayPalCaptureOutcome(
                paypalOrderId, null, capId, null,
                extractPayerEmail(captured),
                extractPayerName(captured),
                extractPayerId(captured),
                PaymentResult.Status.CAPTURED,
                "PayPal Sandbox payment captured (Capture ID: " + capId + ").");
          }
        } catch (PayPalApiException apiEx) {
          log.info("PayPal capture rejected for order {} (status={}, issue={}, debugId={}): {}",
              paypalOrderId, apiEx.getStatusCode(), apiEx.getIssue(), apiEx.getDebugId(), apiEx.getMessage());
          PaymentResult.Status mappedStatus = "ORDER_NOT_APPROVED".equalsIgnoreCase(apiEx.getIssue())
              || "PAYER_ACTION_REQUIRED".equalsIgnoreCase(apiEx.getIssue())
                  ? PaymentResult.Status.PAYER_ACTION_REQUIRED
                  : PaymentResult.Status.FAILED;
          return new PayPalCaptureOutcome(
              paypalOrderId, extractAuthorizationId(order), null,
              appendLoginPrompt(approveUrl != null ? approveUrl : "https://www.sandbox.paypal.com/checkoutnow?token=" + paypalOrderId),
              extractPayerEmail(order), extractPayerName(order), extractPayerId(order),
              mappedStatus,
              apiEx.getMessage());
        }
      }

      return new PayPalCaptureOutcome(
          paypalOrderId, extractAuthorizationId(order), null,
          appendLoginPrompt(approveUrl != null ? approveUrl : "https://www.sandbox.paypal.com/checkoutnow?token=" + paypalOrderId),
          extractPayerEmail(order), extractPayerName(order), extractPayerId(order),
          PaymentResult.Status.PAYER_ACTION_REQUIRED,
          "PayPal order " + paypalOrderId + " is " + status + ". Sign in and approve in PayPal Sandbox first.");
    } catch (PayPalApiException apiEx) {
      log.warn("PayPal API exception capturing order {} (issue={}, debugId={}): {}",
          paypalOrderId, apiEx.getIssue(), apiEx.getDebugId(), apiEx.getMessage());
      PaymentResult.Status mappedStatus = "ORDER_NOT_APPROVED".equalsIgnoreCase(apiEx.getIssue())
          || "PAYER_ACTION_REQUIRED".equalsIgnoreCase(apiEx.getIssue())
              ? PaymentResult.Status.PAYER_ACTION_REQUIRED
              : PaymentResult.Status.FAILED;
      return new PayPalCaptureOutcome(
          paypalOrderId, null, null, null, null, null,
          mappedStatus,
          apiEx.getMessage());
    } catch (Exception e) {
      log.error("Failed to capture PayPal Sandbox order {}: {}", paypalOrderId, e.getMessage());
      return new PayPalCaptureOutcome(
          paypalOrderId, null, null, null, null, null,
          PaymentResult.Status.FAILED,
          "PayPal Sandbox capture failed: " + e.getMessage());
    }
  }

  // ------------------------------------------------------------------ Orders v2: ALLOW -> CAPTURE

  public PayPalExecutionOutcome executeAllowCapture(PurchaseIntent intent, String vaultToken) {
    String token = requireAccessToken();
    if (token == null) {
      return new PayPalExecutionOutcome(null, null, null, null, "CAPTURE",
          PaymentResult.Status.FAILED, "PayPal OAuth2 token unavailable. Check PAYPAL_CLIENT_ID/SECRET.");
    }

    try {
      JsonNode created = createOrder(intent, "CAPTURE", vaultToken, token);
      String orderId = created.path("id").asText(null);
      String status = created.path("status").asText("");
      String approvalUrl = appendLoginPrompt(extractApprovalUrl(created));

      if ("COMPLETED".equalsIgnoreCase(status)) {
        String captureId = extractCaptureId(created);
        return new PayPalExecutionOutcome(orderId, null, captureId, null, "CAPTURE",
            PaymentResult.Status.CAPTURED,
            "PayPal Sandbox order " + orderId + " captured immediately (capture " + captureId + ").");
      }

      if ("APPROVED".equalsIgnoreCase(status) || (vaultToken != null && !vaultToken.isBlank() && "CREATED".equalsIgnoreCase(status))) {
        JsonNode captured = captureOrderNode(orderId, intent.getIdempotencyKey(), token);
        String capStatus = captured.path("status").asText("");
        String captureId = extractCaptureId(captured);
        if ("COMPLETED".equalsIgnoreCase(capStatus)) {
          return new PayPalExecutionOutcome(orderId, null, captureId, null, "CAPTURE",
              PaymentResult.Status.CAPTURED,
              "PayPal Sandbox order " + orderId + " captured (capture " + captureId + ").");
        }
      }

      return new PayPalExecutionOutcome(orderId, null, null, approvalUrl, "CAPTURE",
          PaymentResult.Status.PAYER_ACTION_REQUIRED,
          "PayPal Sandbox order " + orderId + " created (" + status + "). Buyer approval required before capture.");
    } catch (Exception e) {
      log.error("PayPal CAPTURE flow failed for intent {}: {}", intent.getId(), e.getMessage());
      return new PayPalExecutionOutcome(null, null, null, null, "CAPTURE",
          PaymentResult.Status.RECONCILIATION_REQUIRED,
          "PayPal order execution failed: " + e.getMessage());
    }
  }

  /**
   * Queries PayPal Sandbox Orders v2 REST API live (GET /v2/checkout/orders/{orderId})
   * to verify authoritative order state, capture ID, amount, and payer details.
   */
  public PayPalOrderVerificationResult fetchOrderDetails(String orderId) {
    if (orderId == null || orderId.isBlank()) {
      return new PayPalOrderVerificationResult(
          orderId, "INVALID", null, null, null, null, null, "USD", null,
          "PayPal Order ID is required.");
    }
    String token = requireAccessToken();
    if (token == null) {
      return new PayPalOrderVerificationResult(
          orderId, "UNAVAILABLE", null, null, null, null, null, "USD", null,
          "PayPal OAuth2 access token unavailable.");
    }
    try {
      JsonNode order = restClient.get()
          .uri("/v2/checkout/orders/{orderId}", orderId)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
          .retrieve()
          .body(JsonNode.class);
      if (order == null) {
        return new PayPalOrderVerificationResult(
            orderId, "NOT_FOUND", null, null, null, null, null, "USD", null,
            "No order payload returned from PayPal Sandbox.");
      }
      String status = order.path("status").asText("UNKNOWN");
      String captureId = extractCaptureId(order);
      String payerId = extractPayerId(order);
      String payerEmail = extractPayerEmail(order);
      String payerName = extractPayerName(order);
      JsonNode unitAmount = order.path("purchase_units").path(0).path("amount");
      String valueStr = unitAmount.path("value").asText("");
      java.math.BigDecimal amount = null;
      if (!valueStr.isBlank()) {
        try {
          amount = new java.math.BigDecimal(valueStr);
        } catch (NumberFormatException ignored) {
          // ignore
        }
      }
      String currency = unitAmount.path("currency_code").asText("USD");
      String createTime = order.path("update_time").asText(order.path("create_time").asText(null));
      return new PayPalOrderVerificationResult(
          orderId, status, captureId, payerEmail, payerName, payerId,
          amount, currency, createTime,
          "Verified live in PayPal Sandbox: status=" + status
              + (captureId != null ? " (capture " + captureId + ")" : ""));
    } catch (Exception ex) {
      return new PayPalOrderVerificationResult(
          orderId, "ERROR", null, null, null, null, null, "USD", null,
          "Live PayPal verification failed: " + ex.getMessage());
    }
  }

  // ------------------------------------------------------------------ Orders v2: HOLD -> AUTHORIZE

  public PayPalExecutionOutcome createHoldAuthorization(PurchaseIntent intent, String vaultToken) {
    String token = requireAccessToken();
    if (token == null) {
      return new PayPalExecutionOutcome(null, null, null, null, "AUTHORIZE",
          PaymentResult.Status.FAILED, "PayPal OAuth2 token unavailable.");
    }

    try {
      JsonNode created = createOrder(intent, "AUTHORIZE", vaultToken, token);
      String orderId = created.path("id").asText(null);
      String status = created.path("status").asText("");
      String approvalUrl = appendLoginPrompt(extractApprovalUrl(created));
      String authId = extractAuthorizationId(created);

      if (authId != null) {
        return new PayPalExecutionOutcome(orderId, authId, null, null, "AUTHORIZE",
            PaymentResult.Status.AUTHORIZED,
            "PayPal Sandbox order " + orderId + " authorized for HOLD (auth " + authId + ").");
      }

      if ("APPROVED".equalsIgnoreCase(status) || (vaultToken != null && !vaultToken.isBlank() && "CREATED".equalsIgnoreCase(status))) {
        JsonNode authorized = authorizeOrderNode(orderId, intent.getIdempotencyKey(), token);
        authId = extractAuthorizationId(authorized);
        if (authId != null) {
          return new PayPalExecutionOutcome(orderId, authId, null, null, "AUTHORIZE",
              PaymentResult.Status.AUTHORIZED,
              "PayPal Sandbox order " + orderId + " authorized (auth " + authId + ").");
        }
      }

      return new PayPalExecutionOutcome(orderId, null, null, approvalUrl, "AUTHORIZE",
          PaymentResult.Status.PAYER_ACTION_REQUIRED,
          "PayPal Sandbox HOLD order " + orderId + " created (" + status + "). Awaiting human/sandbox approval.");
    } catch (Exception e) {
      log.error("PayPal AUTHORIZE flow failed for intent {}: {}", intent.getId(), e.getMessage());
      return new PayPalExecutionOutcome(null, null, null, null, "AUTHORIZE",
          PaymentResult.Status.RECONCILIATION_REQUIRED,
          "PayPal HOLD authorization failed: " + e.getMessage());
    }
  }

  public PayPalExecutionOutcome captureHeldIntent(PurchaseIntent intent) {
    String token = requireAccessToken();
    if (token == null) {
      return new PayPalExecutionOutcome(intent.getProviderOrderId(), intent.getPaypalAuthorizationId(),
          null, intent.getApprovalUrl(), "CAPTURE", PaymentResult.Status.FAILED,
          "PayPal OAuth2 token unavailable.");
    }

    try {
      if (intent.getPaypalAuthorizationId() != null && !intent.getPaypalAuthorizationId().isBlank()) {
        JsonNode capRes = restClient.post()
            .uri("/v2/payments/authorizations/{authId}/capture", intent.getPaypalAuthorizationId())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .header("PayPal-Request-Id", intent.getIdempotencyKey() + "-auth-capture")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("final_capture", true))
            .retrieve()
            .body(JsonNode.class);
        String captureId = capRes != null ? capRes.path("id").asText(null) : null;
        String capStatus = capRes != null ? capRes.path("status").asText("") : "";
        if ("COMPLETED".equalsIgnoreCase(capStatus) || captureId != null) {
          return new PayPalExecutionOutcome(intent.getProviderOrderId(), intent.getPaypalAuthorizationId(),
              captureId, null, "CAPTURE", PaymentResult.Status.CAPTURED,
              "PayPal authorization " + intent.getPaypalAuthorizationId() + " captured (" + captureId + ").");
        }
      }

      if (intent.getProviderOrderId() != null && !intent.getProviderOrderId().isBlank()) {
        return syncAndCaptureOrder(intent.getProviderOrderId(), intent.getIdempotencyKey(), token);
      }

      return new PayPalExecutionOutcome(null, null, null, null, "CAPTURE",
          PaymentResult.Status.FAILED, "No PayPal Order ID or Authorization ID exists on this intent.");
    } catch (Exception e) {
      log.error("Failed to capture held PayPal intent {}: {}", intent.getId(), e.getMessage());
      return new PayPalExecutionOutcome(intent.getProviderOrderId(), intent.getPaypalAuthorizationId(),
          null, intent.getApprovalUrl(), "CAPTURE", PaymentResult.Status.RECONCILIATION_REQUIRED,
          "PayPal capture failed: " + e.getMessage());
    }
  }

  public PayPalExecutionOutcome syncAndCaptureOrder(String orderId, String idempotencyKey, String accessToken) {
    String token = accessToken != null ? accessToken : requireAccessToken();
    if (token == null) {
      return new PayPalExecutionOutcome(orderId, null, null, null, "CAPTURE",
          PaymentResult.Status.FAILED, "PayPal OAuth2 token unavailable.");
    }
    try {
      JsonNode order = restClient.get()
          .uri("/v2/checkout/orders/{orderId}", orderId)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
          .retrieve()
          .body(JsonNode.class);
      if (order == null) {
        return new PayPalExecutionOutcome(orderId, null, null, null, "CAPTURE",
            PaymentResult.Status.RECONCILIATION_REQUIRED, "Empty PayPal order response");
      }
      String status = order.path("status").asText("");
      String orderIntent = order.path("intent").asText("CAPTURE");
      String approvalUrl = extractApprovalUrl(order);

      if ("COMPLETED".equalsIgnoreCase(status)) {
        String captureId = extractCaptureId(order);
        return new PayPalExecutionOutcome(orderId, extractAuthorizationId(order), captureId, null,
            orderIntent, PaymentResult.Status.CAPTURED,
            "PayPal Sandbox order " + orderId + " is COMPLETED (capture " + captureId + ").");
      }

      if ("APPROVED".equalsIgnoreCase(status)) {
        if ("AUTHORIZE".equalsIgnoreCase(orderIntent)) {
          JsonNode authRes = authorizeOrderNode(orderId, idempotencyKey, token);
          String authId = extractAuthorizationId(authRes);
          if (authId != null) {
            JsonNode capRes = restClient.post()
                .uri("/v2/payments/authorizations/{authId}/capture", authId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("PayPal-Request-Id", idempotencyKey + "-auth-cap")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("final_capture", true))
                .retrieve()
                .body(JsonNode.class);
            String capId = capRes != null ? capRes.path("id").asText(null) : null;
            return new PayPalExecutionOutcome(orderId, authId, capId, null, "AUTHORIZE",
                PaymentResult.Status.CAPTURED,
                "PayPal order " + orderId + " authorized (" + authId + ") and captured (" + capId + ").");
          }
        } else {
          JsonNode capRes = captureOrderNode(orderId, idempotencyKey, token);
          String capId = extractCaptureId(capRes);
          if ("COMPLETED".equalsIgnoreCase(capRes.path("status").asText(""))) {
            return new PayPalExecutionOutcome(orderId, null, capId, null, "CAPTURE",
                PaymentResult.Status.CAPTURED,
                "PayPal Sandbox order " + orderId + " captured (" + capId + ").");
          }
        }
      }

      return new PayPalExecutionOutcome(orderId, extractAuthorizationId(order), null, approvalUrl,
          orderIntent, PaymentResult.Status.PAYER_ACTION_REQUIRED,
          "PayPal order " + orderId + " status is " + status + ". Complete Sandbox buyer approval first.");
    } catch (Exception e) {
      return new PayPalExecutionOutcome(orderId, null, null, null, "CAPTURE",
          PaymentResult.Status.RECONCILIATION_REQUIRED,
          "Failed to query/capture PayPal order " + orderId + ": " + e.getMessage());
    }
  }

  public PayPalExecutionOutcome voidHeldIntent(PurchaseIntent intent) {
    if (intent.getPaypalAuthorizationId() == null || intent.getPaypalAuthorizationId().isBlank()) {
      return new PayPalExecutionOutcome(intent.getProviderOrderId(), null, null, null, "AUTHORIZE",
          PaymentResult.Status.VOIDED,
          "Hold rejected locally (no PayPal authorization ID was captured yet).");
    }
    String token = requireAccessToken();
    if (token == null) {
      return new PayPalExecutionOutcome(intent.getProviderOrderId(), intent.getPaypalAuthorizationId(),
          null, null, "AUTHORIZE", PaymentResult.Status.FAILED, "PayPal OAuth2 token unavailable.");
    }
    try {
      restClient.post()
          .uri("/v2/payments/authorizations/{authId}/void", intent.getPaypalAuthorizationId())
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of())
          .retrieve()
          .toBodilessEntity();
      return new PayPalExecutionOutcome(intent.getProviderOrderId(), intent.getPaypalAuthorizationId(),
          null, null, "AUTHORIZE", PaymentResult.Status.VOIDED,
          "PayPal Sandbox authorization " + intent.getPaypalAuthorizationId() + " voided.");
    } catch (Exception e) {
      log.error("Failed to void PayPal authorization {}: {}", intent.getPaypalAuthorizationId(), e.getMessage());
      return new PayPalExecutionOutcome(intent.getProviderOrderId(), intent.getPaypalAuthorizationId(),
          null, null, "AUTHORIZE", PaymentResult.Status.RECONCILIATION_REQUIRED,
          "PayPal void request returned: " + e.getMessage());
    }
  }

  // ------------------------------------------------------------------ Vault v3

  public VaultSetupResult createSetupTokenWithLinks() {
    return createSetupTokenWithLinks("/vault/success", "/app/safety");
  }

  public VaultSetupResult createSetupTokenWithLinks(String returnPath, String cancelPath) {
    String token = requireAccessToken();
    if (token == null) {
      return new VaultSetupResult(null, "ERROR", null, "PayPal client credentials not configured or invalid.");
    }
    String cleanReturn = (returnPath != null && returnPath.startsWith("/")) ? returnPath : "/" + (returnPath != null ? returnPath : "vault/success");
    String cleanCancel = (cancelPath != null && cancelPath.startsWith("/")) ? cancelPath : "/" + (cancelPath != null ? cancelPath : "app/safety");
    try {
      Map<String, Object> req = Map.of(
          "payment_source", Map.of(
              "paypal", Map.of(
                  "usage_type", "MERCHANT",
                  "experience_context", Map.of(
                      "landing_page", "LOGIN",
                      "return_url", frontendUrl + cleanReturn,
                      "cancel_url", frontendUrl + cleanCancel
                  )
              )
          )
      );
      JsonNode resp = restClient.post()
          .uri("/v3/vault/setup-tokens")
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
          .header("PayPal-Request-Id", UUID.randomUUID().toString())
          .contentType(MediaType.APPLICATION_JSON)
          .body(req)
          .retrieve()
          .body(JsonNode.class);
      if (resp == null) {
        return new VaultSetupResult(null, "ERROR", null, "Empty setup-token response from PayPal");
      }
      return new VaultSetupResult(
          resp.path("id").asText(null),
          resp.path("status").asText("PAYER_ACTION_REQUIRED"),
          appendLoginPrompt(extractApprovalUrl(resp)),
          "PayPal Vault setup token created. Sign in with your PayPal Sandbox email & password to issue a Vault token.");
    } catch (Exception e) {
      log.error("Failed to create PayPal setup token: {}", e.getMessage());
      return new VaultSetupResult(null, "ERROR", null, "PayPal setup token failed: " + e.getMessage());
    }
  }

  public VaultPaymentTokenResult exchangeSetupTokenForPaymentToken(String setupTokenId) {
    String token = requireAccessToken();
    if (token == null || setupTokenId == null || setupTokenId.isBlank()) {
      return new VaultPaymentTokenResult(null, null, null, "ERROR", "Missing PayPal token or setupTokenId");
    }
    try {
      Map<String, Object> req = Map.of(
          "payment_source", Map.of(
              "token", Map.of(
                  "id", setupTokenId,
                  "type", "SETUP_TOKEN"
              )
          )
      );
      JsonNode resp = restClient.post()
          .uri("/v3/vault/payment-tokens")
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
          .header("PayPal-Request-Id", UUID.randomUUID().toString())
          .contentType(MediaType.APPLICATION_JSON)
          .body(req)
          .retrieve()
          .body(JsonNode.class);
      if (resp == null || !resp.has("id")) {
        return new VaultPaymentTokenResult(null, null, null, "ERROR", "PayPal did not return a payment token.");
      }
      String paymentTokenId = resp.path("id").asText();
      String rawEmail = resp.path("payment_source").path("paypal").path("email_address").asText(null);
      String payerId = resp.path("payment_source").path("paypal").path("payer_id").asText("").trim();
      if (payerId.isEmpty()) {
        payerId = resp.path("customer").path("id").asText("").trim();
      }
      String cleanEmail = rawEmail != null && !rawEmail.isBlank() ? rawEmail.trim() : null;
      return new VaultPaymentTokenResult(
          paymentTokenId, cleanEmail, payerId.isEmpty() ? null : payerId, "ACTIVE",
          "PayPal Vault payment token linked cleanly after Sandbox approval.");
    } catch (Exception e) {
      log.error("Failed to exchange setup token {}: {}", setupTokenId, e.getMessage());
      return new VaultPaymentTokenResult(null, null, null, "ERROR",
          "Vault token exchange failed (ensure you signed in and approved in PayPal Sandbox): " + e.getMessage());
    }
  }

  public boolean deletePaymentToken(String paymentTokenId) {
    if (paymentTokenId == null || paymentTokenId.isBlank()) {
      return false;
    }
    String accessToken = requireAccessToken();
    if (accessToken == null) {
      return false;
    }
    try {
      restClient.delete()
          .uri("/v3/vault/payment-tokens/{id}", paymentTokenId)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
          .retrieve()
          .toBodilessEntity();
      log.info("PayPal vault payment token deleted: {}", paymentTokenId);
      return true;
    } catch (Exception e) {
      log.warn("PayPal vault delete returned {} for token {}", e.getMessage(), paymentTokenId);
      return false;
    }
  }

  // ------------------------------------------------------------------ Internal helpers

  private JsonNode createOrder(PurchaseIntent intent, String intentType, String vaultToken, String accessToken) {
    String value = intent.getMaxTotal().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    Map<String, Object> amount = Map.of(
        "currency_code", intent.getCurrency(),
        "value", value
    );

    Map<String, Object> purchaseUnit = new LinkedHashMap<>();
    purchaseUnit.put("reference_id", intent.getId().toString());
    purchaseUnit.put("custom_id", intent.getId().toString());
    purchaseUnit.put("description", "BuyBound Guarded Purchase · " + safeSku(intent));
    purchaseUnit.put("amount", amount);

    Map<String, Object> orderRequest = new LinkedHashMap<>();
    orderRequest.put("intent", intentType);
    orderRequest.put("purchase_units", List.of(purchaseUnit));

    if (vaultToken != null && !vaultToken.isBlank()) {
      orderRequest.put("payment_source", Map.of(
          "paypal", Map.of("vault_id", vaultToken)
      ));
    } else {
      Map<String, Object> experienceContext = new LinkedHashMap<>();
      experienceContext.put("return_url", frontendUrl + "/app/purchases?paypal_return=true&intentId=" + intent.getId());
      experienceContext.put("cancel_url", frontendUrl + "/app/shop?paypal_cancel=true&intentId=" + intent.getId());
      experienceContext.put("user_action", "PAY_NOW");
      experienceContext.put("shipping_preference", "NO_SHIPPING");
      experienceContext.put("landing_page", "LOGIN");
      experienceContext.put("brand_name", "BuyBound");
      orderRequest.put("payment_source", Map.of(
          "paypal", Map.of("experience_context", experienceContext)
      ));
    }

    return restClient.post()
        .uri("/v2/checkout/orders")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .header("PayPal-Request-Id", intent.getIdempotencyKey() + "-" + intentType.toLowerCase())
        .contentType(MediaType.APPLICATION_JSON)
        .body(orderRequest)
        .retrieve()
        .onStatus(HttpStatusCode::isError, (req, res) -> {
          throw buildPayPalApiException("/v2/checkout/orders", res);
        })
        .body(JsonNode.class);
  }

  private JsonNode captureOrderNode(String orderId, String idempotencyKey, String accessToken) {
    return captureOrderNode(orderId, idempotencyKey, accessToken, null);
  }

  private JsonNode captureOrderNode(
      String orderId, String idempotencyKey, String accessToken, String mockResponseHeader) {
    RestClient.RequestBodySpec spec = restClient.post()
        .uri("/v2/checkout/orders/{orderId}/capture", orderId)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .header("PayPal-Request-Id", idempotencyKey + "-capture")
        .contentType(MediaType.APPLICATION_JSON);
    String normalizedMock = normalizeMockResponseHeader(mockResponseHeader);
    if (normalizedMock != null) {
      spec = spec.header("PayPal-Mock-Response", normalizedMock);
    }
    return spec
        .body(Map.of())
        .retrieve()
        .onStatus(HttpStatusCode::isError, (req, res) -> {
          throw buildPayPalApiException("/v2/checkout/orders/" + orderId + "/capture", res);
        })
        .body(JsonNode.class);
  }

  private JsonNode authorizeOrderNode(String orderId, String idempotencyKey, String accessToken) {
    return restClient.post()
        .uri("/v2/checkout/orders/{orderId}/authorize", orderId)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .header("PayPal-Request-Id", idempotencyKey + "-authorize")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of())
        .retrieve()
        .onStatus(HttpStatusCode::isError, (req, res) -> {
          throw buildPayPalApiException("/v2/checkout/orders/" + orderId + "/authorize", res);
        })
        .body(JsonNode.class);
  }

  public static String normalizeMockResponseHeader(String mockResponseHeader) {
    if (mockResponseHeader == null) return null;
    String trimmed = mockResponseHeader.trim();
    if (trimmed.isEmpty()) return null;
    if (trimmed.startsWith("{")) return trimmed;
    String cleanCode = trimmed.replaceAll("[^A-Za-z0-9_]", "");
    if (cleanCode.isEmpty()) return null;
    return "{\"mock_application_codes\":\"" + cleanCode + "\"}";
  }

  private PayPalApiException buildPayPalApiException(
      String endpoint, org.springframework.http.client.ClientHttpResponse res) {
    int statusCode = 502;
    try {
      statusCode = res.getStatusCode().value();
    } catch (Exception ignored) {
      // keep 502
    }
    String rawBody = readErrorBody(res);
    String issue = "";
    String description = "";
    String debugId = "";
    String topMessage = "";
    if (!rawBody.isEmpty()) {
      try {
        JsonNode err = errorMapper.readTree(rawBody);
        topMessage = err.path("message").asText("").trim();
        debugId = err.path("debug_id").asText("").trim();
        JsonNode details = err.path("details");
        if (details.isArray() && !details.isEmpty()) {
          issue = details.get(0).path("issue").asText("").trim();
          description = details.get(0).path("description").asText("").trim();
        }
        if (issue.isEmpty()) {
          issue = err.path("name").asText("").trim();
        }
      } catch (Exception ignored) {
        description = rawBody;
      }
    }
    StringBuilder msg = new StringBuilder("PayPal Sandbox ").append(endpoint)
        .append(" returned HTTP ").append(statusCode);
    if (!issue.isEmpty()) msg.append(" · issue=").append(issue);
    if (!debugId.isEmpty()) msg.append(" · debug_id=").append(debugId);
    String detailText = !description.isEmpty() ? description : topMessage;
    if (!detailText.isEmpty()) msg.append(": ").append(detailText);
    return new PayPalApiException(statusCode, issue.isEmpty() ? null : issue,
        debugId.isEmpty() ? null : debugId, msg.toString());
  }

  public static String appendLoginPrompt(String url) {
    if (url == null || url.isBlank()) {
      return url;
    }
    if (url.contains("prompt=")) {
      return url;
    }
    return url + (url.contains("?") ? "&prompt=login" : "?prompt=login");
  }

  private static String readErrorBody(org.springframework.http.client.ClientHttpResponse res) {
    try {
      return new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8).trim();
    } catch (Exception ignored) {
      return "";
    }
  }

  /**
   * Exchanges a PayPal OAuth authorization code (from sandbox.paypal.com/signin/authorize redirect)
   * for the buyer's real Sandbox email and full name via /v1/oauth2/token + userinfo.
   */
  public OptionalBuyerProfile exchangeAuthorizationCodeForBuyerProfile(String authCode) {
    if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()
        || authCode == null || authCode.isBlank()) {
      return new OptionalBuyerProfile(null, null, null, "Missing PayPal OAuth code or client credentials.");
    }
    try {
      String credentials = Base64.getEncoder().encodeToString(
          (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
      TokenResponse tokenRes = restClient.post()
          .uri("/v1/oauth2/token")
          .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body("grant_type=authorization_code&code="
              + java.net.URLEncoder.encode(authCode.trim(), StandardCharsets.UTF_8))
          .retrieve()
          .body(TokenResponse.class);
      if (tokenRes == null || tokenRes.accessToken == null || tokenRes.accessToken.isBlank()) {
        return new OptionalBuyerProfile(null, null, null, "PayPal OAuth token exchange returned empty token.");
      }
      JsonNode profile = restClient.get()
          .uri("/v1/identity/openidconnect/userinfo?schema=openid")
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenRes.accessToken)
          .retrieve()
          .body(JsonNode.class);
      String email = profile != null ? profile.path("email").asText("").trim() : "";
      if (email.isEmpty() && profile != null && profile.path("emails").isArray() && !profile.path("emails").isEmpty()) {
        email = profile.path("emails").get(0).path("value").asText("").trim();
      }
      String payerId = profile != null ? profile.path("payer_id").asText("").trim() : "";
      if (payerId.isEmpty() && profile != null) {
        payerId = profile.path("user_id").asText("").trim();
      }
      if (payerId.isEmpty() && profile != null) {
        payerId = profile.path("sub").asText("").trim();
      }
      String name = profile != null ? profile.path("name").asText("").trim() : "";
      if (name.isEmpty() && profile != null) {
        name = (profile.path("given_name").asText("") + " " + profile.path("family_name").asText("")).trim();
      }
      if (payerId.isEmpty() && email.isEmpty()) {
        return new OptionalBuyerProfile(null, name.isEmpty() ? null : name, null,
            "PayPal OAuth succeeded, but payer identity was missing from userinfo.");
      }
      return new OptionalBuyerProfile(
          email.isEmpty() ? null : email,
          name.isEmpty() ? null : name,
          payerId.isEmpty() ? null : payerId,
          "Signed in via live PayPal Sandbox OAuth.");
    } catch (Exception e) {
      log.warn("PayPal OAuth authorization code exchange failed: {}", e.getMessage());
      return new OptionalBuyerProfile(null, null, null, "PayPal sign-in exchange failed: " + e.getMessage());
    }
  }

  public String fetchAccessToken() {
    return requireAccessToken();
  }

  private String requireAccessToken() {
    if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
      return null;
    }
    if (cachedAccessToken != null && Instant.now().isBefore(tokenExpiresAt)) {
      return cachedAccessToken;
    }
    String credentials = Base64.getEncoder().encodeToString(
        (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
    Exception lastEx = null;
    for (int attempt = 1; attempt <= 2; attempt++) {
      try {
        TokenResponse response = restClient.post()
            .uri("/v1/oauth2/token")
            .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body("grant_type=client_credentials")
            .retrieve()
            .body(TokenResponse.class);
        if (response != null && response.accessToken != null) {
          cachedAccessToken = response.accessToken;
          long ttl = Math.max(60, response.expiresIn - 60L);
          tokenExpiresAt = Instant.now().plusSeconds(ttl);
          return cachedAccessToken;
        }
      } catch (Exception e) {
        lastEx = e;
        if (attempt < 2) {
          try {
            Thread.sleep(250);
          } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
          }
        }
      }
    }
    log.error("PayPal OAuth2 token acquisition failed after retries: {}",
        lastEx != null ? lastEx.getMessage() : "empty response");
    cachedAccessToken = null;
    return null;
  }

  private String extractApprovalUrl(JsonNode root) {
    JsonNode links = root.path("links");
    if (links.isArray()) {
      for (JsonNode link : links) {
        String rel = link.path("rel").asText("");
        if ("payer-action".equals(rel) || "approve".equals(rel)) {
          return link.path("href").asText(null);
        }
      }
    }
    return null;
  }

  private String extractCaptureId(JsonNode order) {
    JsonNode captures = order.path("purchase_units").path(0).path("payments").path("captures");
    if (captures.isArray() && !captures.isEmpty()) {
      return captures.get(0).path("id").asText(null);
    }
    return null;
  }

  private String extractAuthorizationId(JsonNode order) {
    JsonNode auths = order.path("purchase_units").path(0).path("payments").path("authorizations");
    if (auths.isArray() && !auths.isEmpty()) {
      return auths.get(0).path("id").asText(null);
    }
    return null;
  }

  private String safeSku(PurchaseIntent intent) {
    if (intent.getCanonicalSku() != null && !intent.getCanonicalSku().isBlank()) {
      return intent.getCanonicalSku();
    }
    return intent.getMerchantId();
  }

  private String extractPayerId(JsonNode order) {
    if (order == null) return null;
    String payerId = order.path("payer").path("payer_id").asText("").trim();
    if (!payerId.isEmpty()) return payerId;
    String accountId = order.path("payment_source").path("paypal").path("account_id").asText("").trim();
    if (!accountId.isEmpty()) return accountId;
    String subPayer = order.path("payment_source").path("paypal").path("payer_id").asText("").trim();
    return subPayer.isEmpty() ? null : subPayer;
  }

  public static String maskEmail(String email) {
    if (email == null || email.isBlank()) return null;
    String trimmed = email.trim();
    int at = trimmed.indexOf('@');
    if (at <= 1) {
      return at == 1 ? trimmed.charAt(0) + "***" + trimmed.substring(at) : trimmed;
    }
    String local = trimmed.substring(0, at);
    if (local.contains("***")) {
      return trimmed;
    }
    int prefixLen = local.length() > 3 ? 2 : 1;
    return local.substring(0, prefixLen) + "***" + trimmed.substring(at);
  }

  private String extractPayerEmail(JsonNode order) {
    if (order == null) return null;
    String email = order.path("payer").path("email_address").asText("").trim();
    if (!email.isEmpty()) return email;
    String sourceEmail = order.path("payment_source").path("paypal").path("email_address").asText("").trim();
    return sourceEmail.isEmpty() ? null : sourceEmail;
  }

  private String extractPayerName(JsonNode order) {
    if (order == null) return null;
    JsonNode nameNode = order.path("payer").path("name");
    if (nameNode.isMissingNode() || nameNode.isEmpty()) {
      nameNode = order.path("payment_source").path("paypal").path("name");
    }
    String given = nameNode.path("given_name").asText("").trim();
    String surname = nameNode.path("surname").asText("").trim();
    String combined = (given + " " + surname).trim();
    return combined.isEmpty() ? null : combined;
  }

  public record OptionalBuyerProfile(
      String email,
      String name,
      String payerId,
      String message) {
    public OptionalBuyerProfile(String email, String name, String message) {
      this(email, name, null, message);
    }
  }

  public record PayPalOrderCreatedResult(
      String orderId,
      String status,
      String approveUrl,
      String message) {}

  public record PayPalCaptureOutcome(
      String orderId,
      String authorizationId,
      String captureId,
      String approveUrl,
      String payerEmail,
      String payerName,
      String payerId,
      PaymentResult.Status status,
      String message) {
    public PayPalCaptureOutcome(
        String orderId,
        String authorizationId,
        String captureId,
        String approveUrl,
        String payerEmail,
        String payerName,
        PaymentResult.Status status,
        String message) {
      this(orderId, authorizationId, captureId, approveUrl, payerEmail, payerName, null, status, message);
    }
  }

  public record PayPalExecutionOutcome(
      String orderId,
      String authorizationId,
      String captureId,
      String approvalUrl,
      String intentType,
      PaymentResult.Status status,
      String message) {}

  public record PayPalOrderVerificationResult(
      String orderId,
      String status,
      String captureId,
      String payerEmail,
      String payerName,
      String payerId,
      java.math.BigDecimal amount,
      String currency,
      String updateTime,
      String message) {}

  public record VaultSetupResult(
      String setupTokenId,
      String status,
      String approvalUrl,
      String message) {}

  public record VaultPaymentTokenResult(
      String paymentTokenId,
      String payerEmail,
      String payerId,
      String status,
      String message) {
    public VaultPaymentTokenResult(
        String paymentTokenId,
        String payerEmail,
        String status,
        String message) {
      this(paymentTokenId, payerEmail, null, status, message);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class TokenResponse {
    @JsonProperty("access_token")
    String accessToken;
    @JsonProperty("expires_in")
    int expiresIn;
  }

  private static class PayPalApiException extends RuntimeException {
    private final int statusCode;
    private final String issue;
    private final String debugId;

    PayPalApiException(int statusCode, String issue, String debugId, String message) {
      super(message);
      this.statusCode = statusCode;
      this.issue = issue;
      this.debugId = debugId;
    }

    int getStatusCode() {
      return statusCode;
    }

    String getIssue() {
      return issue;
    }

    String getDebugId() {
      return debugId;
    }
  }
}
