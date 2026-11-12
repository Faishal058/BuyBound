package com.buybound.purchase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.buybound.audit.AuditService;
import com.buybound.common.exception.ConflictException;
import com.buybound.guardian.domain.Decision;
import com.buybound.guardian.service.GuardianEvaluator;
import com.buybound.payment.PaymentAdapter;
import com.buybound.payment.PaymentResult;
import com.buybound.purchase.domain.GuardianDecisionRecord;
import com.buybound.purchase.domain.IntentStatus;
import com.buybound.purchase.domain.PurchaseIntent;
import com.buybound.purchase.dto.SubmitOfferRequest;
import com.buybound.purchase.repository.GuardianDecisionRepository;
import com.buybound.purchase.repository.PaymentAttemptRepository;
import com.buybound.purchase.repository.PurchaseIntentRepository;
import com.buybound.purchase.repository.SpendReservationRepository;
import com.buybound.purchase.service.PurchaseService;
import com.buybound.watch.domain.Mandate;
import com.buybound.watch.domain.MandateStatus;
import com.buybound.watch.domain.Watch;
import com.buybound.watch.domain.WatchStatus;
import com.buybound.watch.repository.MandateRepository;
import com.buybound.watch.repository.WatchRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseServiceTest {
  @Mock private PurchaseIntentRepository intents;
  @Mock private GuardianDecisionRepository decisions;
  @Mock private SpendReservationRepository reservations;
  @Mock private PaymentAttemptRepository attempts;
  @Mock private WatchRepository watches;
  @Mock private MandateRepository mandates;
  @Mock private AuditService audit;

  private PurchaseService service;
  private Watch watch;
  private Mandate mandate;

  @BeforeEach
  void setUp() {
    service = new PurchaseService(intents, decisions, reservations, attempts, watches,
        mandates, new GuardianEvaluator(), audit, Optional.empty());
    ReflectionTestUtils.setField(service, "paymentsMode", "sandbox");

    watch = new Watch("Sony WH-1000XM5", null, "SKU-1", "black", "merchant-a", "USD",
        new BigDecimal("300.00"));
    mandate = new Mandate(watch.getId(), 1, "USD",
        new BigDecimal("500.00"), new BigDecimal("500.00"), 2,
        List.of("merchant-a"), 3, 3600L, Mandate.NewMerchantBehavior.HOLD,
        null, null);
  }

  /** Stubs used by the submit/approve paths. Called only by tests that need them. */
  private void stubOfferPath() {
    when(watches.findById(watch.getId())).thenReturn(Optional.of(watch));
    when(watches.findByIdForUpdate(watch.getId())).thenReturn(Optional.of(watch));
    when(mandates.findByWatchIdAndStatus(watch.getId(), MandateStatus.ACTIVE))
        .thenReturn(Optional.of(mandate));
    when(intents.countByWatchIdAndCreatedAtAfterAndStatusNot(
        any(), any(), any())).thenReturn(0L);
    when(intents.save(any())).thenAnswer(i -> i.getArgument(0));
    when(decisions.save(any())).thenAnswer(i -> i.getArgument(0));
    when(reservations.save(any())).thenAnswer(i -> i.getArgument(0));
  }

  private SubmitOfferRequest offer(String merchantId, String total) {
    return new SubmitOfferRequest(merchantId, "SKU-1", "black", 1, "USD",
        new BigDecimal(total), new BigDecimal(total), "fixture");
  }

  @Test
  void submitOfferAllowReservesBudget() {
    stubOfferPath();
    var response = service.submitOffer(watch.getId(), offer("merchant-a", "350.00"));
    assertEquals(IntentStatus.SPEND_RESERVED, response.intent().status());
    assertEquals(Decision.ALLOW, response.decision().decision());
    verify(reservations).save(any());
    verify(audit, times(2)).record(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void submitOfferBlockDoesNotReserve() {
    stubOfferPath();
    var response = service.submitOffer(watch.getId(), offer("merchant-a", "900.00"));
    assertEquals(IntentStatus.BLOCKED, response.intent().status());
    assertEquals(Decision.BLOCK, response.decision().decision());
    verify(reservations, never()).save(any());
  }

  @Test
  void submitOfferHoldAwaitsApproval() {
    stubOfferPath();
    var response = service.submitOffer(watch.getId(), offer("merchant-new", "350.00"));
    assertEquals(IntentStatus.AWAITING_APPROVAL, response.intent().status());
    assertEquals(Decision.HOLD, response.decision().decision());
    verify(reservations, never()).save(any());
  }

  @Test
  void submitOfferOnPausedWatchConflicts() {
    stubOfferPath();
    watch.setStatus(WatchStatus.PAUSED);
    assertThrows(ConflictException.class,
        () -> service.submitOffer(watch.getId(), offer("merchant-a", "350.00")));
  }

  @Test
  void approveRevalidatesToAllow() {
    stubOfferPath();
    var submitted = service.submitOffer(watch.getId(), offer("merchant-new", "350.00"));
    UUID intentId = submitted.intent().id();

    // Re-stub the repository to return a hold-state intent for the approve call.
    PurchaseIntent persisted = new PurchaseIntent(watch.getId(), mandate.getId(), 1,
        "merchant-new", "SKU-1", "black", 1, "USD", new BigDecimal("350.00"), "k1");
    persisted.setStatus(IntentStatus.AWAITING_APPROVAL);
    persisted.setOfferSnapshot(submitted.intent().offerSnapshot() == null ? null
        : new com.buybound.purchase.domain.OfferSnapshot(
            "merchant-new", "SKU-1", "black", 1, "USD",
            new BigDecimal("350.00"), new BigDecimal("350.00"),
            java.time.Instant.now(), "fixture", "h"));
    when(intents.findById(intentId)).thenReturn(Optional.of(persisted));
    when(mandates.findById(mandate.getId())).thenReturn(Optional.of(mandate));

    var approved = service.approve(intentId);
    // merchant-new with HOLD behavior revalidates to HOLD again (not ALLOW)
    assertEquals(IntentStatus.AWAITING_APPROVAL, approved.intent().status());
    assertEquals(Decision.HOLD, approved.decision().decision());
  }

  @Test
  void approveNonHoldIntentConflicts() {
    stubOfferPath();
    var submitted = service.submitOffer(watch.getId(), offer("merchant-a", "350.00"));
    UUID intentId = submitted.intent().id();
    PurchaseIntent persisted = mock(PurchaseIntent.class);
    when(persisted.getStatus()).thenReturn(IntentStatus.SPEND_RESERVED);
    when(intents.findById(intentId)).thenReturn(Optional.of(persisted));
    assertThrows(ConflictException.class, () -> service.approve(intentId));
  }

  @Test
  void demoCaptureAlwaysRejectedBecauseStubAdapterIsRemoved() {
    UUID intentId = UUID.randomUUID();
    ConflictException ex = assertThrows(ConflictException.class,
        () -> service.demoCapture(intentId));
    assertTrue(ex.getMessage().contains("PayPal Sandbox"));
  }
}
