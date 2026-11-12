package com.buybound.purchase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.buybound.ai.service.AiShoppingAgentService;
import com.buybound.audit.AuditService;
import com.buybound.catalog.client.Channel3Client;
import com.buybound.catalog.service.Channel3SearchService;
import com.buybound.common.exception.ExternalServiceException;
import com.buybound.guardian.domain.Decision;
import com.buybound.guardian.service.GuardianEvaluator;
import com.buybound.payment.PayPalPaymentAdapter;
import com.buybound.payment.PayPalPaymentAdapter.PayPalExecutionOutcome;
import com.buybound.payment.PaymentResult;
import com.buybound.purchase.domain.HuntedOfferRecord;
import com.buybound.purchase.domain.IntentStatus;
import com.buybound.purchase.dto.HuntWatchResponse;
import com.buybound.purchase.dto.HuntedOfferDto;
import com.buybound.purchase.repository.GuardianDecisionRepository;
import com.buybound.purchase.repository.HuntedOfferRepository;
import com.buybound.purchase.repository.PaymentAttemptRepository;
import com.buybound.purchase.repository.PurchaseIntentRepository;
import com.buybound.purchase.repository.SpendReservationRepository;
import com.buybound.purchase.service.HuntOrchestratorService;
import com.buybound.watch.domain.Mandate;
import com.buybound.watch.domain.MandateStatus;
import com.buybound.watch.domain.Watch;
import com.buybound.watch.repository.MandateRepository;
import com.buybound.watch.repository.WatchRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OvernightDropHuntTest {

  @Test
  void flagshipNikeAirJordanOvernightDropBlocksSketchyAndOverpricedAndCapturesNike204() {
    WatchRepository watches = mock(WatchRepository.class);
    MandateRepository mandates = mock(MandateRepository.class);
    PurchaseIntentRepository intents = mock(PurchaseIntentRepository.class);
    GuardianDecisionRepository decisions = mock(GuardianDecisionRepository.class);
    SpendReservationRepository reservations = mock(SpendReservationRepository.class);
    PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
    HuntedOfferRepository huntedOffers = mock(HuntedOfferRepository.class);
    AuditService audit = mock(AuditService.class);
    AiShoppingAgentService aiService = mock(AiShoppingAgentService.class);
    PayPalPaymentAdapter payPalAdapter = mock(PayPalPaymentAdapter.class);
    Channel3Client channel3Client = mock(Channel3Client.class);

    when(channel3Client.isConfigured()).thenReturn(false);
    Channel3SearchService searchService = new Channel3SearchService(channel3Client);

    Watch watch = new Watch(
        "Nike Air Jordan 1 Retro High OG (Chicago Lost & Found)",
        "snk-aj1-lost-found", "NIKE-AJ1-CHIC-OG", "Chicago Lost & Found",
        "nike.com", "USD", new BigDecimal("200.00"));
    watch.setBaselineMarketPrice(new BigDecimal("320.00"));

    Mandate mandate = new Mandate(
        watch.getId(), 1, "USD",
        new BigDecimal("210.00"), new BigDecimal("210.00"), 1,
        List.of("nike.com", "flightclub.com", "bestbuy.com", "target.com"),
        3, 3600L, Mandate.NewMerchantBehavior.HOLD, null, new BigDecimal("500.00"));
    mandate.setMinSellerRating(4.5);

    when(watches.findByIdForUpdate(watch.getId())).thenReturn(Optional.of(watch));
    when(watches.save(any(Watch.class))).thenAnswer(inv -> inv.getArgument(0));
    when(mandates.findByWatchIdAndStatus(watch.getId(), MandateStatus.ACTIVE)).thenReturn(Optional.of(mandate));
    when(mandates.findByStatusOrderByCreatedAtDesc(MandateStatus.ACTIVE)).thenReturn(List.of(mandate));
    when(mandates.save(any(Mandate.class))).thenAnswer(inv -> inv.getArgument(0));
    when(intents.countByWatchIdAndCreatedAtAfterAndStatusNot(any(), any(), any())).thenReturn(0L);
    when(intents.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(decisions.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(reservations.sumActiveSince(any(), any())).thenReturn(BigDecimal.ZERO);
    when(reservations.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(attempts.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(huntedOffers.save(any(HuntedOfferRecord.class))).thenAnswer(inv -> inv.getArgument(0));
    when(aiService.analyzeOffer(any(), any()))
        .thenThrow(new ExternalServiceException("Use deterministic fallback in test"));
    when(payPalAdapter.executeAllowCapture(any(), any()))
        .thenReturn(new PayPalExecutionOutcome(
            "PP-AJ1-ORDER-1", null, "CAP-LIVE-9901",
            null, "CAPTURE", PaymentResult.Status.CAPTURED,
            "Live PayPal Sandbox Vault capture completed (CAP-LIVE-9901)."));

    HuntOrchestratorService orchestrator = new HuntOrchestratorService(
        watches, mandates, intents, decisions, reservations, attempts,
        huntedOffers, searchService, aiService, new GuardianEvaluator(),
        audit, Optional.of(payPalAdapter), new ObjectMapper());

    HuntWatchResponse response = orchestrator.huntWatch(watch.getId());

    assertThat(response.offersScanned()).isEqualTo(3);
    assertThat(response.evaluatedOffers()).hasSize(3);

    HuntedOfferDto sketchy = response.evaluatedOffers().stream()
        .filter(o -> "hyped-kicks-liquidator.net".equals(o.merchantId()))
        .findFirst().orElseThrow();
    assertThat(sketchy.guardianDecision()).isEqualTo(Decision.BLOCK);
    assertThat(sketchy.reasonCodes()).contains("SELLER_NEGATIVE_REVIEW_RED_FLAG", "SELLER_RATING_TOO_LOW");
    assertThat(sketchy.sellerRating()).isEqualTo(2.8);
    assertThat(sketchy.negativeRedFlagCount()).isEqualTo(14);

    HuntedOfferDto overpriced = response.evaluatedOffers().stream()
        .filter(o -> "flightclub.com".equals(o.merchantId()))
        .findFirst().orElseThrow();
    assertThat(overpriced.guardianDecision()).isEqualTo(Decision.BLOCK);
    assertThat(overpriced.reasonCodes()).contains("ITEM_PRICE_CAP_EXCEEDED");

    HuntedOfferDto winner = response.evaluatedOffers().stream()
        .filter(o -> "nike.com".equals(o.merchantId()))
        .findFirst().orElseThrow();
    assertThat(winner.guardianDecision()).isEqualTo(Decision.ALLOW);
    assertThat(winner.reasonCodes()).contains("WITHIN_DROP_GUARDRAIL_BAND");
    assertThat(winner.sellerRating()).isEqualTo(4.9);
    assertThat(winner.sellerReviewCount()).isEqualTo(3420);
    assertThat(winner.estimatedDelivery()).isEqualTo("Arriving in 2 Days");

    assertThat(response.selectedIntent().status()).isEqualTo(IntentStatus.CAPTURED);
    assertThat(response.selectedIntent().merchantId()).isEqualTo("nike.com");
    assertThat(response.selectedIntent().maxTotal()).isEqualByComparingTo("204.00");
    assertThat(response.selectedIntent().paypalCaptureId()).isEqualTo("CAP-LIVE-9901");
  }
}

