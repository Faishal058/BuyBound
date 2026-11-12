package com.buybound.watch.service;

import com.buybound.audit.AuditService;
import com.buybound.common.exception.ConflictException;
import com.buybound.common.exception.ResourceNotFoundException;
import com.buybound.guardian.domain.Decision;
import com.buybound.payment.PayPalPaymentAdapter;
import com.buybound.payment.PayPalPaymentAdapter.VaultPaymentTokenResult;
import com.buybound.payment.PayPalPaymentAdapter.VaultSetupResult;
import com.buybound.purchase.domain.HuntedOfferRecord;
import com.buybound.purchase.domain.IntentStatus;
import com.buybound.purchase.domain.PurchaseIntent;
import com.buybound.purchase.domain.SpendReservation;
import com.buybound.purchase.repository.HuntedOfferRepository;
import com.buybound.purchase.repository.PurchaseIntentRepository;
import com.buybound.purchase.repository.SpendReservationRepository;
import com.buybound.watch.domain.Mandate;
import com.buybound.watch.domain.MandateStatus;
import com.buybound.watch.domain.Watch;
import com.buybound.watch.domain.WatchStatus;
import com.buybound.watch.dto.ActiveMandateTelemetryResponse;
import com.buybound.watch.dto.UpdateMandateRequest;
import com.buybound.watch.repository.MandateRepository;
import com.buybound.watch.repository.WatchRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MandateService {
  private final MandateRepository mandates;
  private final WatchRepository watches;
  private final PurchaseIntentRepository intents;
  private final HuntedOfferRepository offers;
  private final SpendReservationRepository reservations;
  private final AuditService audit;
  private final Optional<PayPalPaymentAdapter> payPalAdapter;

  public MandateService(MandateRepository mandates, WatchRepository watches,
                        PurchaseIntentRepository intents, HuntedOfferRepository offers,
                        SpendReservationRepository reservations, AuditService audit,
                        Optional<PayPalPaymentAdapter> payPalAdapter) {
    this.mandates = mandates;
    this.watches = watches;
    this.intents = intents;
    this.offers = offers;
    this.reservations = reservations;
    this.audit = audit;
    this.payPalAdapter = payPalAdapter;
  }

  @Transactional(readOnly = true)
  public ActiveMandateTelemetryResponse getActiveTelemetry() {
    List<Mandate> allMandates = mandates.findAllByOrderByCreatedAtDesc();
    Mandate primary = allMandates.stream()
        .filter(m -> m.getStatus() == MandateStatus.ACTIVE)
        .findFirst()
        .orElse(allMandates.isEmpty() ? null : allMandates.get(0));

    List<Watch> allWatches = watches.findAll();
    int activeWatches = (int) allWatches.stream().filter(w -> w.getStatus() == WatchStatus.ACTIVE).count();

    Instant weekStart = Instant.now().atZone(ZoneOffset.UTC).toLocalDate()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .atStartOfDay(ZoneOffset.UTC).toInstant();
    BigDecimal weeklySpent = reservations.sumGlobalSpentOrReservedSince(weekStart);
    if (weeklySpent == null) weeklySpent = BigDecimal.ZERO;

    BigDecimal maxWeekly = primary != null && primary.getMaxWeeklySpend() != null
        ? primary.getMaxWeeklySpend()
        : new BigDecimal("500.00");
    BigDecimal remaining = maxWeekly.subtract(weeklySpent).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

    List<PurchaseIntent> allIntents = intents.findAllByOrderByCreatedAtDesc();
    List<HuntedOfferRecord> allOffers = offers.findAllByOrderByCreatedAtDesc();

    BigDecimal totalVerifiedSavings = allIntents.stream()
        .filter(i -> i.getStatus() == IntentStatus.CAPTURED || i.getStatus() == IntentStatus.SPEND_RESERVED)
        .map(i -> i.getOfferSnapshot() != null && i.getOfferSnapshot().getVerifiedSavings() != null
            ? i.getOfferSnapshot().getVerifiedSavings() : BigDecimal.ZERO)
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .setScale(2, RoundingMode.HALF_UP);

    BigDecimal totalCommission = allIntents.stream()
        .filter(i -> i.getStatus() == IntentStatus.CAPTURED)
        .map(i -> {
          if (i.getOfferSnapshot() == null || i.getOfferSnapshot().getCommissionRate() == null
              || i.getOfferSnapshot().getTotalPrice() == null) {
            return BigDecimal.ZERO;
          }
          return i.getOfferSnapshot().getTotalPrice()
              .multiply(i.getOfferSnapshot().getCommissionRate())
              .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        })
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .setScale(2, RoundingMode.HALF_UP);

    long blockedFromOffers = allOffers.stream()
        .filter(o -> o.getGuardianDecision() == Decision.BLOCK)
        .count();
    long blockedFromIntents = allIntents.stream()
        .filter(i -> i.getStatus() == IntentStatus.BLOCKED)
        .count();
    long blockedThreats = Math.max(blockedFromOffers, blockedFromIntents);

    long heldApprovals = allIntents.stream()
        .filter(i -> i.getStatus() == IntentStatus.HELD_FOR_APPROVAL || i.getStatus() == IntentStatus.AWAITING_APPROVAL)
        .count();
    long capturedCount = allIntents.stream()
        .filter(i -> i.getStatus() == IntentStatus.CAPTURED)
        .count();

    String vaultToken = allMandates.stream()
        .filter(m -> m.getStatus() == MandateStatus.ACTIVE && m.getPaypalVaultToken() != null)
        .map(Mandate::getPaypalVaultToken)
        .findFirst()
        .orElse(primary != null ? primary.getPaypalVaultToken() : null);

    String payerEmail = allMandates.stream()
        .filter(m -> m.getStatus() == MandateStatus.ACTIVE && m.getPaypalPayerEmail() != null)
        .map(Mandate::getPaypalPayerEmail)
        .findFirst()
        .orElse(primary != null ? primary.getPaypalPayerEmail() : null);

    String payerName = allMandates.stream()
        .filter(m -> m.getStatus() == MandateStatus.ACTIVE && m.getPaypalPayerName() != null)
        .map(Mandate::getPaypalPayerName)
        .findFirst()
        .orElse(primary != null ? primary.getPaypalPayerName() : null);

    String payerId = allMandates.stream()
        .filter(m -> m.getStatus() == MandateStatus.ACTIVE && m.getPaypalPayerId() != null)
        .map(Mandate::getPaypalPayerId)
        .findFirst()
        .orElse(primary != null ? primary.getPaypalPayerId() : null);

    BigDecimal sandboxBalance = allMandates.stream()
        .filter(m -> m.getSandboxBalance() != null)
        .map(Mandate::getSandboxBalance)
        .min(BigDecimal::compareTo)
        .map(b -> b.setScale(2, RoundingMode.HALF_UP))
        .orElse(null);

    boolean buyerConnected = (vaultToken != null && !vaultToken.isBlank())
        || (payerId != null && !payerId.isBlank());

    BigDecimal maxUnitPrice = primary != null && primary.getMaxUnitPrice() != null
        ? primary.getMaxUnitPrice().setScale(2, RoundingMode.HALF_UP)
        : new BigDecimal("260.00");
    List<String> allowedMerchants = primary != null && primary.getAllowedMerchantIds() != null
        ? primary.getAllowedMerchantIds()
        : List.of("bestbuy.com", "macys.com", "target.com");
    String newMerchantBehavior = primary != null && primary.getNewMerchantBehavior() != null
        ? primary.getNewMerchantBehavior().name()
        : Mandate.NewMerchantBehavior.HOLD.name();

    return new ActiveMandateTelemetryResponse(
        primary != null ? primary.getId() : null,
        primary != null ? primary.getWatchId() : null,
        primary != null ? primary.getStatus() : MandateStatus.REVOKED,
        primary != null ? primary.getCurrency() : "USD",
        maxWeekly.setScale(2, RoundingMode.HALF_UP),
        weeklySpent.setScale(2, RoundingMode.HALF_UP),
        remaining,
        activeWatches,
        allWatches.size(),
        buyerConnected,
        vaultToken != null && !vaultToken.isBlank(),
        ActiveMandateTelemetryResponse.maskVaultToken(vaultToken),
        payerEmail,
        totalVerifiedSavings,
        blockedThreats,
        heldApprovals,
        capturedCount,
        totalCommission,
        maxUnitPrice,
        allowedMerchants,
        sandboxBalance,
        payerName,
        payerId,
        newMerchantBehavior);
  }

  @Transactional
  public Map<String, Object> connectPayPalSetup(UUID mandateId) {
    Mandate mandate = mandates.findById(mandateId)
        .orElseThrow(() -> new ResourceNotFoundException("Mandate not found: " + mandateId));
    if (mandate.getStatus() != MandateStatus.ACTIVE) {
      throw new ConflictException("Mandate is revoked and cannot connect PayPal.");
    }
    PayPalPaymentAdapter pp = payPalAdapter.orElseThrow(() ->
        new ConflictException("PayPal Sandbox adapter is not active."));
    VaultSetupResult setup = pp.createSetupTokenWithLinks();
    if (setup.setupTokenId() == null) {
      throw new ConflictException(setup.message());
    }
    audit.record("mandate.paypal_setup_initiated", "mandate", mandateId.toString(),
        mandateId.toString(), null, null,
        "PayPal Vault setup token created: " + setup.setupTokenId());
    return Map.of(
        "mandateId", mandateId.toString(),
        "setupTokenId", setup.setupTokenId(),
        "status", setup.status(),
        "approvalUrl", setup.approvalUrl() != null ? setup.approvalUrl() : "",
        "message", setup.message());
  }

  @Transactional
  public Map<String, Object> completePayPalVault(UUID mandateId, String setupTokenId) {
    Mandate mandate = mandates.findById(mandateId)
        .orElseThrow(() -> new ResourceNotFoundException("Mandate not found: " + mandateId));
    if (mandate.getStatus() != MandateStatus.ACTIVE) {
      throw new ConflictException("Mandate is revoked.");
    }
    PayPalPaymentAdapter pp = payPalAdapter.orElseThrow(() ->
        new ConflictException("PayPal Sandbox adapter is not active."));
    VaultPaymentTokenResult tokenRes = pp.exchangeSetupTokenForPaymentToken(setupTokenId);
    if (tokenRes.paymentTokenId() == null) {
      throw new ConflictException(tokenRes.message());
    }

    // Link this user's Sandbox Vault Payment Token, Payer ID, and masked email across active mandates.
    String cleanEmail = tokenRes.payerEmail() != null && !tokenRes.payerEmail().isBlank()
        ? tokenRes.payerEmail().trim().toLowerCase(Locale.ROOT)
        : null;
    String verifiedPayerId = tokenRes.payerId() != null && !tokenRes.payerId().isBlank()
        ? tokenRes.payerId()
        : "VAULT-" + tokenRes.paymentTokenId().substring(0, Math.min(12, tokenRes.paymentTokenId().length()));
    List<Mandate> activeMandates = mandates.findByStatusOrderByCreatedAtDesc(MandateStatus.ACTIVE);
    for (Mandate m : activeMandates) {
      m.setPaypalVaultToken(tokenRes.paymentTokenId());
      m.setPaypalPayerEmail(cleanEmail);
      m.setPaypalPayerId(verifiedPayerId);
      mandates.save(m);
    }

    audit.record("mandate.paypal_vault_connected", "mandate", mandateId.toString(),
        mandateId.toString(), null, null,
        "PayPal Vault token verified and connected (payer " + verifiedPayerId + ").");

    return Map.of(
        "mandateId", mandateId.toString(),
        "vaultConnected", true,
        "maskedVaultToken", ActiveMandateTelemetryResponse.maskVaultToken(tokenRes.paymentTokenId()),
        "paypalPayerId", verifiedPayerId,
        "paypalPayerEmail", cleanEmail != null ? cleanEmail : "verified-sandbox-buyer",
        "status", "CONNECTED",
        "message", tokenRes.message());
  }

  @Transactional
  public Map<String, Object> killSwitch(UUID mandateId) {
    mandates.findById(mandateId)
        .orElseThrow(() -> new ResourceNotFoundException("Mandate not found: " + mandateId));

    List<Mandate> activeMandates = mandates.findByStatusOrderByCreatedAtDesc(MandateStatus.ACTIVE);
    Instant now = Instant.now();
    int tokensRevoked = 0;

    for (Mandate m : activeMandates) {
      if (m.getPaypalVaultToken() != null && !m.getPaypalVaultToken().isBlank() && payPalAdapter.isPresent()) {
        payPalAdapter.get().deletePaymentToken(m.getPaypalVaultToken());
        tokensRevoked++;
      }
      m.setPaypalVaultToken(null);
      m.setStatus(MandateStatus.REVOKED);
      m.setRevokedAt(now);
      m.setRevocationReason("MANDATE_KILL_SWITCH_ACTIVATED");
      mandates.save(m);
    }

    List<Watch> activeWatches = watches.findAll().stream()
        .filter(w -> w.getStatus() == WatchStatus.ACTIVE)
        .toList();
    for (Watch w : activeWatches) {
      w.setStatus(WatchStatus.PAUSED);
      watches.save(w);
    }

    List<SpendReservation> pendingReservations = reservations.findByStatus(SpendReservation.ReservationStatus.ACTIVE);
    for (SpendReservation r : pendingReservations) {
      r.setStatus(SpendReservation.ReservationStatus.RELEASED);
      reservations.save(r);
    }

    List<PurchaseIntent> heldIntents = intents.findAllByOrderByCreatedAtDesc().stream()
        .filter(i -> i.getStatus() == IntentStatus.HELD_FOR_APPROVAL
            || i.getStatus() == IntentStatus.AWAITING_APPROVAL
            || i.getStatus() == IntentStatus.SPEND_RESERVED)
        .toList();
    for (PurchaseIntent i : heldIntents) {
      if (i.getPaypalAuthorizationId() != null && payPalAdapter.isPresent()) {
        payPalAdapter.get().voidHeldIntent(i);
      }
      i.setStatus(IntentStatus.BLOCKED);
      i.setFailureReason("Mandate Kill Switch activated");
      intents.save(i);
    }

    audit.record("MANDATE_KILL_SWITCH_ACTIVATED", "mandate", mandateId.toString(),
        mandateId.toString(), "MANDATE_REVOKED", "1.0",
        "One-Tap Kill Switch activated: " + activeMandates.size() + " mandates revoked, "
            + activeWatches.size() + " watches paused, " + pendingReservations.size()
            + " pending reservations released.");

    return Map.of(
        "status", "REVOKED",
        "revokedMandates", activeMandates.size(),
        "pausedWatches", activeWatches.size(),
        "releasedReservations", pendingReservations.size(),
        "vaultTokensDeleted", tokensRevoked,
        "message", "Buying paused — All spending rules disabled, PayPal authorization severed, and active watches paused.");
  }

  @Transactional
  public Map<String, Object> restore(UUID mandateId) {
    mandates.findById(mandateId)
        .orElseThrow(() -> new ResourceNotFoundException("Mandate not found: " + mandateId));

    List<Mandate> revokedMandates = mandates.findByStatusOrderByCreatedAtDesc(MandateStatus.REVOKED);
    for (Mandate m : revokedMandates) {
      m.setStatus(MandateStatus.ACTIVE);
      m.setRevokedAt(null);
      m.setRevocationReason(null);
      mandates.save(m);
    }

    List<Watch> pausedWatches = watches.findAll().stream()
        .filter(w -> w.getStatus() == WatchStatus.PAUSED)
        .toList();
    for (Watch w : pausedWatches) {
      w.setStatus(WatchStatus.ACTIVE);
      watches.save(w);
    }

    audit.record("MANDATE_RESTORED", "mandate", mandateId.toString(),
        mandateId.toString(), null, "1.0",
        "Buying re-enabled: " + revokedMandates.size() + " mandates restored, "
            + pausedWatches.size() + " watches reactivated.");

    return Map.of(
        "status", "ACTIVE",
        "restoredMandates", revokedMandates.size(),
        "reactivatedWatches", pausedWatches.size(),
        "message", "Automated buying re-enabled — your spending limits and price watches are active again.");
  }

  /**
   * Updates per-item, weekly spend caps, and approved merchant domains across the shopper's active mandates.
   */
  @Transactional
  public ActiveMandateTelemetryResponse updateLimits(UUID mandateId, UpdateMandateRequest req) {
    Mandate target = mandates.findById(mandateId)
        .orElseThrow(() -> new ResourceNotFoundException("Mandate not found: " + mandateId));

    BigDecimal unitCap = req.maxUnitPrice() != null
        ? req.maxUnitPrice().setScale(2, RoundingMode.HALF_UP)
        : target.getMaxUnitPrice();
    BigDecimal totalCap = req.maxTotal() != null
        ? req.maxTotal().setScale(2, RoundingMode.HALF_UP)
        : (unitCap != null ? unitCap.max(target.getMaxTotal()) : target.getMaxTotal());

    if (unitCap != null && totalCap != null && unitCap.compareTo(totalCap) > 0) {
      throw new IllegalArgumentException("maxUnitPrice cannot exceed maxTotal");
    }

    BigDecimal weeklyCap = req.maxWeeklySpend() != null
        ? req.maxWeeklySpend().setScale(2, RoundingMode.HALF_UP)
        : target.getMaxWeeklySpend();

    List<String> normalizedMerchants = normalizeMerchants(req.allowedMerchantIds());

    Mandate.NewMerchantBehavior parsedBehavior = parseNewMerchantBehavior(req.newMerchantBehavior());
    MandateStatus targetStatus = req.status();

    List<Mandate> siblingMandates = new ArrayList<>();
    siblingMandates.add(target);
    for (Mandate m : mandates.findAllByOrderByCreatedAtDesc()) {
      if (!m.getId().equals(target.getId()) && m.getStatus() != MandateStatus.REVOKED) {
        siblingMandates.add(m);
      }
    }

    for (Mandate m : siblingMandates) {
      if (unitCap != null) {
        m.setMaxUnitPrice(unitCap);
      }
      if (totalCap != null) {
        m.setMaxTotal(totalCap);
      }
      if (weeklyCap != null) {
        m.setMaxWeeklySpend(weeklyCap);
      }
      if (req.requireHumanApprovalAbove() != null) {
        m.setRequireHumanApprovalAbove(req.requireHumanApprovalAbove().setScale(2, RoundingMode.HALF_UP));
      } else if (m.getRequireHumanApprovalAbove() != null && unitCap != null
          && m.getRequireHumanApprovalAbove().compareTo(unitCap) < 0) {
        m.setRequireHumanApprovalAbove(unitCap);
      }
      if (normalizedMerchants != null) {
        m.setAllowedMerchantIds(normalizedMerchants);
      }
      if (parsedBehavior != null) {
        m.setNewMerchantBehavior(parsedBehavior);
      }
      if (targetStatus != null) {
        m.setStatus(targetStatus);
        if (targetStatus == MandateStatus.ACTIVE) {
          m.setRevokedAt(null);
          m.setRevocationReason(null);
        }
      }
      mandates.save(m);
    }

    audit.record(
        "MANDATE_LIMITS_UPDATED",
        "mandate",
        mandateId.toString(),
        mandateId.toString(),
        null,
        "1.0",
        String.format("Updated spending limits across %d mandates: maxUnitPrice=$%s, maxWeeklySpend=$%s%s%s",
            siblingMandates.size(),
            unitCap != null ? unitCap.toPlainString() : "N/A",
            weeklyCap != null ? weeklyCap.toPlainString() : "N/A",
            targetStatus != null ? ", status=" + targetStatus : "",
            parsedBehavior != null ? ", newMerchantBehavior=" + parsedBehavior : ""));

    return getActiveTelemetry();
  }

  /**
   * Releases all currently locked ACTIVE spend reservations so demo weekly budgets
   * reset cleanly without deleting captured transaction history.
   */
  @Transactional
  public Map<String, Object> resetReservations() {
    List<SpendReservation> active = reservations.findAll().stream()
        .filter(r -> r.getStatus() == SpendReservation.ReservationStatus.ACTIVE)
        .toList();
    for (SpendReservation r : active) {
      r.setStatus(SpendReservation.ReservationStatus.RELEASED);
      reservations.save(r);
    }
    audit.record(
        "SPEND_RESERVATIONS_RESET",
        "mandate",
        "global",
        "global",
        null,
        "1.0",
        "Released " + active.size() + " active spend reservations for demo budget reset.");
    return Map.of(
        "status", "RESET",
        "releasedCount", active.size(),
        "message", "Released " + active.size() + " active spend reservation(s).");
  }

  private Mandate.NewMerchantBehavior parseNewMerchantBehavior(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return Mandate.NewMerchantBehavior.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  /**
   * Synchronizes the verified PayPal Sandbox buyer identity returned by OAuth or Orders Capture.
   */
  @Transactional
  public ActiveMandateTelemetryResponse syncVerifiedPayPalBuyer(
      String payerId,
      String payerEmail,
      String payerName,
      BigDecimal sandboxBalance) {
    String cleanPayerId = payerId != null && !payerId.isBlank() ? payerId.trim() : null;
    String cleanEmail = payerEmail != null && !payerEmail.isBlank()
        ? payerEmail.trim().toLowerCase(Locale.ROOT)
        : null;
    if (cleanPayerId == null && cleanEmail == null) {
      throw new IllegalArgumentException("PayPal Sandbox payer identity is required.");
    }
    String cleanName = payerName != null && !payerName.isBlank() ? payerName.trim() : null;
    List<Mandate> targets = mandates.findAllByOrderByCreatedAtDesc();
    if (targets.isEmpty()) {
      throw new ConflictException("Create or re-enable a price alert before connecting a PayPal Sandbox buyer account.");
    }
    BigDecimal resolvedBalance = sandboxBalance != null && sandboxBalance.compareTo(BigDecimal.ZERO) > 0
        ? sandboxBalance.setScale(2, RoundingMode.HALF_UP)
        : null;
    for (Mandate m : targets) {
      if (cleanPayerId != null) {
        m.setPaypalPayerId(cleanPayerId);
      }
      if (cleanEmail != null) {
        m.setPaypalPayerEmail(cleanEmail);
      }
      if (cleanName != null) {
        m.setPaypalPayerName(cleanName);
      }
      if (resolvedBalance != null) {
        m.setSandboxBalance(resolvedBalance);
      }
      mandates.save(m);
    }

    audit.record(
        "mandate.paypal_buyer_verified",
        "mandate",
        targets.getFirst().getId().toString(),
        targets.getFirst().getId().toString(),
        null,
        "1.0",
        "PayPal Sandbox buyer connected (" + (cleanPayerId != null ? cleanPayerId : "sandbox-buyer") + ").");
    return getActiveTelemetry();
  }

  /**
   * Compatibility helper syncing verified payer email & name onto existing mandates.
   */
  @Transactional
  public void syncBuyerIdentity(String payerEmail, String payerName, BigDecimal sandboxBalance) {
    syncVerifiedPayPalBuyer(null, payerEmail, payerName, sandboxBalance);
  }

  /**
   * Disconnects the currently linked PayPal Sandbox buyer across all mandates
   * and restores the starting $5,000.00 Sandbox wallet tracking balance.
   */
  @Transactional
  public ActiveMandateTelemetryResponse disconnectPayPal() {
    List<Mandate> all = mandates.findAllByOrderByCreatedAtDesc();
    for (Mandate m : all) {
      m.setPaypalVaultToken(null);
      m.setPaypalPayerId(null);
      m.setPaypalPayerEmail(null);
      m.setPaypalPayerName(null);
      m.setSandboxBalance(null);
      mandates.save(m);
    }
    if (!all.isEmpty()) {
      audit.record(
          "mandate.paypal_disconnected",
          "mandate",
          all.getFirst().getId().toString(),
          all.getFirst().getId().toString(),
          null,
          "1.0",
          "Disconnected linked PayPal Sandbox buyer account.");
    }
    return getActiveTelemetry();
  }

  /**
   * Deducts a real captured PayPal Sandbox amount from the shopper's tracked $5,000 balance
   * and synchronizes verified buyer identity returned from PayPal Sandbox.
   */
  @Transactional
  public BigDecimal recordSandboxCapture(BigDecimal amount, String payerEmail, String payerName) {
    return recordSandboxCapture(amount, payerEmail, payerName, null);
  }

  @Transactional
  public BigDecimal recordSandboxCapture(
      BigDecimal amount, String payerEmail, String payerName, String payerId) {
    List<Mandate> targets = mandates.findByStatusOrderByCreatedAtDesc(MandateStatus.ACTIVE);
    if (targets.isEmpty()) {
      targets = mandates.findAllByOrderByCreatedAtDesc();
    }
    BigDecimal currentBalance = targets.stream()
        .filter(m -> m.getSandboxBalance() != null)
        .map(Mandate::getSandboxBalance)
        .min(BigDecimal::compareTo)
        .orElse(null);
    BigDecimal delta = amount != null ? amount.max(BigDecimal.ZERO) : BigDecimal.ZERO;
    BigDecimal nextBalance = currentBalance != null
        ? currentBalance.subtract(delta).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
        : null;

    String cleanEmail = payerEmail != null && !payerEmail.isBlank()
        ? payerEmail.trim().toLowerCase(Locale.ROOT)
        : null;
    for (Mandate m : targets) {
      if (nextBalance != null) {
        m.setSandboxBalance(nextBalance);
      }
      if (payerId != null && !payerId.isBlank()) {
        m.setPaypalPayerId(payerId.trim());
      }
      if (cleanEmail != null && !cleanEmail.isBlank()) {
        m.setPaypalPayerEmail(cleanEmail);
      }
      if (payerName != null && !payerName.isBlank()) {
        m.setPaypalPayerName(payerName.trim());
      }
      mandates.save(m);
    }
    return nextBalance;
  }

  private List<String> normalizeMerchants(List<String> raw) {
    if (raw == null) {
      return null;
    }
    Set<String> clean = new LinkedHashSet<>();
    for (String m : raw) {
      if (m == null || m.isBlank()) continue;
      String domain = m.trim().toLowerCase(Locale.ROOT);
      if (domain.startsWith("www.")) {
        domain = domain.substring(4);
      }
      if (!domain.isEmpty()) {
        clean.add(domain);
      }
    }
    return new ArrayList<>(clean);
  }
}

