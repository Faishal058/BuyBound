package com.buybound.purchase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.buybound.ai.service.AiShoppingAgentService;
import com.buybound.audit.AuditService;
import com.buybound.catalog.service.Channel3SearchService;
import com.buybound.common.exception.ConflictException;
import com.buybound.guardian.domain.Decision;
import com.buybound.guardian.domain.EvaluationContext;
import com.buybound.guardian.domain.Offer;
import com.buybound.guardian.domain.Policy;
import com.buybound.guardian.dto.RuntimeGuardrailSettings;
import com.buybound.guardian.service.GuardianEvaluator;
import com.buybound.guardian.service.RuntimeGuardrailSettingsService;
import com.buybound.payment.PayPalPaymentAdapter;
import com.buybound.payment.PayPalPaymentAdapter.PayPalOrderCreatedResult;
import com.buybound.purchase.domain.IntentStatus;
import com.buybound.purchase.domain.OfferSnapshot;
import com.buybound.purchase.domain.PurchaseIntent;
import com.buybound.purchase.domain.SpendReservation;
import com.buybound.purchase.dto.InstantBuyRequest;
import com.buybound.purchase.dto.InstantBuyResponse;
import com.buybound.purchase.repository.GuardianDecisionRepository;
import com.buybound.purchase.repository.HuntedOfferRepository;
import com.buybound.purchase.repository.PaymentAttemptRepository;
import com.buybound.purchase.repository.PurchaseIntentRepository;
import com.buybound.purchase.repository.SpendReservationRepository;
import com.buybound.purchase.service.CheckoutSecurityValidator;
import com.buybound.purchase.service.HuntOrchestratorService;
import com.buybound.watch.domain.Mandate;
import com.buybound.watch.domain.MandateStatus;
import com.buybound.watch.domain.Watch;
import com.buybound.watch.repository.MandateRepository;
import com.buybound.watch.repository.WatchRepository;
import com.buybound.watch.service.MandateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class DemoModeAndDirectBuyTest {

  @AfterEach
  void resetRuntimeDefaults() {
    RuntimeGuardrailSettingsService.resetStaticDefaults();
  }

  @Test
  void runtimeSettingsDefaultToStrictCisoAndAutoApproveDemoOverridesHold() {
    RuntimeGuardrailSettings defaults = RuntimeGuardrailSettings.defaults();
    assertThat(defaults.demoBypassEnabled()).isFalse();
    assertThat(defaults.strictSellerVerification()).isTrue();
    assertThat(defaults.guardianAutoApproveDemoMode()).isFalse();
    assertThat(defaults.rateLimitDisabled()).isFalse();
    assertThat(defaults.ignoreOvernightWindow()).isFalse();

    RuntimeGuardrailSettingsService svc = new RuntimeGuardrailSettingsService(null);
    svc.updateSettings(new RuntimeGuardrailSettings(false, false, true, true, true));

    @SuppressWarnings("unchecked")
    ObjectProvider<RuntimeGuardrailSettingsService> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(svc);

    GuardianEvaluator evaluator = new GuardianEvaluator(provider);
    Policy strictPolicy = new Policy(
        "m-1", 1, "USD", new BigDecimal("250.00"), new BigDecimal("250.00"), 1,
        List.of("nike.com"), 3, 3600L, Policy.NewMerchantBehavior.HOLD,
        new BigDecimal("150.00"), new BigDecimal("500.00"), new BigDecimal("220.00"),
        "1.0", Policy.MandateStatus.ACTIVE, 4.5);
    Instant now = Instant.now();
    Offer newMerchantOffer = new Offer(
        "unfamiliarshop.com", "SKU-1", "Red", 1, "USD",
        new BigDecimal("200.00"), new BigDecimal("200.00"), now, "channel3", "hash-1",
        new BigDecimal("300.00"), "Valid Product", true, false, false, new BigDecimal("95.00"),
        4.2, 50, false, 1, "Minor delay", "2 Days");

    var result = evaluator.evaluate(strictPolicy, newMerchantOffer, new EvaluationContext(0, new BigDecimal("400.00"), now));
    assertThat(result.decision()).isEqualTo(Decision.ALLOW);
  }

  @Test
  void checkoutSecurityValidatorHonorsDemoBypassWhileStillBlockingCapturedReplay() {
    RuntimeGuardrailSettingsService svc = new RuntimeGuardrailSettingsService(null);
    svc.updateSettings(new RuntimeGuardrailSettings(true, false, true, true, true));

    Watch watch = new Watch("Test Item", "p-1", "SKU-1", "Std", "bestbuy.com", "USD", new BigDecimal("100.00"));
    Mandate pausedMandate = new Mandate(
        watch.getId(), 1, "USD", new BigDecimal("50.00"), new BigDecimal("50.00"), 1,
        List.of("bestbuy.com"), 1, 300L, Mandate.NewMerchantBehavior.BLOCK, null, new BigDecimal("50.00"));
    pausedMandate.setStatus(MandateStatus.PAUSED);

    PurchaseIntent intent = new PurchaseIntent(
        watch.getId(), pausedMandate.getId(), 1, "otherstore.com", "SKU-1", "Std", 1,
        "USD", new BigDecimal("299.00"), "idem-demo-1");
    OfferSnapshot snap = new OfferSnapshot(
        "otherstore.com", "SKU-1", "Std", 1, "USD",
        new BigDecimal("299.00"), new BigDecimal("299.00"), Instant.now(), "test", "bad-hash");
    snap.setProductUrl("http://localhost:8080/test-item");
    intent.setOfferSnapshot(snap);
    intent.setStatus(IntentStatus.ORDER_CREATED);

    // Passes when demoBypassEnabled is true even with mismatched amount/hash/url
    CheckoutSecurityValidator.verifyCheckoutPayloadIntegrity(intent, pausedMandate);

    // Still blocks replay of CAPTURED intent!
    intent.setStatus(IntentStatus.CAPTURED);
    assertThatThrownBy(() -> CheckoutSecurityValidator.verifyCheckoutPayloadIntegrity(intent, pausedMandate))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void directInstantBuyBypassesAgentGuardrailsAndCreatesPayPalOrder() {
    WatchRepository watches = mock(WatchRepository.class);
    MandateRepository mandates = mock(MandateRepository.class);
    PurchaseIntentRepository intents = mock(PurchaseIntentRepository.class);
    GuardianDecisionRepository decisions = mock(GuardianDecisionRepository.class);
    SpendReservationRepository reservations = mock(SpendReservationRepository.class);
    PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
    HuntedOfferRepository huntedOffers = mock(HuntedOfferRepository.class);
    Channel3SearchService channel3 = mock(Channel3SearchService.class);
    AiShoppingAgentService aiService = mock(AiShoppingAgentService.class);
    AuditService audit = mock(AuditService.class);
    PayPalPaymentAdapter payPalAdapter = mock(PayPalPaymentAdapter.class);

    when(watches.findAll()).thenReturn(List.of());
    when(watches.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(mandates.findByStatusOrderByCreatedAtDesc(MandateStatus.ACTIVE)).thenReturn(List.of());
    when(mandates.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
    when(mandates.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(intents.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(decisions.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(attempts.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(huntedOffers.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(payPalAdapter.createInteractiveOrder(any(), any())).thenReturn(
        new PayPalOrderCreatedResult(
            "PP-DIRECT-99", "PAYER_ACTION_REQUIRED",
            "https://www.sandbox.paypal.com/checkoutnow?token=PP-DIRECT-99",
            "Ready for approval"));

    HuntOrchestratorService orchestrator = new HuntOrchestratorService(
        watches, mandates, intents, decisions, reservations, attempts,
        huntedOffers, channel3, aiService, new GuardianEvaluator(),
        audit, Optional.of(payPalAdapter), new ObjectMapper());

    InstantBuyRequest request = new InstantBuyRequest(
        null, null, null, null, null, true,
        "Dyson Airwrap Multi-Styler", new BigDecimal("549.99"), "USD",
        "Ulta Beauty", "ulta.com", "https://ulta.com/airwrap", null, "DYSON-AW-1");

    InstantBuyResponse response = orchestrator.instantBuy(request);

    assertThat(response.intent().purchaseMode()).isEqualTo("DIRECT_HUMAN");
    assertThat(response.intent().status()).isEqualTo(IntentStatus.ORDER_CREATED);
    assertThat(response.intent().providerOrderId()).isEqualTo("PP-DIRECT-99");
    assertThat(response.paypalExecution().approvalUrl())
        .isEqualTo("https://www.sandbox.paypal.com/checkoutnow?token=PP-DIRECT-99");
  }

  @Test
  void resetReservationsReleasesAllActiveSpendLocks() {
    MandateRepository mandates = mock(MandateRepository.class);
    WatchRepository watches = mock(WatchRepository.class);
    PurchaseIntentRepository intents = mock(PurchaseIntentRepository.class);
    HuntedOfferRepository offers = mock(HuntedOfferRepository.class);
    SpendReservationRepository reservations = mock(SpendReservationRepository.class);
    AuditService audit = mock(AuditService.class);

    SpendReservation activeRes = new SpendReservation(
        java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), new BigDecimal("204.00"));
    when(reservations.findAll()).thenReturn(List.of(activeRes));
    when(reservations.save(any())).thenAnswer(inv -> inv.getArgument(0));

    MandateService service = new MandateService(
        mandates, watches, intents, offers, reservations, audit, Optional.empty());

    Map<String, Object> result = service.resetReservations();

    assertThat(result.get("status")).isEqualTo("RESET");
    assertThat(result.get("releasedCount")).isEqualTo(1);
    assertThat(activeRes.getStatus()).isEqualTo(SpendReservation.ReservationStatus.RELEASED);
  }
}

