package com.buybound.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.buybound.ai.service.AiShoppingAgentService;
import com.buybound.audit.AuditService;
import com.buybound.common.exception.GlobalExceptionHandler;
import com.buybound.guardian.service.GuardianEvaluator;
import com.buybound.payment.PayPalCheckoutService.CapturePayPalOrderResponse;
import com.buybound.payment.PayPalCheckoutService.CreatePayPalOrderRequest;
import com.buybound.payment.PayPalCheckoutService.CreatePayPalOrderResponse;
import com.buybound.payment.PayPalCheckoutService.PayPalConfigResponse;
import com.buybound.payment.PayPalPaymentAdapter.PayPalCaptureOutcome;
import com.buybound.purchase.domain.IntentStatus;
import com.buybound.purchase.domain.PurchaseIntent;
import com.buybound.purchase.dto.PurchaseIntentResponse;
import com.buybound.purchase.repository.GuardianDecisionRepository;
import com.buybound.purchase.repository.HuntedOfferRepository;
import com.buybound.purchase.repository.PaymentAttemptRepository;
import com.buybound.purchase.repository.PurchaseIntentRepository;
import com.buybound.purchase.repository.SpendReservationRepository;
import com.buybound.user.service.UserProfileService;
import com.buybound.watch.domain.Mandate;
import com.buybound.watch.domain.MandateStatus;
import com.buybound.watch.dto.ActiveMandateTelemetryResponse;
import com.buybound.watch.repository.MandateRepository;
import com.buybound.watch.repository.WatchRepository;
import com.buybound.watch.service.MandateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@WebMvcTest(PayPalCheckoutController.class)
@Import(GlobalExceptionHandler.class)
class PayPalCheckoutControllerTest {
  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private PayPalCheckoutService service;

  @Test
  void configReturnsPublicClientIdAndSandboxModeWithoutSecrets() throws Exception {
    when(service.getConfig()).thenReturn(new PayPalConfigResponse(
        "test-paypal-client-id", "sandbox", "USD", true,
        "https://www.sandbox.paypal.com/signin/authorize?client_id=test-paypal-client-id"));

    mockMvc.perform(get("/api/paypal/config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clientId").value("test-paypal-client-id"))
        .andExpect(jsonPath("$.mode").value("sandbox"))
        .andExpect(jsonPath("$.configured").value(true));
  }

  @Test
  void createOrderReturnsPayPalApproveUrlWhenGuardianAllows() throws Exception {
    UUID intentId = UUID.randomUUID();
    PurchaseIntentResponse intentRes = new PurchaseIntentResponse(
        intentId, UUID.randomUUID(), UUID.randomUUID(), 1, IntentStatus.ORDER_CREATED,
        "target.com", "airpods-2", "Apple", 1, "USD", new BigDecimal("189.99"),
        UUID.randomUUID(), "1.0", "hash", null, "idem-1", "PAYPAL-ORDER-123",
        null, null, "https://www.sandbox.paypal.com/checkoutnow?token=PAYPAL-ORDER-123",
        null, Instant.now(), Instant.now());

    when(service.createOrder(any(CreatePayPalOrderRequest.class))).thenReturn(
        new CreatePayPalOrderResponse(
            intentId, "PAYPAL-ORDER-123",
            "https://www.sandbox.paypal.com/checkoutnow?token=PAYPAL-ORDER-123",
            null, intentRes, null, false, "Ready for PayPal approval."));

    mockMvc.perform(post("/api/paypal/create-order")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"productTitle":"Apple AirPods Pro 2","merchantId":"target.com","price":189.99,"currency":"USD"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paypalOrderId").value("PAYPAL-ORDER-123"))
        .andExpect(jsonPath("$.approveUrl").value("https://www.sandbox.paypal.com/checkoutnow?token=PAYPAL-ORDER-123"))
        .andExpect(jsonPath("$.blocked").value(false));
  }

  @Test
  void captureOrderForwardsMockResponseHeaderAndReturnsLivePayPalCaptureReceipt() throws Exception {
    UUID intentId = UUID.randomUUID();
    PurchaseIntentResponse intentRes = new PurchaseIntentResponse(
        intentId, UUID.randomUUID(), UUID.randomUUID(), 1, IntentStatus.CAPTURED,
        "target.com", "airpods-2", "Apple", 1, "USD", new BigDecimal("189.99"),
        UUID.randomUUID(), "1.0", "hash", null, "idem-1", "PAYPAL-ORDER-123",
        null, "CAP-LIVE-999", null, null, Instant.now(), Instant.now());

    when(service.captureOrder(eq("PAYPAL-ORDER-123"), eq("INSTRUMENT_DECLINED"))).thenReturn(
        new CapturePayPalOrderResponse(
            intentRes, "CAP-LIVE-999", "PAYPAL-ORDER-123",
            "realbuyer@example.com", "Real Buyer",
            null, new BigDecimal("189.99"),
            "Live PayPal Sandbox capture completed (CAP-LIVE-999)!"));

    mockMvc.perform(post("/api/paypal/capture-order/PAYPAL-ORDER-123")
            .header("PayPal-Mock-Response", "INSTRUMENT_DECLINED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.captureId").value("CAP-LIVE-999"))
        .andExpect(jsonPath("$.payerEmail").value("realbuyer@example.com"));
  }

  @Test
  void oauthCallbackLinksVerifiedSandboxPayerIdentity() throws Exception {
    when(service.oauthCallback("auth-code-123")).thenReturn(
        new ActiveMandateTelemetryResponse(
            UUID.randomUUID(), UUID.randomUUID(), MandateStatus.ACTIVE, "USD",
            new BigDecimal("500.00"), BigDecimal.ZERO, new BigDecimal("500.00"),
            3, 3, true, null, "realbuyer@example.com",
            BigDecimal.ZERO, 0L, 0L, 0L, BigDecimal.ZERO,
            new BigDecimal("260.00"), List.of("bestbuy.com", "target.com"),
            null, "Real Sandbox Buyer", "PAYERID12345"));

    mockMvc.perform(post("/api/paypal/oauth-callback")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"code":"auth-code-123"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paypalConnected").value(true))
        .andExpect(jsonPath("$.paypalPayerId").value("PAYERID12345"))
        .andExpect(jsonPath("$.paypalPayerEmail").value("realbuyer@example.com"));

    when(service.startPayPalLogin()).thenReturn(java.util.Map.of(
        "setupTokenId", "SETUP-123",
        "approvalUrl", "https://www.sandbox.paypal.com/agreements/approve?approval_session_id=SETUP-123",
        "status", "PAYER_ACTION_REQUIRED"));
    mockMvc.perform(post("/api/paypal/start-login"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.setupTokenId").value("SETUP-123"))
        .andExpect(jsonPath("$.approvalUrl").value("https://www.sandbox.paypal.com/agreements/approve?approval_session_id=SETUP-123"));

    when(service.vaultLoginCallback("SETUP-123")).thenReturn(
        new PayPalCheckoutService.OAuthSessionResult(
            new ActiveMandateTelemetryResponse(
                UUID.randomUUID(), UUID.randomUUID(), MandateStatus.ACTIVE, "USD",
                new BigDecimal("500.00"), BigDecimal.ZERO, new BigDecimal("500.00"),
                0, 0, true, "vlt_live_1234", "realbuyer@example.com",
                BigDecimal.ZERO, 0L, 0L, 0L, BigDecimal.ZERO,
                new BigDecimal("260.00"), List.of("bestbuy.com"),
                null, "Real Sandbox Buyer", "PAYERID12345"),
            UUID.randomUUID()));
    mockMvc.perform(post("/api/paypal/vault-login-callback")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"setupTokenId":"SETUP-123"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paypalConnected").value(true))
        .andExpect(jsonPath("$.paypalPayerId").value("PAYERID12345"));
  }

  @Test
  void captureOrderRequiresLivePayPalApprovalNeverFabricatesCapturesAndForwardsMockRejection() throws Exception {
    PayPalPaymentAdapter mockAdapter = mock(PayPalPaymentAdapter.class);
    PurchaseIntentRepository intents = mock(PurchaseIntentRepository.class);
    GuardianDecisionRepository decisions = mock(GuardianDecisionRepository.class);
    SpendReservationRepository reservations = mock(SpendReservationRepository.class);
    PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
    HuntedOfferRepository huntedOffers = mock(HuntedOfferRepository.class);
    WatchRepository watches = mock(WatchRepository.class);
    MandateRepository mandates = mock(MandateRepository.class);
    AuditService audit = mock(AuditService.class);
    AiShoppingAgentService aiService = mock(AiShoppingAgentService.class);
    UserProfileService userProfileService = mock(UserProfileService.class);

    Mandate mandate = new Mandate(
        UUID.randomUUID(), 1, "USD",
        new BigDecimal("500.00"), new BigDecimal("500.00"), 1,
        List.of("bestbuy.com"), 3, 3600L,
        Mandate.NewMerchantBehavior.HOLD, null, new BigDecimal("500.00"));

    Instant observedAt = Instant.now();
    String hash = com.buybound.purchase.service.PurchaseService.evidenceHash(
        "bestbuy.com", "airpods-pro-2", "Apple", 1, "USD",
        new BigDecimal("179.00"), new BigDecimal("179.00"), observedAt);
    PurchaseIntent intent = new PurchaseIntent(
        mandate.getWatchId(), mandate.getId(), 1,
        "bestbuy.com", "airpods-pro-2", "Apple", 1, "USD",
        new BigDecimal("179.00"), "pp-live-test-1");
    intent.setProviderOrderId("8L8915293N1545906");
    intent.setStatus(IntentStatus.ORDER_CREATED);
    intent.setEvidenceHash(hash);
    intent.setOfferSnapshot(new com.buybound.purchase.domain.OfferSnapshot(
        "bestbuy.com", "airpods-pro-2", "Apple", 1, "USD",
        new BigDecimal("179.00"), new BigDecimal("179.00"), observedAt, "channel3", hash));

    when(intents.findFirstByProviderOrderIdOrderByCreatedAtDesc("8L8915293N1545906"))
        .thenReturn(Optional.of(intent));
    when(intents.save(any(PurchaseIntent.class))).thenAnswer(inv -> inv.getArgument(0));
    when(mandates.findById(mandate.getId())).thenReturn(Optional.of(mandate));
    when(mandates.findByStatusOrderByCreatedAtDesc(MandateStatus.ACTIVE)).thenReturn(List.of(mandate));
    when(mandates.save(any(Mandate.class))).thenAnswer(inv -> inv.getArgument(0));
    when(reservations.findByIntentId(intent.getId())).thenReturn(Optional.empty());
    when(reservations.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(attempts.save(any())).thenAnswer(inv -> inv.getArgument(0));

    // 1. Unapproved or negative-tested order (PayPal-Mock-Response: INSTRUMENT_DECLINED) must reject cleanly
    when(mockAdapter.captureApprovedOrder("8L8915293N1545906", "pp-live-test-1", "INSTRUMENT_DECLINED"))
        .thenReturn(new PayPalCaptureOutcome(
            "8L8915293N1545906", null, null, null, null, null, null,
            PaymentResult.Status.FAILED,
            "PayPal Sandbox /v2/checkout/orders/8L8915293N1545906/capture returned HTTP 422 · issue=INSTRUMENT_DECLINED · debug_id=f1234abcd: The instrument presented was declined."));

    MandateService realMandateService = new MandateService(
        mandates, watches, intents, huntedOffers, reservations, audit, Optional.of(mockAdapter));
    PayPalCheckoutService realCheckoutService = new PayPalCheckoutService(
        Optional.of(mockAdapter), intents, decisions, reservations, attempts,
        huntedOffers, watches, mandates, realMandateService, aiService,
        new GuardianEvaluator(), audit, new ObjectMapper(), Optional.of(userProfileService),
        "sandbox", "test-client-id");

    MockMvc standaloneMvc = MockMvcBuilders
        .standaloneSetup(new PayPalCheckoutController(realCheckoutService))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    standaloneMvc.perform(post("/api/paypal/capture-order/8L8915293N1545906")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"mockResponseCode\":\"INSTRUMENT_DECLINED\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("INSTRUMENT_DECLINED")))
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("debug_id=f1234abcd")));
    assertThat(intent.getStatus()).isEqualTo(IntentStatus.ORDER_CREATED);

    // 2. Approved order captures via live PayPal Sandbox API and stores real payer identity
    when(mockAdapter.captureApprovedOrder("8L8915293N1545906", "pp-live-test-1", null))
        .thenReturn(new PayPalCaptureOutcome(
            "8L8915293N1545906", null, "CAP-LIVE-98765", null,
            "livebuyer@example.com", "Live Sandbox Buyer", "PAYER-LIVE-77",
            PaymentResult.Status.CAPTURED,
            "PayPal Sandbox payment captured (Capture ID: CAP-LIVE-98765)."));

    standaloneMvc.perform(post("/api/paypal/capture-order/8L8915293N1545906"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paypalOrderId").value("8L8915293N1545906"))
        .andExpect(jsonPath("$.captureId").value("CAP-LIVE-98765"))
        .andExpect(jsonPath("$.intent.status").value("CAPTURED"))
        .andExpect(jsonPath("$.payerName").value("Live Sandbox Buyer"))
        .andExpect(jsonPath("$.payerEmail").value("livebuyer@example.com"))
        .andExpect(jsonPath("$.deductedAmount").value(179.00));

    assertThat(intent.getStatus()).isEqualTo(IntentStatus.CAPTURED);
    assertThat(intent.getPaypalCaptureId()).isEqualTo("CAP-LIVE-98765");
    assertThat(mandate.getPaypalPayerId()).isEqualTo("PAYER-LIVE-77");
    assertThat(mandate.getPaypalPayerEmail()).isEqualTo("livebuyer@example.com");
    assertThat(PayPalPaymentAdapter.normalizeMockResponseHeader("INSTRUMENT_DECLINED"))
        .isEqualTo("{\"mock_application_codes\":\"INSTRUMENT_DECLINED\"}");
  }
}
