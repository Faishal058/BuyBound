package com.buybound.purchase.service;

import com.buybound.audit.AuditEvent;
import com.buybound.audit.AuditEventRepository;
import com.buybound.audit.AuditService;
import com.buybound.common.exception.ConflictException;
import com.buybound.common.exception.ResourceNotFoundException;
import com.buybound.guardian.domain.Decision;
import com.buybound.guardian.domain.EvaluationContext;
import com.buybound.guardian.domain.GuardianDecisionResult;
import com.buybound.guardian.domain.Offer;
import com.buybound.guardian.domain.Policy;
import com.buybound.guardian.domain.ReasonCode;
import com.buybound.guardian.service.GuardianEvaluator;
import com.buybound.guardian.service.RuntimeGuardrailSettingsService;
import com.buybound.payment.PayPalPaymentAdapter;
import com.buybound.payment.PayPalPaymentAdapter.PayPalExecutionOutcome;
import com.buybound.payment.PaymentAdapter;
import com.buybound.payment.PaymentResult;
import com.buybound.purchase.domain.GuardianDecisionRecord;
import com.buybound.purchase.domain.HuntedOfferRecord;
import com.buybound.purchase.domain.IntentStatus;
import com.buybound.purchase.domain.OfferSnapshot;
import com.buybound.purchase.domain.PaymentAttempt;
import com.buybound.purchase.domain.PurchaseIntent;
import com.buybound.purchase.domain.SpendReservation;
import com.buybound.purchase.dto.DemoCaptureResponse;
import com.buybound.purchase.dto.GuardianDecisionResponse;
import com.buybound.purchase.dto.HuntedOfferDto;
import com.buybound.purchase.dto.OfferEvaluationResponse;
import com.buybound.purchase.dto.PurchaseActivityDto;
import com.buybound.purchase.dto.PurchaseIntentResponse;
import com.buybound.purchase.dto.SubmitOfferRequest;
import com.buybound.purchase.repository.GuardianDecisionRepository;
import com.buybound.purchase.repository.HuntedOfferRepository;
import com.buybound.purchase.repository.PaymentAttemptRepository;
import com.buybound.purchase.repository.PurchaseIntentRepository;
import com.buybound.purchase.repository.SpendReservationRepository;
import com.buybound.user.domain.UserProfile;
import com.buybound.user.repository.UserProfileRepository;
import com.buybound.user.service.UserProfileService;
import com.buybound.watch.domain.Mandate;
import com.buybound.watch.domain.MandateStatus;
import com.buybound.watch.domain.Watch;
import com.buybound.watch.domain.WatchStatus;
import com.buybound.watch.repository.MandateRepository;
import com.buybound.watch.repository.WatchRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseService {
  private static final Logger log = LoggerFactory.getLogger(PurchaseService.class);
  private static final String POLICY_VERSION = "1.0";

  private final PurchaseIntentRepository intents;
  private final GuardianDecisionRepository decisions;
  private final SpendReservationRepository reservations;
  private final PaymentAttemptRepository attempts;
  private final WatchRepository watches;
  private final MandateRepository mandates;
  private final GuardianEvaluator evaluator;
  private final AuditService audit;
  private final Optional<PaymentAdapter> paymentAdapter;
  private final Optional<PayPalPaymentAdapter> payPalAdapter;
  private final Optional<HuntedOfferRepository> huntedOffers;
  private final ObjectMapper objectMapper;

  @Value("${app.payments.mode:sandbox}")
  private String paymentsMode;

  @Autowired(required = false)
  private UserProfileService userProfileService;

  @Autowired(required = false)
  private UserProfileRepository userProfileRepository;

  @Autowired(required = false)
  private AuditEventRepository auditEventRepository;

  /** Backward-compatible constructor preserved for PurchaseServiceTest. */
  public PurchaseService(PurchaseIntentRepository intents, GuardianDecisionRepository decisions,
                         SpendReservationRepository reservations, PaymentAttemptRepository attempts,
                         WatchRepository watches, MandateRepository mandates,
                         GuardianEvaluator evaluator, AuditService audit,
                         Optional<PaymentAdapter> paymentAdapter) {
    this(intents, decisions, reservations, attempts, watches, mandates, evaluator, audit,
        paymentAdapter, Optional.empty(), Optional.empty(), new ObjectMapper());
  }

  @Autowired
  public PurchaseService(PurchaseIntentRepository intents, GuardianDecisionRepository decisions,
                         SpendReservationRepository reservations, PaymentAttemptRepository attempts,
                         WatchRepository watches, MandateRepository mandates,
                         GuardianEvaluator evaluator, AuditService audit,
                         Optional<PaymentAdapter> paymentAdapter,
                         Optional<PayPalPaymentAdapter> payPalAdapter,
                         Optional<HuntedOfferRepository> huntedOffers,
                         ObjectMapper objectMapper) {
    this.intents = intents;
    this.decisions = decisions;
    this.reservations = reservations;
    this.attempts = attempts;
    this.watches = watches;
    this.mandates = mandates;
    this.evaluator = evaluator;
    this.audit = audit;
    this.paymentAdapter = paymentAdapter;
    this.payPalAdapter = payPalAdapter;
    this.huntedOffers = huntedOffers;
    this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
  }

  @Transactional
  public OfferEvaluationResponse submitOffer(UUID watchId, SubmitOfferRequest req) {
    Watch watch = watches.findByIdForUpdate(watchId)
        .orElseThrow(() -> new ResourceNotFoundException("Watch not found: " + watchId));
    if (watch.getStatus() != WatchStatus.ACTIVE) {
      throw new ConflictException("Watch is not active: " + watch.getStatus());
    }
    Mandate mandate = mandates.findByWatchIdAndStatus(watchId, MandateStatus.ACTIVE)
        .orElseThrow(() -> new ConflictException("No active mandate for watch: " + watchId));

    Instant now = Instant.now();
    String evidenceHash = evidenceHash(req.merchantId(), req.canonicalSku(), req.variant(),
        req.quantity(), req.currency(), req.unitPrice(), req.totalPrice(), now);

    PurchaseIntent intent = new PurchaseIntent(
        watchId, mandate.getId(), mandate.getVersion(), req.merchantId(),
        req.canonicalSku(), req.variant(), req.quantity(), req.currency(),
        req.totalPrice(), "intent-" + UUID.randomUUID());
    intent.setOfferSnapshot(new OfferSnapshot(
        req.merchantId(), req.canonicalSku(), req.variant(), req.quantity(),
        req.currency(), req.unitPrice(), req.totalPrice(), now,
        req.source() != null ? req.source() : "manual", evidenceHash));
    intent.setStatus(IntentStatus.OFFER_VALIDATED);
    intents.save(intent);
    audit.record("offer.validated", "purchase_intent", intent.getId().toString(),
        intent.getId().toString(), null, POLICY_VERSION,
        "Offer validated for watch " + watchId);

    GuardianDecisionRecord decision = evaluateAndPersist(intent, mandate, watch, now);
    transitionAfterDecision(intent, decision, false);

    audit.record("purchase." + intent.getStatus().name().toLowerCase(), "purchase_intent",
        intent.getId().toString(), intent.getId().toString(),
        decision.getReasonCodes(), decision.getPolicyVersion(),
        "Guardian " + decision.getDecision() + " -> " + intent.getStatus());
    return OfferEvaluationResponse.of(intent, decision);
  }

  @Transactional
  public OfferEvaluationResponse approve(UUID intentId) {
    PurchaseIntent intent = getIntent(intentId);
    if (intent.getStatus() != IntentStatus.AWAITING_APPROVAL
        && intent.getStatus() != IntentStatus.HELD_FOR_APPROVAL) {
      throw new ConflictException("Intent is not awaiting approval: " + intent.getStatus());
    }
    Mandate mandate = mandates.findById(intent.getMandateId())
        .orElseThrow(() -> new ResourceNotFoundException("Mandate not found"));
    if (mandate.getStatus() != MandateStatus.ACTIVE
        || mandate.getVersion() != intent.getMandateVersion()) {
      intent.setStatus(IntentStatus.BLOCKED);
      intent.setFailureReason("Mandate changed or revoked since evaluation");
      audit.record("purchase.blocked", "purchase_intent", intentId.toString(),
          intentId.toString(), "MANDATE_REVOKED", POLICY_VERSION,
          "Approval refused: mandate changed or revoked");
      return OfferEvaluationResponse.of(intent, latestDecision(intentId));
    }
    Watch watch = watches.findById(intent.getWatchId())
        .orElseThrow(() -> new ResourceNotFoundException("Watch not found"));

    GuardianDecisionRecord decision = evaluateAndPersist(intent, mandate, watch, Instant.now());
    transitionAfterDecision(intent, decision, true);
    audit.record("purchase." + intent.getStatus().name().toLowerCase(), "purchase_intent",
        intentId.toString(), intentId.toString(), decision.getReasonCodes(),
        decision.getPolicyVersion(), "Human approval revalidated: " + decision.getDecision());
    return OfferEvaluationResponse.of(intent, decision);
  }

  /**
   * Captures a held PayPal authorization (or completes PayPal Sandbox checkout capture)
   * from the AG Grid inline Approve & Capture Hold button.
   */
  @Transactional
  public DemoCaptureResponse approveHold(UUID intentId) {
    PurchaseIntent intent = getIntent(intentId);
    if (intent.getStatus() != IntentStatus.HELD_FOR_APPROVAL
        && intent.getStatus() != IntentStatus.AWAITING_APPROVAL
        && intent.getStatus() != IntentStatus.AWAITING_PAYPAL_SETTLEMENT
        && intent.getStatus() != IntentStatus.APPROVED
        && intent.getStatus() != IntentStatus.ORDER_CREATED
        && intent.getStatus() != IntentStatus.SPEND_RESERVED) {
      throw new ConflictException("Intent is not eligible for hold approval/capture: " + intent.getStatus());
    }

    Mandate mandate = mandates.findById(intent.getMandateId())
        .orElseThrow(() -> new ResourceNotFoundException("Mandate not found"));
    boolean bypassChecks = "DIRECT_HUMAN".equalsIgnoreCase(intent.getPurchaseMode())
        || RuntimeGuardrailSettingsService.currentSnapshot().demoBypassEnabled();
    if (!bypassChecks && mandate.getStatus() != MandateStatus.ACTIVE) {
      intent.setStatus(IntentStatus.BLOCKED);
      intent.setFailureReason("Mandate revoked before hold approval");
      throw new ConflictException("Mandate has been revoked; cannot capture hold.");
    }

    if (!bypassChecks) {
      CheckoutSecurityValidator.verifyCheckoutPayloadIntegrity(intent, mandate);
    }

    // Reserve budget atomically if not already reserved.
    if (reservations.findByIntentId(intent.getId()).isEmpty()) {
      BigDecimal remaining = remainingBudget(mandate, Instant.now());
      if (remaining != null && intent.getMaxTotal().compareTo(remaining) > 0) {
        intent.setStatus(IntentStatus.BLOCKED);
        intent.setFailureReason("Weekly mandate budget exhausted");
        throw new ConflictException("Insufficient remaining mandate budget to capture hold.");
      }
      reservations.save(new SpendReservation(intent.getId(), intent.getWatchId(), intent.getMaxTotal()));
    }

    PayPalPaymentAdapter pp = payPalAdapter.orElseThrow(() ->
        new ConflictException("PayPal Sandbox adapter is not active."));

    PayPalExecutionOutcome outcome = (intent.getPaypalAuthorizationId() != null || intent.getProviderOrderId() != null)
        ? pp.captureHeldIntent(intent)
        : pp.executeAllowCapture(intent, mandate.getPaypalVaultToken());

    return applyPayPalOutcome(intent, outcome, "hold_approved");
  }

  @Transactional
  public DemoCaptureResponse rejectHold(UUID intentId) {
    PurchaseIntent intent = getIntent(intentId);
    if (intent.getStatus() != IntentStatus.HELD_FOR_APPROVAL
        && intent.getStatus() != IntentStatus.AWAITING_APPROVAL
        && intent.getStatus() != IntentStatus.ORDER_CREATED) {
      throw new ConflictException("Intent is not in a hold state: " + intent.getStatus());
    }

    String message = "Hold rejected by user.";
    if (payPalAdapter.isPresent() && intent.getPaypalAuthorizationId() != null) {
      PayPalExecutionOutcome voidRes = payPalAdapter.get().voidHeldIntent(intent);
      message = voidRes.message();
      PaymentAttempt attempt = new PaymentAttempt(intent.getId(), "paypal-sandbox",
          intent.getPaypalAuthorizationId(), false, IntentStatus.REJECTED_BY_USER);
      attempt.setPaypalOrderId(intent.getProviderOrderId());
      attempt.setPaypalAuthorizationId(intent.getPaypalAuthorizationId());
      attempt.setPaymentIntentType("AUTHORIZE_VOID");
      attempts.save(attempt);
    }

    reservations.findByIntentId(intent.getId()).ifPresent(r ->
        r.setStatus(SpendReservation.ReservationStatus.RELEASED));
    intent.setStatus(IntentStatus.REJECTED_BY_USER);
    intent.setFailureReason(message);
    intents.save(intent);

    audit.record("purchase.rejected_by_user", "purchase_intent", intentId.toString(),
        intentId.toString(), null, POLICY_VERSION, message);

    return new DemoCaptureResponse(intent.getId(), intent.getStatus(), false,
        intent.getProviderOrderId(), message);
  }

  @Transactional
  public DemoCaptureResponse syncPayPal(UUID intentId) {
    PurchaseIntent intent = getIntent(intentId);
    if (intent.getProviderOrderId() == null || intent.getProviderOrderId().isBlank()) {
      throw new ConflictException("No PayPal order ID exists on intent " + intentId);
    }
    PayPalPaymentAdapter pp = payPalAdapter.orElseThrow(() ->
        new ConflictException("PayPal Sandbox adapter is not active."));
    PayPalExecutionOutcome outcome = pp.syncAndCaptureOrder(
        intent.getProviderOrderId(), intent.getIdempotencyKey(), null);
    return applyPayPalOutcome(intent, outcome, "paypal_sync");
  }

  private DemoCaptureResponse applyPayPalOutcome(PurchaseIntent intent, PayPalExecutionOutcome outcome, String eventPrefix) {
    if (outcome.orderId() != null) intent.setProviderOrderId(outcome.orderId());
    if (outcome.authorizationId() != null) intent.setPaypalAuthorizationId(outcome.authorizationId());
    if (outcome.captureId() != null) intent.setPaypalCaptureId(outcome.captureId());
    if (outcome.approvalUrl() != null) intent.setApprovalUrl(outcome.approvalUrl());

    IntentStatus nextStatus;
    if (outcome.status() == PaymentResult.Status.CAPTURED) {
      nextStatus = IntentStatus.CAPTURED;
      intent.setApprovalUrl(null);
      reservations.findByIntentId(intent.getId()).ifPresent(r ->
          r.setStatus(SpendReservation.ReservationStatus.SETTLED));
      BigDecimal paid = intent.getMaxTotal() != null ? intent.getMaxTotal() : BigDecimal.ZERO;
      List<Mandate> active = mandates.findByStatusOrderByCreatedAtDesc(MandateStatus.ACTIVE);
      for (Mandate m : active) {
        if (m.getSandboxBalance() != null) {
          m.deductSandboxBalance(paid);
          mandates.save(m);
        }
      }
      if (userProfileService != null) {
        userProfileService.recordSandboxCaptureWalletDeduction(paid, null, null, null);
      }
    } else if (outcome.status() == PaymentResult.Status.PAYER_ACTION_REQUIRED) {
      nextStatus = intent.getStatus() == IntentStatus.HELD_FOR_APPROVAL
          ? IntentStatus.HELD_FOR_APPROVAL
          : IntentStatus.ORDER_CREATED;
    } else if (outcome.status() == PaymentResult.Status.FAILED) {
      nextStatus = IntentStatus.FAILED;
      intent.setFailureReason(outcome.message());
      reservations.findByIntentId(intent.getId()).ifPresent(r ->
          r.setStatus(SpendReservation.ReservationStatus.RELEASED));
    } else {
      nextStatus = IntentStatus.RECONCILIATION_REQUIRED;
      intent.setFailureReason(outcome.message());
    }
    intent.setStatus(nextStatus);
    intents.save(intent);

    String ref = outcome.captureId() != null ? outcome.captureId()
        : (outcome.authorizationId() != null ? outcome.authorizationId() : outcome.orderId());
    PaymentAttempt attempt = new PaymentAttempt(intent.getId(), "paypal-sandbox", ref, false, nextStatus);
    attempt.setPaypalOrderId(outcome.orderId());
    attempt.setPaypalAuthorizationId(outcome.authorizationId());
    attempt.setPaypalCaptureId(outcome.captureId());
    attempt.setPaymentIntentType(outcome.intentType());
    attempt.setAmount(intent.getMaxTotal());
    attempt.setCurrency(intent.getCurrency());
    attempts.save(attempt);

    audit.record("payment." + eventPrefix + "." + nextStatus.name().toLowerCase(),
        "purchase_intent", intent.getId().toString(), intent.getId().toString(),
        null, POLICY_VERSION, outcome.message());

    return new DemoCaptureResponse(intent.getId(), intent.getStatus(), false, ref,
        outcome.message(), outcome.orderId(), outcome.authorizationId(),
        outcome.captureId(), outcome.approvalUrl());
  }

  /**
   * Demo/stub capture is permanently disabled; all transactions execute against live PayPal Sandbox.
   */
  @Transactional
  public DemoCaptureResponse demoCapture(UUID intentId) {
    throw new ConflictException(
        "Demo stub capture is disabled: all BuyBound checkouts execute in real time via PayPal Sandbox.");
  }

  /**
   * Executes payment capture using the configured PaymentAdapter (PayPal Sandbox or Stub).
   */
  @Transactional
  public DemoCaptureResponse capture(UUID intentId) {
    PurchaseIntent intent = getIntent(intentId);
    if (intent.getStatus() != IntentStatus.SPEND_RESERVED
        && intent.getStatus() != IntentStatus.ORDER_CREATED
        && intent.getStatus() != IntentStatus.AWAITING_PAYPAL_SETTLEMENT
        && intent.getStatus() != IntentStatus.APPROVED) {
      throw new ConflictException("Intent is not reserved for capture: " + intent.getStatus());
    }

    if (payPalAdapter.isPresent()) {
      Mandate mandate = mandates.findById(intent.getMandateId()).orElse(null);
      CheckoutSecurityValidator.verifyCheckoutPayloadIntegrity(intent, mandate);
      String vaultToken = mandate != null ? mandate.getPaypalVaultToken() : null;
      PayPalExecutionOutcome outcome = intent.getProviderOrderId() != null
          ? payPalAdapter.get().syncAndCaptureOrder(intent.getProviderOrderId(), intent.getIdempotencyKey(), null)
          : payPalAdapter.get().executeAllowCapture(intent, vaultToken);
      return applyPayPalOutcome(intent, outcome, "capture");
    }

    PaymentAdapter adapter = paymentAdapter.orElseThrow(() ->
        new ConflictException("No payment adapter available for mode: " + paymentsMode));

    intent.setStatus(IntentStatus.ORDER_CREATE_PENDING);
    PaymentResult result = adapter.capture(intent);
    attempts.save(new PaymentAttempt(intent.getId(), adapter.name(),
        result.providerReference(), result.simulated(), IntentStatus.CAPTURED));

    if (result.status() == PaymentResult.Status.CAPTURED) {
      intent.setStatus(IntentStatus.CAPTURED);
      intent.setProviderOrderId(result.providerReference());
      reservations.findByIntentId(intent.getId()).ifPresent(r ->
          r.setStatus(SpendReservation.ReservationStatus.SETTLED));
    } else if (result.status() == PaymentResult.Status.FAILED) {
      intent.setStatus(IntentStatus.FAILED);
      intent.setFailureReason(result.message());
      reservations.findByIntentId(intent.getId()).ifPresent(r ->
          r.setStatus(SpendReservation.ReservationStatus.RELEASED));
    } else {
      intent.setStatus(IntentStatus.RECONCILIATION_REQUIRED);
      intent.setFailureReason(result.message());
    }
    audit.record("payment." + intent.getStatus().name().toLowerCase(), "purchase_intent",
        intentId.toString(), intentId.toString(), null, POLICY_VERSION,
        adapter.name() + " capture: " + result.message());
    return new DemoCaptureResponse(intent.getId(), intent.getStatus(), result.simulated(),
        result.providerReference(), result.message());
  }

  @Transactional(readOnly = true)
  public PurchaseIntentResponse get(UUID intentId) {
    return PurchaseIntentResponse.of(getIntent(intentId));
  }

  @Transactional(readOnly = true)
  public List<PurchaseIntentResponse> listForWatch(UUID watchId) {
    return intents.findByWatchIdOrderByCreatedAtDesc(watchId).stream()
        .map(PurchaseIntentResponse::of).toList();
  }

  @Transactional(readOnly = true)
  public List<PurchaseIntentResponse> listAll() {
    return intents.findAllByOrderByCreatedAtDesc().stream()
        .map(PurchaseIntentResponse::of).toList();
  }

  @Transactional(readOnly = true)
  public List<PurchaseActivityDto> getActivityFeed() {
    List<PurchaseIntent> allIntents = intents.findAllByOrderByCreatedAtDesc();
    UserProfile profile = null;
    if (userProfileRepository != null) {
      profile = userProfileRepository.findFirstByLoggedInTrueOrderByUpdatedAtDesc()
          .orElseGet(() -> userProfileRepository.findFirstByOrderByCreatedAtAsc().orElse(null));
    }
    List<AuditEvent> recentAudits = auditEventRepository != null
        ? auditEventRepository.findTop50ByOrderByOccurredAtDesc()
        : List.of();
    BigDecimal currentWalletBalance = profile != null ? profile.getSandboxWalletBalance() : null;
    String profileEmail = profile != null ? profile.getPaypalEmail() : null;
    String profilePayerId = profile != null ? profile.getPaypalPayerId() : null;

    return allIntents.stream().map(intent -> {
      Watch watch = watches.findById(intent.getWatchId()).orElse(null);
      Mandate mandate = mandates.findById(intent.getMandateId()).orElse(null);
      PaymentAttempt latestAttempt = attempts.findByIntentIdOrderByCreatedAtDesc(intent.getId())
          .stream().findFirst().orElse(null);
      GuardianDecisionRecord decisionRec = decisions.findByIntentId(intent.getId()).orElse(null);
      OfferSnapshot snap = intent.getOfferSnapshot();

      String productName = watch != null && watch.getProductName() != null
          ? watch.getProductName()
          : (snap != null && snap.getAiForensicsSummary() != null
              ? snap.getCanonicalSku()
              : intent.getCanonicalSku());
      String merchantDomain = intent.getMerchantId();
      String merchantName = merchantDomain;
      String payerEmail = mandate != null && mandate.getPaypalPayerEmail() != null
          ? mandate.getPaypalPayerEmail() : profileEmail;
      String payerId = latestAttempt != null && latestAttempt.getPaypalPayerId() != null
          ? latestAttempt.getPaypalPayerId()
          : (mandate != null && mandate.getPaypalPayerId() != null ? mandate.getPaypalPayerId() : profilePayerId);
      BigDecimal walletBal = (mandate != null && mandate.getSandboxBalance() != null)
          ? mandate.getSandboxBalance() : currentWalletBalance;
      String auditSummary = recentAudits.stream()
          .filter(a -> intent.getId().toString().equals(a.getEntityId()))
          .map(AuditEvent::getSummary)
          .findFirst()
          .orElse(intent.getFailureReason());

      return new PurchaseActivityDto(
          intent.getId(),
          intent.getWatchId(),
          intent.getMandateId(),
          intent.getUpdatedAt() != null ? intent.getUpdatedAt() : intent.getCreatedAt(),
          productName,
          merchantName,
          merchantDomain,
          snap != null ? snap.getImageUrl() : null,
          snap != null ? snap.getProductUrl() : null,
          intent.getPurchaseMode(),
          intent.getStatus(),
          formatPaymentStatusLabel(intent.getStatus()),
          intent.getProviderOrderId(),
          intent.getPaypalCaptureId(),
          intent.getPaypalAuthorizationId(),
          intent.getApprovalUrl(),
          payerEmail,
          payerId,
          intent.getMaxTotal(),
          intent.getCurrency(),
          walletBal,
          auditSummary,
          decisionRec != null ? decisionRec.getReasonCodes() : null);
    }).toList();
  }

  private static String formatPaymentStatusLabel(IntentStatus status) {
    if (status == null) return "UNKNOWN";
    return switch (status) {
      case CAPTURED, SUCCEEDED -> "COMPLETED (VERIFIED IN PAYPAL SANDBOX)";
      case AWAITING_PAYPAL_SETTLEMENT, ORDER_CREATED, APPROVED -> "AWAITING PAYPAL SANDBOX PAYMENT";
      case HELD_FOR_APPROVAL, AWAITING_APPROVAL -> "HELD FOR HUMAN APPROVAL";
      case BLOCKED, REJECTED_BY_USER -> "BLOCKED BY GUARDIAN / USER";
      case FAILED, RECONCILIATION_REQUIRED -> "PAYMENT FAILED / NEEDS SYNC";
      default -> status.name();
    };
  }

  @Transactional(readOnly = true)
  public List<HuntedOfferDto> listAllOffers() {
    return huntedOffers.map(repo -> repo.findAllByOrderByCreatedAtDesc().stream()
        .map(r -> HuntedOfferDto.of(r, objectMapper)).toList())
        .orElse(List.of());
  }

  @Transactional(readOnly = true)
  public GuardianDecisionResponse decision(UUID intentId) {
    getIntent(intentId);
    return GuardianDecisionResponse.of(latestDecision(intentId));
  }

  // ------------------------------------------------------------------ internals

  private GuardianDecisionRecord evaluateAndPersist(PurchaseIntent intent, Mandate mandate,
                                                    Watch watch, Instant now) {
    OfferSnapshot snap = intent.getOfferSnapshot();
    Policy policy = new Policy(
        mandate.getId().toString(), mandate.getVersion(), mandate.getCurrency(),
        mandate.getMaxUnitPrice(), mandate.getMaxTotal(), mandate.getMaxQuantity(),
        mandate.getAllowedMerchantIds(), mandate.getMaxPurchasesPerDay(),
        mandate.getOfferMaxAgeSeconds(),
        Policy.NewMerchantBehavior.valueOf(mandate.getNewMerchantBehavior().name()),
        mandate.getRequireHumanApprovalAbove(), mandate.getMaxWeeklySpend(),
        watch.getTargetPrice(), POLICY_VERSION,
        mandate.getStatus() == MandateStatus.ACTIVE
            ? Policy.MandateStatus.ACTIVE : Policy.MandateStatus.REVOKED,
        mandate.getMinSellerRating());

    Offer offer = new Offer(
        snap.getMerchantId(), snap.getCanonicalSku(), snap.getVariant(),
        snap.getQuantity(), snap.getCurrency(), snap.getUnitPrice(), snap.getTotalPrice(),
        snap.getObservedAt(), snap.getSource(), snap.getEvidenceHash(),
        snap.getBaselinePrice(), snap.getAiForensicsSummary(),
        snap.getAiExactSkuMatch() == null || snap.getAiExactSkuMatch(),
        Boolean.TRUE.equals(snap.getAiPromptInjectionDetected()),
        Boolean.TRUE.equals(snap.getAiSubscriptionTrapDetected()),
        snap.getAiMatchConfidence(),
        snap.getSellerRating(), snap.getSellerReviewCount(), snap.getVerifiedSellerHistory(),
        snap.getNegativeRedFlagCount(), snap.getSellerRedFlagSummary(), snap.getEstimatedDelivery());

    EvaluationContext context = new EvaluationContext(
        purchasesToday(intent.getWatchId(), now), remainingBudget(mandate, now), now);

    GuardianDecisionResult result = evaluator.evaluate(policy, offer, context);
    GuardianDecisionRecord record = new GuardianDecisionRecord(
        intent.getId(), mandate.getId(), mandate.getVersion(), result.decision(),
        result.reasonCodes().stream().map(ReasonCode::name).collect(Collectors.joining(",")),
        result.policyVersion(), snap.getEvidenceHash(), result.evaluatedAt(), result.expiresAt());
    try {
      record.setRuleChecksJson(objectMapper.writeValueAsString(result.ruleChecks()));
    } catch (Exception ignored) {
      // non-fatal
    }
    decisions.save(record);

    intent.setGuardianDecisionId(record.getId());
    intent.setPolicyVersion(result.policyVersion());
    intent.setEvidenceHash(snap.getEvidenceHash());
    intent.setStatus(IntentStatus.GUARDIAN_EVALUATED);
    return record;
  }

  private void transitionAfterDecision(PurchaseIntent intent, GuardianDecisionRecord decision,
                                       boolean fromApproval) {
    switch (decision.getDecision()) {
      case ALLOW -> {
        SpendReservation reservation = new SpendReservation(
            intent.getId(), intent.getWatchId(), intent.getMaxTotal());
        reservations.save(reservation);
        intent.setStatus(IntentStatus.SPEND_RESERVED);
      }
      case HOLD -> intent.setStatus(IntentStatus.AWAITING_APPROVAL);
      case BLOCK -> {
        intent.setStatus(IntentStatus.BLOCKED);
        intent.setFailureReason("Guardian BLOCK: " + decision.getReasonCodes());
      }
    }
    if (fromApproval && decision.getDecision() == Decision.BLOCK) {
      log.info("Approval revalidation blocked intent {}", intent.getId());
    }
  }

  private int purchasesToday(UUID watchId, Instant now) {
    Instant startOfDay = now.truncatedTo(ChronoUnit.DAYS);
    return (int) intents.countByWatchIdAndCreatedAtAfterAndStatusNot(
        watchId, startOfDay, IntentStatus.BLOCKED);
  }

  private BigDecimal remainingBudget(Mandate mandate, Instant now) {
    if (mandate.getMaxWeeklySpend() == null) {
      return null;
    }
    Instant weekStart = now.atZone(ZoneOffset.UTC).toLocalDate()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .atStartOfDay(ZoneOffset.UTC).toInstant();
    BigDecimal spent = reservations.sumActiveSince(mandate.getWatchId(), weekStart);
    return mandate.getMaxWeeklySpend().subtract(spent == null ? BigDecimal.ZERO : spent);
  }

  private PurchaseIntent getIntent(UUID id) {
    return intents.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Purchase intent not found: " + id));
  }

  private GuardianDecisionRecord latestDecision(UUID intentId) {
    return decisions.findByIntentId(intentId)
        .orElseThrow(() -> new ResourceNotFoundException(
            "No Guardian decision for intent: " + intentId));
  }

  public static String evidenceHash(String merchantId, String sku, String variant, int quantity,
                             String currency, BigDecimal unitPrice, BigDecimal totalPrice,
                             Instant observedAt) {
    try {
      String input = String.join("|", merchantId, String.valueOf(sku), String.valueOf(variant),
          String.valueOf(quantity), currency, unitPrice.toPlainString(),
          totalPrice.toPlainString(), observedAt.toString());
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : hash) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
