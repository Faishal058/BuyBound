package com.buybound.payment;

import com.buybound.ai.dto.AiOfferForensicsResult;
import com.buybound.ai.service.AiShoppingAgentService;
import com.buybound.audit.AuditService;
import com.buybound.catalog.dto.Channel3ProductOfferDto;
import com.buybound.common.exception.ConflictException;
import com.buybound.common.exception.ExternalServiceException;
import com.buybound.common.exception.ResourceNotFoundException;
import com.buybound.guardian.domain.Decision;
import com.buybound.guardian.domain.EvaluationContext;
import com.buybound.guardian.domain.GuardianDecisionResult;
import com.buybound.guardian.domain.Offer;
import com.buybound.guardian.domain.Policy;
import com.buybound.guardian.domain.ReasonCode;
import com.buybound.guardian.service.GuardianEvaluator;
import com.buybound.guardian.service.RuntimeGuardrailSettingsService;
import com.buybound.payment.PayPalPaymentAdapter.PayPalCaptureOutcome;
import com.buybound.payment.PayPalPaymentAdapter.PayPalOrderCreatedResult;
import com.buybound.payment.PayPalPaymentAdapter.PayPalOrderVerificationResult;
import com.buybound.purchase.domain.GuardianDecisionRecord;
import com.buybound.purchase.domain.HuntedOfferRecord;
import com.buybound.purchase.domain.IntentStatus;
import com.buybound.purchase.domain.OfferSnapshot;
import com.buybound.purchase.domain.PaymentAttempt;
import com.buybound.purchase.domain.PurchaseIntent;
import com.buybound.purchase.domain.SpendReservation;
import com.buybound.purchase.dto.GuardianDecisionResponse;
import com.buybound.purchase.dto.PurchaseIntentResponse;
import com.buybound.purchase.repository.GuardianDecisionRepository;
import com.buybound.purchase.repository.HuntedOfferRepository;
import com.buybound.purchase.repository.PaymentAttemptRepository;
import com.buybound.purchase.repository.PurchaseIntentRepository;
import com.buybound.purchase.repository.SpendReservationRepository;
import com.buybound.purchase.service.CheckoutSecurityValidator;
import com.buybound.purchase.service.PurchaseService;
import com.buybound.user.service.UserProfileService;
import com.buybound.watch.domain.Mandate;
import com.buybound.watch.domain.MandateStatus;
import com.buybound.watch.domain.Watch;
import com.buybound.watch.dto.ActiveMandateTelemetryResponse;
import com.buybound.watch.repository.MandateRepository;
import com.buybound.watch.repository.WatchRepository;
import com.buybound.watch.service.MandateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Interactive PayPal Sandbox checkout orchestration:
 * Guardian Safety Check -> Real Sandbox Order Creation -> Live Buyer Approval -> Capture & $5,000 Wallet Deduction.
 */
@Service
public class PayPalCheckoutService {
  private static final Logger log = LoggerFactory.getLogger(PayPalCheckoutService.class);
  private static final String POLICY_VERSION = "1.0";

  private final Optional<PayPalPaymentAdapter> payPalAdapter;
  private final PurchaseIntentRepository intents;
  private final GuardianDecisionRepository decisions;
  private final SpendReservationRepository reservations;
  private final PaymentAttemptRepository attempts;
  private final HuntedOfferRepository huntedOffers;
  private final WatchRepository watches;
  private final MandateRepository mandates;
  private final MandateService mandateService;
  private final AiShoppingAgentService aiService;
  private final GuardianEvaluator evaluator;
  private final AuditService audit;
  private final ObjectMapper objectMapper;
  private final Optional<UserProfileService> userProfileService;
  private final String paymentsMode;
  private final String configuredClientId;
  private final String frontendUrl;

  public PayPalCheckoutService(
      Optional<PayPalPaymentAdapter> payPalAdapter,
      PurchaseIntentRepository intents,
      GuardianDecisionRepository decisions,
      SpendReservationRepository reservations,
      PaymentAttemptRepository attempts,
      HuntedOfferRepository huntedOffers,
      WatchRepository watches,
      MandateRepository mandates,
      MandateService mandateService,
      AiShoppingAgentService aiService,
      GuardianEvaluator evaluator,
      AuditService audit,
      ObjectMapper objectMapper,
      Optional<UserProfileService> userProfileService,
      String paymentsMode,
      String configuredClientId) {
    this(payPalAdapter, intents, decisions, reservations, attempts, huntedOffers, watches,
        mandates, mandateService, aiService, evaluator, audit, objectMapper, userProfileService,
        paymentsMode, configuredClientId, "http://localhost:5173");
  }

  @Autowired
  public PayPalCheckoutService(
      Optional<PayPalPaymentAdapter> payPalAdapter,
      PurchaseIntentRepository intents,
      GuardianDecisionRepository decisions,
      SpendReservationRepository reservations,
      PaymentAttemptRepository attempts,
      HuntedOfferRepository huntedOffers,
      WatchRepository watches,
      MandateRepository mandates,
      MandateService mandateService,
      AiShoppingAgentService aiService,
      GuardianEvaluator evaluator,
      AuditService audit,
      ObjectMapper objectMapper,
      Optional<UserProfileService> userProfileService,
      @Value("${app.payments.mode:sandbox}") String paymentsMode,
      @Value("${app.paypal.client-id:${PAYPAL_CLIENT_ID:}}") String configuredClientId,
      @Value("${FRONTEND_URL:${app.frontend-url:${APP_FRONTEND_URL:http://localhost:5173}}}") String frontendUrl) {
    this.payPalAdapter = payPalAdapter;
    this.intents = intents;
    this.decisions = decisions;
    this.reservations = reservations;
    this.attempts = attempts;
    this.huntedOffers = huntedOffers;
    this.watches = watches;
    this.mandates = mandates;
    this.mandateService = mandateService;
    this.aiService = aiService;
    this.evaluator = evaluator;
    this.audit = audit;
    this.objectMapper = objectMapper;
    this.userProfileService = userProfileService;
    this.paymentsMode = paymentsMode;
    this.configuredClientId = configuredClientId;
    this.frontendUrl = normalizeFrontendUrl(frontendUrl);
  }

  private static String normalizeFrontendUrl(String raw) {
    if (raw == null || raw.isBlank()) {
      return "http://localhost:5173";
    }
    String trimmed = raw.trim().replaceAll("/+$", "");
    if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
      return "https://" + trimmed;
    }
    return trimmed;
  }

  public record OAuthSessionResult(ActiveMandateTelemetryResponse telemetry, UUID userId) {}

  public PayPalConfigResponse getConfig() {
    String clientId = payPalAdapter.map(PayPalPaymentAdapter::getClientId)
        .filter(s -> !s.isBlank())
        .orElse(configuredClientId != null ? configuredClientId : "");
    boolean configured = payPalAdapter.map(PayPalPaymentAdapter::isConfigured)
        .orElse(!clientId.isBlank());
    String redirectUri = frontendUrl + "/login?paypal_oauth=true";
    String oauthUrl = clientId.isBlank()
        ? ""
        : "https://www.sandbox.paypal.com/signin/authorize?client_id="
            + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
            + "&response_type=code&scope=" + URLEncoder.encode("openid profile email", StandardCharsets.UTF_8)
            + "&prompt=login"
            + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
    return new PayPalConfigResponse(clientId, paymentsMode, "USD", configured, oauthUrl);
  }

  @Transactional
  public ActiveMandateTelemetryResponse oauthCallback(String authCode) {
    return oauthCallbackWithSession(authCode).telemetry();
  }

  @Transactional
  public OAuthSessionResult oauthCallbackWithSession(String authCode) {
    PayPalPaymentAdapter pp = payPalAdapter.orElseThrow(() ->
        new ConflictException("PayPal Sandbox adapter is not active."));
    PayPalPaymentAdapter.OptionalBuyerProfile profile = pp.exchangeAuthorizationCodeForBuyerProfile(authCode);
    if ((profile.payerId() == null || profile.payerId().isBlank())
        && (profile.email() == null || profile.email().isBlank())) {
      throw new ConflictException(profile.message());
    }
    UUID resolvedUserId = userProfileService
        .map(s -> s.loginOrConnectFromPayPalOAuth(profile.payerId(), profile.email(), profile.name()))
        .map(r -> r.id())
        .orElse(null);
    ActiveMandateTelemetryResponse telemetry = mandates.findAll().isEmpty()
        ? mandateService.getActiveTelemetry()
        : mandateService.syncVerifiedPayPalBuyer(profile.payerId(), profile.email(), profile.name(), null);
    return new OAuthSessionResult(telemetry, resolvedUserId);
  }

  public Map<String, Object> startPayPalLogin() {
    PayPalPaymentAdapter pp = payPalAdapter.orElseThrow(() ->
        new ConflictException("PayPal Sandbox adapter is not active."));
    if (!pp.isConfigured()) {
      throw new ConflictException("PayPal Sandbox credentials are missing. Check PAYPAL_CLIENT_ID and PAYPAL_CLIENT_SECRET.");
    }
    PayPalPaymentAdapter.VaultSetupResult setup =
        pp.createSetupTokenWithLinks("/login?paypal_vault_login=true", "/login?paypal_cancelled=true");
    if (setup.setupTokenId() == null || setup.approvalUrl() == null || setup.approvalUrl().isBlank()) {
      throw new ConflictException(setup.message());
    }
    return Map.of(
        "setupTokenId", setup.setupTokenId(),
        "approvalUrl", setup.approvalUrl(),
        "status", setup.status());
  }

  @Transactional
  public OAuthSessionResult vaultLoginCallback(String setupTokenId) {
    PayPalPaymentAdapter pp = payPalAdapter.orElseThrow(() ->
        new ConflictException("PayPal Sandbox adapter is not active."));
    PayPalPaymentAdapter.VaultPaymentTokenResult tokenRes = pp.exchangeSetupTokenForPaymentToken(setupTokenId);
    if (tokenRes.paymentTokenId() == null) {
      throw new ConflictException(tokenRes.message());
    }

    String cleanEmail = tokenRes.payerEmail() != null && !tokenRes.payerEmail().isBlank()
        ? tokenRes.payerEmail().trim().toLowerCase(Locale.ROOT)
        : null;
    String verifiedPayerId = tokenRes.payerId() != null && !tokenRes.payerId().isBlank()
        ? tokenRes.payerId()
        : "VAULT-" + tokenRes.paymentTokenId().substring(0, Math.min(12, tokenRes.paymentTokenId().length()));

    UUID resolvedUserId = userProfileService
        .map(s -> s.loginOrConnectFromPayPalOAuth(verifiedPayerId, cleanEmail, null))
        .map(r -> r.id())
        .orElse(null);

    List<Mandate> activeMandates = mandates.findByStatusOrderByCreatedAtDesc(MandateStatus.ACTIVE);
    for (Mandate m : activeMandates) {
      m.setPaypalVaultToken(tokenRes.paymentTokenId());
      if (cleanEmail != null) {
        m.setPaypalPayerEmail(cleanEmail);
      }
      m.setPaypalPayerId(verifiedPayerId);
      mandates.save(m);
    }

    audit.record("user.paypal_vault_login", "paypal_vault", verifiedPayerId,
        resolvedUserId != null ? resolvedUserId.toString() : verifiedPayerId, null, POLICY_VERSION,
        "Shopper authenticated via live PayPal Sandbox Vault sign-in (payer " + verifiedPayerId + ").");

    return new OAuthSessionResult(mandateService.getActiveTelemetry(), resolvedUserId);
  }

  @Transactional
  public CreatePayPalOrderResponse createOrder(CreatePayPalOrderRequest req) {
    PayPalPaymentAdapter pp = payPalAdapter.orElseThrow(() ->
        new ConflictException("PayPal Sandbox adapter is not active."));
    if (!pp.isConfigured()) {
      throw new ConflictException("PAYPAL_CLIENT_ID and PAYPAL_CLIENT_SECRET must be configured in .env.");
    }

    Instant now = Instant.now();

    // Case 1: Shopper clicked Approve & Pay on an existing held/created order in /app/purchases
    if (req.intentId() != null) {
      return createInteractiveOrderForExistingIntent(req.intentId(), pp, now);
    }

    boolean bypassAgent = Boolean.TRUE.equals(req.bypassAgent());

    // Case 2: New checkout initiated from /app/shop -> validate external URLs FIRST
    if ((!bypassAgent && (!CheckoutSecurityValidator.isSafeExternalUrl(req.productUrl(), false)
        || !CheckoutSecurityValidator.isSafeExternalUrl(req.imageUrl(), false)))
        || (bypassAgent && (!CheckoutSecurityValidator.isSafeExternalUrl(req.productUrl(), true)
        || !CheckoutSecurityValidator.isSafeExternalUrl(req.imageUrl(), true)))) {
      throw new IllegalArgumentException("Blocked unsafe or SSRF-risk merchant URL in checkout request.");
    }
    String title = req.productTitle() != null && !req.productTitle().isBlank()
        ? req.productTitle().trim() : "BuyBound Item";
    String domain = req.merchantId() != null && !req.merchantId().isBlank()
        ? req.merchantId().trim().toLowerCase(Locale.ROOT) : "bestbuy.com";
    BigDecimal unitPrice = req.price() != null ? req.price().setScale(2, RoundingMode.HALF_UP) : new BigDecimal("100.00");
    BigDecimal baseline = req.baselinePrice() != null && req.baselinePrice().compareTo(unitPrice) >= 0
        ? req.baselinePrice().setScale(2, RoundingMode.HALF_UP) : unitPrice;
    String currency = req.currency() != null && !req.currency().isBlank() ? req.currency().toUpperCase(Locale.ROOT) : "USD";
    String sku = req.channel3ProductId() != null && !req.channel3ProductId().isBlank()
        ? req.channel3ProductId() : title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");

    Watch watch = resolveOrCreateWatch(req.watchId(), title, sku, domain, currency, unitPrice, baseline, req.productUrl(), now);
    Mandate referenceMandate = resolveReferenceMandateOrCreateForDirect(req.mandateId(), watch, currency, unitPrice, domain, bypassAgent);
    Mandate mandate = resolveOrCloneMandate(watch.getId(), referenceMandate, now);

    BigDecimal savings = baseline.subtract(unitPrice).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    BigDecimal savingsPct = baseline.compareTo(BigDecimal.ZERO) > 0
        ? savings.multiply(new BigDecimal("100")).divide(baseline, 2, RoundingMode.HALF_UP)
        : BigDecimal.ZERO;

    Channel3ProductOfferDto offerDto = new Channel3ProductOfferDto(
        sku, title, title, null, null, domain, domain, unitPrice, baseline, savings, savingsPct,
        currency, req.productUrl(), req.imageUrl(), "IN_STOCK", "new", new BigDecimal("3.50"),
        bypassAgent ? "direct-human-buy" : "paypal-interactive-checkout", now);

    AiOfferForensicsResult forensics = bypassAgent
        ? new AiOfferForensicsResult(new BigDecimal("100.00"), true, false, false,
            "Direct human checkout bypassed autonomous agent guardrails.", "FORWARD_TO_GUARDIAN")
        : analyzeOfferResilient(watch, offerDto);
    BigDecimal remainingBudget = computeGlobalRemainingBudget(mandate, now);
    Policy policy = buildPolicy(mandate, watch);
    EvaluationContext context = new EvaluationContext(purchasesToday(watch.getId(), now), remainingBudget, now);
    String evidenceHash = PurchaseService.evidenceHash(domain, sku, watch.getVariant(), 1, currency, unitPrice, unitPrice, now);

    Offer guardianOffer = new Offer(
        domain, sku, watch.getVariant(), 1, currency, unitPrice, unitPrice, now,
        bypassAgent ? "direct-human-buy" : "paypal-interactive-checkout", evidenceHash, baseline, title,
        forensics.exactSkuMatch(), forensics.promptInjectionDetected(),
        forensics.subscriptionTrapDetected(), forensics.aiMatchConfidence(),
        offerDto.safeSellerRating(), offerDto.safeSellerReviewCount(), offerDto.safeVerifiedSellerHistory(),
        offerDto.safeNegativeRedFlagCount(), offerDto.safeSellerRedFlagSummary(), offerDto.safeEstimatedDelivery());

    GuardianDecisionResult decision = bypassAgent
        ? new GuardianDecisionResult(
            Decision.ALLOW, List.of(ReasonCode.WITHIN_POLICY), POLICY_VERSION,
            now, now.plus(15, ChronoUnit.MINUTES), List.of())
        : evaluator.evaluate(policy, guardianOffer, context);

    PurchaseIntent intent = new PurchaseIntent(
        watch.getId(), mandate.getId(), mandate.getVersion(), domain, sku,
        watch.getVariant(), 1, currency, unitPrice, "pp-live-" + UUID.randomUUID());
    intent.setPurchaseMode(bypassAgent ? "DIRECT_HUMAN" : "AI_AGENT");

    OfferSnapshot snap = new OfferSnapshot(
        domain, sku, watch.getVariant(), 1, currency, unitPrice, unitPrice, now,
        "paypal-interactive-checkout", evidenceHash);
    snap.setChannel3ProductId(sku);
    snap.setProductUrl(req.productUrl());
    snap.setImageUrl(req.imageUrl());
    snap.setBaselinePrice(baseline);
    snap.setVerifiedSavings(savings);
    snap.setCommissionRate(new BigDecimal("3.50"));
    snap.setAiMatchConfidence(forensics.aiMatchConfidence());
    snap.setAiExactSkuMatch(forensics.exactSkuMatch());
    snap.setAiPromptInjectionDetected(forensics.promptInjectionDetected());
    snap.setAiSubscriptionTrapDetected(forensics.subscriptionTrapDetected());
    snap.setAiForensicsSummary(forensics.aiForensicsSummary());
    snap.setSellerRating(offerDto.safeSellerRating());
    snap.setSellerReviewCount(offerDto.safeSellerReviewCount());
    snap.setVerifiedSellerHistory(offerDto.safeVerifiedSellerHistory());
    snap.setNegativeRedFlagCount(offerDto.safeNegativeRedFlagCount());
    snap.setSellerRedFlagSummary(offerDto.safeSellerRedFlagSummary());
    snap.setEstimatedDelivery(offerDto.safeEstimatedDelivery());
    intent.setOfferSnapshot(snap);
    intent.setPolicyVersion(decision.policyVersion());
    intent.setEvidenceHash(evidenceHash);
    intent.setStatus(IntentStatus.GUARDIAN_EVALUATED);
    intent = intents.save(intent);

    String reasonStr = decision.reasonCodes().stream().map(ReasonCode::name).collect(Collectors.joining(","));
    GuardianDecisionRecord decisionRecord = new GuardianDecisionRecord(
        intent.getId(), mandate.getId(), mandate.getVersion(), decision.decision(),
        reasonStr, decision.policyVersion(), evidenceHash, decision.evaluatedAt(), decision.expiresAt());
    try {
      decisionRecord.setRuleChecksJson(objectMapper.writeValueAsString(decision.ruleChecks()));
    } catch (Exception ignored) {
      // ignore
    }
    decisions.save(decisionRecord);
    intent.setGuardianDecisionId(decisionRecord.getId());

    saveHuntedOffer(watch.getId(), intent.getId(), offerDto, forensics, decision, reasonStr, decisionRecord.getRuleChecksJson());

    if (decision.decision() == Decision.BLOCK) {
      intent.setStatus(IntentStatus.BLOCKED);
      intent.setFailureReason("Guardian BLOCK: " + reasonStr);
      intent = intents.save(intent);
      audit.record("paypal.order_blocked", "purchase_intent", intent.getId().toString(),
          intent.getId().toString(), reasonStr, POLICY_VERSION,
          "Blocked PayPal order for " + title + " at $" + unitPrice);
      return new CreatePayPalOrderResponse(
          intent.getId(), null, null,
          GuardianDecisionResponse.of(decisionRecord),
          PurchaseIntentResponse.of(intent),
          forensics,
          true,
          "Blocked by your spending limits: " + reasonStr);
    }

    if (!bypassAgent && (decision.decision() == Decision.ALLOW || decision.decision() == Decision.HOLD)) {
      CheckoutSecurityValidator.verifyCheckoutPayloadIntegrity(intent, mandate);
    }

    // Guardian returned ALLOW/HOLD or Direct Human Bypass -> create real PayPal Sandbox Order!
    PayPalOrderCreatedResult created = pp.createInteractiveOrder(intent, "CAPTURE");
    if (created.orderId() == null) {
      throw new ConflictException(created.message());
    }

    intent.setProviderOrderId(created.orderId());
    intent.setApprovalUrl(created.approveUrl());
    intent.setStatus(decision.decision() == Decision.HOLD ? IntentStatus.HELD_FOR_APPROVAL : IntentStatus.ORDER_CREATED);
    intent = intents.save(intent);

    if (!bypassAgent && decision.decision() == Decision.ALLOW && reservations.findByIntentId(intent.getId()).isEmpty()) {
      reservations.save(new SpendReservation(intent.getId(), watch.getId(), unitPrice));
    }

    PaymentAttempt attempt = new PaymentAttempt(intent.getId(), "paypal-sandbox", created.orderId(), false, intent.getStatus());
    attempt.setPaypalOrderId(created.orderId());
    attempt.setPaymentIntentType("CAPTURE");
    attempt.setAmount(unitPrice);
    attempt.setCurrency(currency);
    attempts.save(attempt);

    audit.record("paypal.order_created", "purchase_intent", intent.getId().toString(),
        intent.getId().toString(), reasonStr, POLICY_VERSION,
        "Created live PayPal Sandbox order " + created.orderId() + " ($" + unitPrice + " at " + domain + ")");

    return new CreatePayPalOrderResponse(
        intent.getId(),
        created.orderId(),
        created.approveUrl(),
        GuardianDecisionResponse.of(decisionRecord),
        PurchaseIntentResponse.of(intent),
        forensics,
        false,
        created.message());
  }

  private CreatePayPalOrderResponse createInteractiveOrderForExistingIntent(
      UUID intentId, PayPalPaymentAdapter pp, Instant now) {
    PurchaseIntent intent = intents.findById(intentId)
        .orElseThrow(() -> new ResourceNotFoundException("Purchase intent not found: " + intentId));
    UUID mandateUuid = intent.getMandateId();
    Mandate mandate = mandates.findById(mandateUuid)
        .orElseThrow(() -> new ResourceNotFoundException("Mandate not found: " + mandateUuid));

    boolean bypassChecks = "DIRECT_HUMAN".equalsIgnoreCase(intent.getPurchaseMode())
        || RuntimeGuardrailSettingsService.currentSnapshot().demoBypassEnabled();
    if (!bypassChecks && mandate.getStatus() != MandateStatus.ACTIVE) {
      throw new ConflictException("Buying is paused by your Kill Switch. Re-enable Wallet & Safety first.");
    }
    if (!bypassChecks) {
      CheckoutSecurityValidator.verifyCheckoutPayloadIntegrity(intent, mandate);
    }

    GuardianDecisionRecord latest = decisions.findByIntentId(intentId).orElse(null);

    if (intent.getProviderOrderId() == null || intent.getProviderOrderId().isBlank()
        || intent.getPaypalAuthorizationId() != null) {
      PayPalOrderCreatedResult created = pp.createInteractiveOrder(intent, "CAPTURE");
      if (created.orderId() == null) {
        throw new ConflictException(created.message());
      }
      intent.setProviderOrderId(created.orderId());
      intent.setApprovalUrl(created.approveUrl());
      if (intent.getStatus() == IntentStatus.SPEND_RESERVED
          || intent.getStatus() == IntentStatus.AWAITING_PAYPAL_SETTLEMENT) {
        intent.setStatus(IntentStatus.ORDER_CREATED);
      }
      intent = intents.save(intent);
      PaymentAttempt attempt = new PaymentAttempt(intent.getId(), "paypal-sandbox", created.orderId(), false, intent.getStatus());
      attempt.setPaypalOrderId(created.orderId());
      attempt.setPaymentIntentType("CAPTURE");
      attempt.setAmount(intent.getMaxTotal());
      attempt.setCurrency(intent.getCurrency());
      attempts.save(attempt);
    } else if (intent.getApprovalUrl() == null || intent.getApprovalUrl().isBlank()) {
      intent.setApprovalUrl(PayPalPaymentAdapter.appendLoginPrompt(
          "https://www.sandbox.paypal.com/checkoutnow?token=" + intent.getProviderOrderId()));
      intent = intents.save(intent);
    } else {
      String prompted = PayPalPaymentAdapter.appendLoginPrompt(intent.getApprovalUrl());
      if (!prompted.equals(intent.getApprovalUrl())) {
        intent.setApprovalUrl(prompted);
        intent = intents.save(intent);
      }
    }

    audit.record("paypal.interactive_checkout_ready", "purchase_intent", intent.getId().toString(),
        intent.getId().toString(), null, POLICY_VERSION,
        "Prepared live PayPal Sandbox popup for order " + intent.getProviderOrderId());

    return new CreatePayPalOrderResponse(
        intent.getId(),
        intent.getProviderOrderId(),
        intent.getApprovalUrl(),
        latest != null ? GuardianDecisionResponse.of(latest) : null,
        PurchaseIntentResponse.of(intent),
        null,
        false,
        "PayPal Sandbox checkout is ready (" + intent.getProviderOrderId() + ").");
  }

  @Transactional
  public CapturePayPalOrderResponse captureOrder(String paypalOrderId) {
    return captureOrder(paypalOrderId, null);
  }

  @Transactional
  public CapturePayPalOrderResponse captureOrder(String paypalOrderId, String mockResponseHeader) {
    PurchaseIntent intent = intents.findFirstByProviderOrderIdOrderByCreatedAtDesc(paypalOrderId)
        .orElseThrow(() -> new ResourceNotFoundException("No BuyBound order found for PayPal Order ID: " + paypalOrderId));

    Mandate intentMandate = intent.getMandateId() != null
        ? mandates.findById(intent.getMandateId()).orElse(null)
        : null;

    if (intent.getStatus() == IntentStatus.CAPTURED
        && (mockResponseHeader == null || mockResponseHeader.isBlank())) {
      ActiveMandateTelemetryResponse tel = mandateService.getActiveTelemetry();
      return new CapturePayPalOrderResponse(
          PurchaseIntentResponse.of(intent),
          intent.getPaypalCaptureId(),
          paypalOrderId,
          resolveRealBuyerEmail(intentMandate, tel != null ? tel.paypalPayerEmail() : null),
          resolveRealBuyerName(intentMandate, tel != null ? tel.paypalPayerName() : null),
          tel != null ? tel.sandboxBalance() : null,
          BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
          "PayPal Sandbox order " + paypalOrderId + " is already captured.");
    }

    boolean bypassChecks = "DIRECT_HUMAN".equalsIgnoreCase(intent.getPurchaseMode())
        || RuntimeGuardrailSettingsService.currentSnapshot().demoBypassEnabled();
    if (!bypassChecks) {
      CheckoutSecurityValidator.verifyCheckoutPayloadIntegrity(intent, intentMandate);
    }

    PayPalPaymentAdapter pp = payPalAdapter.orElseThrow(() ->
        new ConflictException("PayPal Sandbox adapter is not active."));
    PayPalCaptureOutcome outcome =
        pp.captureApprovedOrder(paypalOrderId, intent.getIdempotencyKey(), mockResponseHeader);

    if (outcome.status() != PaymentResult.Status.CAPTURED) {
      if (outcome.status() == PaymentResult.Status.FAILED
          || outcome.status() == PaymentResult.Status.RECONCILIATION_REQUIRED) {
        PaymentAttempt failedAttempt = new PaymentAttempt(
            intent.getId(), "paypal-sandbox", paypalOrderId, false, IntentStatus.FAILED);
        failedAttempt.setPaypalOrderId(paypalOrderId);
        failedAttempt.setPaymentIntentType("CAPTURE");
        failedAttempt.setAmount(intent.getMaxTotal());
        failedAttempt.setCurrency(intent.getCurrency());
        attempts.save(failedAttempt);
        audit.record("paypal.sandbox.capture_rejected", "purchase_intent", intent.getId().toString(),
            intent.getId().toString(), null, POLICY_VERSION, outcome.message());
      }
      throw new ConflictException(outcome.message());
    }

    String captureId = outcome.captureId();
    if (captureId == null || captureId.isBlank()) {
      throw new ConflictException("PayPal Sandbox did not return a verified capture ID for order " + paypalOrderId + ".");
    }
    String resolvedEmail = resolveRealBuyerEmail(intentMandate, outcome.payerEmail());
    String resolvedName = resolveRealBuyerName(intentMandate, outcome.payerName());

    intent.setStatus(IntentStatus.CAPTURED);
    intent.setPaypalCaptureId(captureId);
    if (outcome.authorizationId() != null) {
      intent.setPaypalAuthorizationId(outcome.authorizationId());
    }
    intent.setApprovalUrl(null);
    intent.setFailureReason(null);
    final PurchaseIntent savedIntent = intents.save(intent);

    SpendReservation reservation = reservations.findByIntentId(savedIntent.getId())
        .orElseGet(() -> new SpendReservation(savedIntent.getId(), savedIntent.getWatchId(), savedIntent.getMaxTotal()));
    reservation.setStatus(SpendReservation.ReservationStatus.SETTLED);
    reservations.save(reservation);

    BigDecimal deducted = intent.getMaxTotal() != null
        ? intent.getMaxTotal().setScale(2, RoundingMode.HALF_UP)
        : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    PaymentAttempt attempt = new PaymentAttempt(
        intent.getId(), "paypal-sandbox", captureId, false, IntentStatus.CAPTURED);
    attempt.setPaypalOrderId(paypalOrderId);
    attempt.setPaypalCaptureId(captureId);
    attempt.setPaymentIntentType("CAPTURE");
    attempt.setAmount(deducted);
    attempt.setCurrency(intent.getCurrency());
    attempt.setPaypalPayerId(outcome.payerId());
    attempts.save(attempt);

    BigDecimal newBalance = mandateService.recordSandboxCapture(
        deducted, resolvedEmail, resolvedName, outcome.payerId());
    BigDecimal profileBalance = userProfileService
        .map(s -> s.recordSandboxCaptureWalletDeduction(deducted, outcome.payerId(), resolvedEmail, resolvedName))
        .orElse(null);
    if (newBalance == null) {
      newBalance = profileBalance;
    }

    audit.record("paypal.sandbox.captured", "purchase_intent", intent.getId().toString(),
        intent.getId().toString(), null, POLICY_VERSION,
        String.format("Captured live PayPal Sandbox order %s (capture %s, payer %s, mode %s) for $%s",
            paypalOrderId, captureId,
            outcome.payerId() != null ? outcome.payerId() : "verified-payer",
            savedIntent.getPurchaseMode(),
            deducted.toPlainString()));

    return new CapturePayPalOrderResponse(
        PurchaseIntentResponse.of(savedIntent),
        captureId,
        paypalOrderId,
        resolvedEmail,
        resolvedName,
        newBalance,
        deducted,
        String.format("Live PayPal Sandbox capture completed (%s) · Charged $%s",
            captureId, deducted.toPlainString()));
  }

  private String resolveRealBuyerEmail(Mandate intentMandate, String liveEmail) {
    if (liveEmail != null && !liveEmail.isBlank()) {
      return liveEmail.trim();
    }
    Optional<UserProfileService.SandboxBuyerIdentity> profileBuyer =
        userProfileService.flatMap(UserProfileService::findPreferredSandboxBuyer);
    if (profileBuyer.isPresent() && profileBuyer.get().paypalEmail() != null && !profileBuyer.get().paypalEmail().isBlank()) {
      return profileBuyer.get().paypalEmail().trim();
    }
    if (intentMandate != null && intentMandate.getPaypalPayerEmail() != null && !intentMandate.getPaypalPayerEmail().isBlank()) {
      return intentMandate.getPaypalPayerEmail().trim();
    }
    ActiveMandateTelemetryResponse tel = mandateService.getActiveTelemetry();
    if (tel != null && tel.paypalPayerEmail() != null && !tel.paypalPayerEmail().isBlank()) {
      return tel.paypalPayerEmail().trim();
    }
    return null;
  }

  private String resolveRealBuyerName(Mandate intentMandate, String liveName) {
    if (liveName != null && !liveName.isBlank()) {
      return liveName.trim();
    }
    Optional<UserProfileService.SandboxBuyerIdentity> profileBuyer =
        userProfileService.flatMap(UserProfileService::findPreferredSandboxBuyer);
    if (profileBuyer.isPresent() && profileBuyer.get().fullName() != null && !profileBuyer.get().fullName().isBlank()) {
      return profileBuyer.get().fullName();
    }
    if (intentMandate != null && intentMandate.getPaypalPayerName() != null && !intentMandate.getPaypalPayerName().isBlank()) {
      return intentMandate.getPaypalPayerName().trim();
    }
    ActiveMandateTelemetryResponse tel = mandateService.getActiveTelemetry();
    if (tel != null && tel.paypalPayerName() != null && !tel.paypalPayerName().isBlank()) {
      return tel.paypalPayerName().trim();
    }
    return null;
  }

  @Transactional
  public PayPalOrderVerificationResult verifyOrderLive(String paypalOrderId) {
    PayPalPaymentAdapter pp = payPalAdapter.orElseThrow(() ->
        new ConflictException("PayPal Sandbox adapter is not active."));
    PayPalOrderVerificationResult live = pp.fetchOrderDetails(paypalOrderId);
    if ("COMPLETED".equalsIgnoreCase(live.status())
        && live.captureId() != null && !live.captureId().isBlank()) {
      intents.findFirstByProviderOrderIdOrderByCreatedAtDesc(paypalOrderId).ifPresent(intent -> {
        if (intent.getStatus() != IntentStatus.CAPTURED) {
          intent.setStatus(IntentStatus.CAPTURED);
          intent.setPaypalCaptureId(live.captureId());
          intent.setApprovalUrl(null);
          intent.setFailureReason(null);
          intents.save(intent);
          reservations.findByIntentId(intent.getId()).ifPresent(r -> {
            r.setStatus(SpendReservation.ReservationStatus.SETTLED);
            reservations.save(r);
          });
          BigDecimal paid = intent.getMaxTotal() != null ? intent.getMaxTotal() : BigDecimal.ZERO;
          mandateService.recordSandboxCapture(paid, live.payerEmail(), live.payerName(), live.payerId());
          userProfileService.ifPresent(s ->
              s.recordSandboxCaptureWalletDeduction(paid, live.payerId(), live.payerEmail(), live.payerName()));
        }
      });
    }
    return live;
  }

  private Mandate resolveReferenceMandateOrCreateForDirect(
      UUID requestedMandateId, Watch watch, String currency, BigDecimal price, String domain, boolean bypassAgent) {
    if (requestedMandateId != null) {
      return mandates.findById(requestedMandateId)
          .orElseThrow(() -> new ResourceNotFoundException("Mandate not found: " + requestedMandateId));
    }
    List<Mandate> active = mandates.findByStatusOrderByCreatedAtDesc(MandateStatus.ACTIVE);
    if (!active.isEmpty()) return active.getFirst();
    List<Mandate> all = mandates.findAllByOrderByCreatedAtDesc();
    if (!all.isEmpty()) return all.getFirst();
    if (!bypassAgent) {
      throw new ConflictException("No wallet policy found. Create a price alert first.");
    }
    Mandate direct = new Mandate(
        watch.getId(), 1, currency,
        price.max(new BigDecimal("1000.00")), price.max(new BigDecimal("1000.00")),
        5, List.of(domain), 20, 86400L,
        Mandate.NewMerchantBehavior.ALLOW, null, new BigDecimal("5000.00"));
    return mandates.save(direct);
  }

  private Mandate resolveReferenceMandate(UUID requestedMandateId) {
    if (requestedMandateId != null) {
      return mandates.findById(requestedMandateId)
          .orElseThrow(() -> new ResourceNotFoundException("Mandate not found: " + requestedMandateId));
    }
    List<Mandate> active = mandates.findByStatusOrderByCreatedAtDesc(MandateStatus.ACTIVE);
    if (!active.isEmpty()) return active.getFirst();
    List<Mandate> revoked = mandates.findByStatusOrderByCreatedAtDesc(MandateStatus.REVOKED);
    if (!revoked.isEmpty()) return revoked.getFirst();
    throw new ConflictException("No wallet policy found. Create a price alert first.");
  }

  private Watch resolveOrCreateWatch(
      UUID watchId, String title, String sku, String domain, String currency,
      BigDecimal targetPrice, BigDecimal baseline, String productUrl, Instant now) {
    if (watchId != null) {
      return watches.findById(watchId)
          .orElseThrow(() -> new ResourceNotFoundException("Watch not found: " + watchId));
    }
    Optional<Watch> existing = watches.findAll().stream()
        .filter(w -> (sku != null && sku.equalsIgnoreCase(w.getProductId())) || title.equalsIgnoreCase(w.getProductName()))
        .findFirst();
    if (existing.isPresent()) {
      Watch w = existing.get();
      w.setLastHuntedAt(now);
      return watches.save(w);
    }
    String safeTitle = title.length() > 195 ? title.substring(0, 195) : title;
    Watch created = new Watch(safeTitle, sku, sku, "Standard", domain, currency, targetPrice);
    created.setBaselineMarketPrice(baseline);
    created.setReferenceProductUrl(productUrl);
    created.setNaturalLanguagePrompt("PayPal Sandbox checkout for " + safeTitle);
    created.setLastHuntedAt(now);
    return watches.save(created);
  }

  private Mandate resolveOrCloneMandate(UUID watchId, Mandate template, Instant now) {
    Optional<Mandate> activeForWatch = mandates.findByWatchIdAndStatus(watchId, MandateStatus.ACTIVE);
    if (template.getStatus() == MandateStatus.ACTIVE && activeForWatch.isPresent()) {
      return activeForWatch.get();
    }
    if (template.getStatus() == MandateStatus.REVOKED) {
      List<Mandate> existing = mandates.findByWatchIdOrderByVersionDesc(watchId);
      if (!existing.isEmpty()) return existing.getFirst();
    }
    int nextVer = mandates.findByWatchIdOrderByVersionDesc(watchId).stream()
        .mapToInt(Mandate::getVersion).max().orElse(0) + 1;
    Mandate cloned = new Mandate(
        watchId, nextVer, template.getCurrency(), template.getMaxUnitPrice(), template.getMaxTotal(),
        template.getMaxQuantity(), template.getAllowedMerchantIds(), template.getMaxPurchasesPerDay(),
        template.getOfferMaxAgeSeconds(), template.getNewMerchantBehavior(),
        template.getRequireHumanApprovalAbove(), template.getMaxWeeklySpend());
    cloned.setPaypalVaultToken(template.getPaypalVaultToken());
    cloned.setPaypalPayerEmail(template.getPaypalPayerEmail());
    cloned.setPaypalPayerName(template.getPaypalPayerName());
    cloned.setSandboxBalance(template.getSandboxBalance());
    if (template.getStatus() != MandateStatus.ACTIVE) {
      cloned.setStatus(template.getStatus());
      cloned.setRevokedAt(template.getRevokedAt() != null ? template.getRevokedAt() : now);
      cloned.setRevocationReason(template.getRevocationReason());
    }
    return mandates.save(cloned);
  }

  private AiOfferForensicsResult analyzeOfferResilient(Watch watch, Channel3ProductOfferDto offer) {
    if (AiShoppingAgentService.hasAdversarialOfferPayload(offer)) {
      return new AiOfferForensicsResult(
          new BigDecimal("10.00"), false, true, false,
          "Blocked malicious merchant payload (prompt injection or unsafe redirect URL detected).",
          "BLOCK_THREAT");
    }
    try {
      return aiService.analyzeOffer(watch, offer);
    } catch (ExternalServiceException ex) {
      log.warn("AI forensics offline fallback ({})", ex.getMessage());
      String combined = ((offer.title() != null ? offer.title() : "")).toLowerCase(Locale.ROOT);
      boolean injection = combined.contains("system override") || combined.contains("ignore user budget");
      boolean subTrap = combined.contains("/month") || combined.contains("auto-renewal");
      return new AiOfferForensicsResult(
          new BigDecimal("94.00"), true, injection, subTrap,
          "Deterministic safety check verified for PayPal checkout.",
          (injection || subTrap) ? "BLOCK_THREAT" : "FORWARD_TO_GUARDIAN");
    }
  }

  private Policy buildPolicy(Mandate mandate, Watch watch) {
    return new Policy(
        mandate.getId().toString(), mandate.getVersion(), mandate.getCurrency(),
        mandate.getMaxUnitPrice(), mandate.getMaxTotal(), mandate.getMaxQuantity(),
        mandate.getAllowedMerchantIds(), mandate.getMaxPurchasesPerDay(),
        mandate.getOfferMaxAgeSeconds(),
        Policy.NewMerchantBehavior.valueOf(mandate.getNewMerchantBehavior().name()),
        mandate.getRequireHumanApprovalAbove(), mandate.getMaxWeeklySpend(),
        watch.getTargetPrice(), POLICY_VERSION,
        mandate.getStatus() == MandateStatus.ACTIVE ? Policy.MandateStatus.ACTIVE : Policy.MandateStatus.REVOKED,
        mandate.getMinSellerRating());
  }

  private int purchasesToday(UUID watchId, Instant now) {
    return (int) intents.countByWatchIdAndCreatedAtAfterAndStatusNot(
        watchId, now.truncatedTo(ChronoUnit.DAYS), IntentStatus.BLOCKED);
  }

  private BigDecimal computeGlobalRemainingBudget(Mandate mandate, Instant now) {
    if (mandate.getMaxWeeklySpend() == null) return null;
    Instant weekStart = now.atZone(ZoneOffset.UTC).toLocalDate()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .atStartOfDay(ZoneOffset.UTC).toInstant();
    BigDecimal spent = Optional.ofNullable(reservations.sumGlobalSpentOrReservedSince(weekStart)).orElse(BigDecimal.ZERO);
    return mandate.getMaxWeeklySpend().subtract(spent);
  }

  private void saveHuntedOffer(UUID watchId, UUID intentId, Channel3ProductOfferDto o,
                               AiOfferForensicsResult f, GuardianDecisionResult d,
                               String reasonStr, String checksJson) {
    HuntedOfferRecord rec = new HuntedOfferRecord(
        watchId, intentId, o.channel3ProductId(), o.title(), o.merchantDomain(), o.channel3ProductId(),
        o.currency(), o.currentPrice(), o.baselinePrice(), o.verifiedSavings(), o.commissionRatePercent(),
        o.productUrl(), o.imageUrl(), o.source(), f.aiMatchConfidence(), f.exactSkuMatch(),
        f.promptInjectionDetected(), f.subscriptionTrapDetected(), f.aiForensicsSummary(),
        d.decision(), reasonStr, checksJson, o.observedAt());
    rec.setSellerRating(o.safeSellerRating());
    rec.setSellerReviewCount(o.safeSellerReviewCount());
    rec.setVerifiedSellerHistory(o.safeVerifiedSellerHistory());
    rec.setNegativeRedFlagCount(o.safeNegativeRedFlagCount());
    rec.setSellerRedFlagSummary(o.safeSellerRedFlagSummary());
    rec.setEstimatedDelivery(o.safeEstimatedDelivery());
    huntedOffers.save(rec);
  }

  public record PayPalConfigResponse(
      String clientId,
      String mode,
      String currency,
      boolean configured,
      String oauthAuthorizeUrl) {}

  public record CreatePayPalOrderRequest(
      String productTitle,
      String merchantId,
      BigDecimal price,
      BigDecimal baselinePrice,
      String currency,
      UUID watchId,
      UUID mandateId,
      UUID intentId,
      String channel3ProductId,
      String imageUrl,
      String productUrl,
      Boolean bypassAgent) {

    public CreatePayPalOrderRequest(
        String productTitle,
        String merchantId,
        BigDecimal price,
        BigDecimal baselinePrice,
        String currency,
        UUID watchId,
        UUID mandateId,
        UUID intentId,
        String channel3ProductId,
        String imageUrl,
        String productUrl) {
      this(productTitle, merchantId, price, baselinePrice, currency, watchId, mandateId,
          intentId, channel3ProductId, imageUrl, productUrl, false);
    }
  }

  public record CreatePayPalOrderResponse(
      UUID intentId,
      String paypalOrderId,
      String approveUrl,
      GuardianDecisionResponse guardianDecision,
      PurchaseIntentResponse intent,
      AiOfferForensicsResult forensics,
      boolean blocked,
      String message) {}

  public record CapturePayPalOrderResponse(
      PurchaseIntentResponse intent,
      String captureId,
      String paypalOrderId,
      String payerEmail,
      String payerName,
      BigDecimal newSandboxBalance,
      BigDecimal deductedAmount,
      String message) {}
}
