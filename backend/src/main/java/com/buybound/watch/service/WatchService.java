package com.buybound.watch.service;

import com.buybound.audit.AuditService;
import com.buybound.common.exception.ConflictException;
import com.buybound.common.exception.ResourceNotFoundException;
import com.buybound.watch.domain.Mandate;
import com.buybound.watch.domain.MandateStatus;
import com.buybound.watch.domain.Watch;
import com.buybound.watch.domain.WatchStatus;
import com.buybound.watch.dto.CreateWatchRequest;
import com.buybound.watch.dto.MandateResponse;
import com.buybound.watch.dto.WatchResponse;
import com.buybound.watch.repository.MandateRepository;
import com.buybound.watch.repository.WatchRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WatchService {
  private final WatchRepository watches;
  private final MandateRepository mandates;
  private final AuditService audit;

  public WatchService(WatchRepository watches, MandateRepository mandates, AuditService audit) {
    this.watches = watches;
    this.mandates = mandates;
    this.audit = audit;
  }

  @Transactional
  public WatchResponse create(CreateWatchRequest req) {
    if (req.maxUnitPrice().compareTo(req.maxTotal()) > 0) {
      throw new IllegalArgumentException("maxUnitPrice cannot exceed maxTotal");
    }
    Watch watch = new Watch(
        req.productName(), req.productId(), req.sku(), req.variant(),
        req.merchantId(), req.currency(), req.targetPrice());
    watch.setNaturalLanguagePrompt(req.naturalLanguagePrompt());
    watch.setReferenceProductUrl(req.referenceProductUrl());
    watch.setBaselineMarketPrice(req.baselineMarketPrice());
    watches.save(watch);

    List<String> merchants = req.allowedMerchantIds() == null || req.allowedMerchantIds().isEmpty()
        ? List.of(req.merchantId())
        : new ArrayList<>(req.allowedMerchantIds());
    if (!merchants.contains(req.merchantId())) {
      merchants = new ArrayList<>(merchants);
      merchants.add(req.merchantId());
    }
    Mandate.NewMerchantBehavior behavior = parseBehavior(req.newMerchantBehavior());
    BigDecimal approvalAbove = req.requireHumanApprovalAbove();
    if (Boolean.TRUE.equals(req.requiresHumanApproval()) && approvalAbove == null) {
      approvalAbove = BigDecimal.ZERO;
    }

    Mandate mandate = new Mandate(
        watch.getId(), 1, req.currency(), req.maxUnitPrice(), req.maxTotal(),
        req.maxQuantity(), merchants,
        req.maxPurchasesPerDay() != null ? req.maxPurchasesPerDay() : 3,
        req.offerMaxAgeSeconds() != null ? req.offerMaxAgeSeconds() : 3600L,
        behavior, approvalAbove, req.maxWeeklySpend());
    if (req.minSellerRating() != null) {
      mandate.setMinSellerRating(req.minSellerRating());
    }
    mandates.save(mandate);

    audit.record("watch.created", "watch", watch.getId().toString(),
        watch.getId().toString(), null, null,
        "Watch created with mandate v1 for " + req.productName());
    return WatchResponse.of(watch, mandate.getId(), mandate.getVersion());
  }

  @Transactional(readOnly = true)
  public List<WatchResponse> list() {
    return watches.findAll().stream()
        .map(w -> WatchResponse.of(w, activeMandateId(w.getId()), activeMandateVersion(w.getId())))
        .toList();
  }

  @Transactional(readOnly = true)
  public WatchResponse get(UUID id) {
    Watch watch = watches.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Watch not found: " + id));
    return WatchResponse.of(watch, activeMandateId(id), activeMandateVersion(id));
  }

  @Transactional(readOnly = true)
  public MandateResponse mandate(UUID watchId) {
    getWatch(watchId);
    return MandateResponse.of(activeMandate(watchId));
  }

  @Transactional
  public WatchResponse pause(UUID id) {
    Watch watch = getWatch(id);
    if (watch.getStatus() == WatchStatus.REVOKED) {
      throw new ConflictException("Revoked watches cannot be paused");
    }
    watch.setStatus(WatchStatus.PAUSED);
    audit.record("watch.paused", "watch", id.toString(), id.toString(), null, null,
        "Watch paused; no new purchase work will start");
    return WatchResponse.of(watch, activeMandateId(id), activeMandateVersion(id));
  }

  @Transactional
  public WatchResponse revoke(UUID id) {
    Watch watch = getWatch(id);
    watch.setStatus(WatchStatus.REVOKED);
    mandates.findByWatchIdAndStatus(id, MandateStatus.ACTIVE).ifPresent(m -> {
      m.setStatus(MandateStatus.REVOKED);
      m.setRevokedAt(Instant.now());
      m.setRevocationReason("User revoked watch mandate");
    });
    audit.record("mandate.revoked", "watch", id.toString(), id.toString(), null, null,
        "Mandate revoked; future autonomous buying blocked immediately");
    return WatchResponse.of(watch, null, 0);
  }

  @Transactional
  public java.util.Map<String, Object> killSwitch() {
    List<Watch> activeWatches = watches.findAll().stream()
        .filter(w -> w.getStatus() != WatchStatus.REVOKED)
        .toList();
    for (Watch w : activeWatches) {
      w.setStatus(WatchStatus.REVOKED);
      watches.save(w);
    }

    List<Mandate> activeMandates = mandates.findAll().stream()
        .filter(m -> m.getStatus() == MandateStatus.ACTIVE)
        .toList();
    Instant now = Instant.now();
    for (Mandate m : activeMandates) {
      m.setStatus(MandateStatus.REVOKED);
      m.setRevokedAt(now);
      m.setRevocationReason("Emergency Kill Switch activated");
      mandates.save(m);
    }

    audit.record("kill_switch.activated", "system", "kill_switch", "kill_switch", null, null,
        "Emergency Kill Switch activated: " + activeWatches.size() + " watches and "
            + activeMandates.size() + " mandates revoked. Autonomous buying halted immediately.");

    return java.util.Map.of(
        "status", "REVOKED",
        "revokedWatches", activeWatches.size(),
        "revokedMandates", activeMandates.size(),
        "message", "Emergency Kill Switch activated. All autonomous buying disabled immediately.");
  }

  private Watch getWatch(UUID id) {
    return watches.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Watch not found: " + id));
  }

  private Mandate activeMandate(UUID watchId) {
    return mandates.findByWatchIdAndStatus(watchId, MandateStatus.ACTIVE)
        .orElseThrow(() -> new ConflictException("No active mandate for watch: " + watchId));
  }

  private UUID activeMandateId(UUID watchId) {
    return mandates.findByWatchIdAndStatus(watchId, MandateStatus.ACTIVE)
        .map(Mandate::getId).orElse(null);
  }

  private int activeMandateVersion(UUID watchId) {
    return mandates.findByWatchIdAndStatus(watchId, MandateStatus.ACTIVE)
        .map(Mandate::getVersion).orElse(0);
  }

  private Mandate.NewMerchantBehavior parseBehavior(String raw) {
    if (raw == null || raw.isBlank()) {
      return Mandate.NewMerchantBehavior.HOLD;
    }
    try {
      return Mandate.NewMerchantBehavior.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("newMerchantBehavior must be HOLD or BLOCK");
    }
  }
}
