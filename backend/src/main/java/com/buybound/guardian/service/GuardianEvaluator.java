package com.buybound.guardian.service;

import com.buybound.guardian.domain.Decision;
import com.buybound.guardian.domain.EvaluationContext;
import com.buybound.guardian.domain.GuardianDecisionResult;
import com.buybound.guardian.domain.Offer;
import com.buybound.guardian.domain.Policy;
import com.buybound.guardian.domain.ReasonCode;
import com.buybound.guardian.domain.RuleCheckDetail;
import com.buybound.guardian.dto.RuntimeGuardrailSettings;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Deterministic Guardian policy evaluator.
 *
 * <p>Pure function of (policy, offer, context): no I/O, no randomness, no LLM.
 * Rules are evaluated in a fixed order and the most severe verdict wins
 * (BLOCK &gt; HOLD &gt; ALLOW). Missing, stale, contradictory or malformed data
 * fails closed — it can never widen authorization.
 */
@Service
public class GuardianEvaluator {

  private final ObjectProvider<RuntimeGuardrailSettingsService> settingsProvider;

  public GuardianEvaluator() {
    this.settingsProvider = null;
  }

  @Autowired
  public GuardianEvaluator(ObjectProvider<RuntimeGuardrailSettingsService> settingsProvider) {
    this.settingsProvider = settingsProvider;
  }

  private RuntimeGuardrailSettings resolveSettings() {
    if (settingsProvider == null) {
      return RuntimeGuardrailSettings.defaults();
    }
    RuntimeGuardrailSettingsService svc = settingsProvider.getIfAvailable();
    return svc != null ? svc.getSettings() : RuntimeGuardrailSettings.defaults();
  }

  /** How long an ALLOW remains valid before the offer must be re-evaluated. */
  private static final long DECISION_TTL_MINUTES = 15;

  /** Maximum acceptable price drift (±2%) between ranking and capture evaluation. */
  private static final BigDecimal PRICE_DRIFT_TOLERANCE = new BigDecimal("0.02");

  /** Minimum ratio of baseline price (45%, i.e., >55% discount triggers anomalous-discount HOLD). */
  private static final BigDecimal ANOMALOUS_DISCOUNT_FLOOR_RATIO = new BigDecimal("0.45");

  private static final BigDecimal MIN_AI_CONFIDENCE = new BigDecimal("70.00");

  private static final List<String> PROMPT_INJECTION_SIGNATURES = List.of(
      "system override", "ignore previous", "ignore user budget",
      "bypass guardian", "disregard mandate", "ignore all caps");

  public GuardianDecisionResult evaluate(Policy policy, Offer offer, EvaluationContext context) {
    if (policy == null || offer == null || context == null) {
      throw new IllegalArgumentException("policy, offer and context are required");
    }
    Instant now = context.now();
    Set<ReasonCode> blockers = EnumSet.noneOf(ReasonCode.class);
    Set<ReasonCode> holders = EnumSet.noneOf(ReasonCode.class);
    List<RuleCheckDetail> checks = new ArrayList<>();

    // 1. Mandate must be active.
    boolean mandateActive = policy.mandateStatus() == Policy.MandateStatus.ACTIVE;
    if (!mandateActive) {
      blockers.add(ReasonCode.MANDATE_REVOKED);
    }
    checks.add(new RuleCheckDetail(
        "Mandate Active", mandateActive, "ACTIVE",
        String.valueOf(policy.mandateStatus()),
        mandateActive ? "OK" : ReasonCode.MANDATE_REVOKED.name()));

    // 2. Adversarial Prompt Injection Firewall (AI forensic flag + deterministic title scan).
    boolean titleInjection = containsInjectionSignature(offer.listingTitle());
    boolean injectionDetected = offer.promptInjectionDetected() || titleInjection;
    if (injectionDetected) {
      blockers.add(ReasonCode.PROMPT_INJECTION_DETECTED);
    }
    checks.add(new RuleCheckDetail(
        "Prompt Injection Firewall", !injectionDetected, "CLEAN_LISTING",
        injectionDetected ? "INJECTION_DETECTED" : "CLEAN",
        injectionDetected ? ReasonCode.PROMPT_INJECTION_DETECTED.name() : "OK"));

    // 3. Exact SKU / Accessory Bait Firewall.
    boolean skuOk = offer.exactSkuMatch();
    if (!skuOk) {
      blockers.add(ReasonCode.SKU_MISMATCH_ACCESSORY_BAIT);
    }
    checks.add(new RuleCheckDetail(
        "Exact SKU Verification", skuOk, "EXACT_PRODUCT_MATCH",
        skuOk ? "MATCHED" : "MISMATCH_ACCESSORY_BAIT",
        skuOk ? "OK" : ReasonCode.SKU_MISMATCH_ACCESSORY_BAIT.name()));

    // 4. Hidden Subscription / Recurring Trap Firewall.
    boolean noSubTrap = !offer.subscriptionTrapDetected();
    if (!noSubTrap) {
      blockers.add(ReasonCode.SUBSCRIPTION_TRAP_BLOCKED);
    }
    checks.add(new RuleCheckDetail(
        "Subscription Trap Check", noSubTrap, "ONE_TIME_PURCHASE",
        noSubTrap ? "ONE_TIME" : "RECURRING_TRAP",
        noSubTrap ? "OK" : ReasonCode.SUBSCRIPTION_TRAP_BLOCKED.name()));

    // 5. Evidence completeness — fail closed on anything missing or malformed.
    boolean evidenceComplete = !(isBlank(offer.merchantId())
        || isBlank(offer.currency())
        || isBlank(offer.evidenceHash())
        || offer.observedAt() == null
        || offer.unitPrice() == null
        || offer.totalPrice() == null
        || offer.quantity() < 1
        || isBlank(policy.currency())
        || policy.allowedMerchantIds() == null);
    if (!evidenceComplete) {
      blockers.add(ReasonCode.INCOMPLETE_EVIDENCE);
    }
    checks.add(new RuleCheckDetail(
        "Evidence Completeness", evidenceComplete, "COMPLETE_SNAPSHOT",
        evidenceComplete ? "VERIFIED" : "MISSING_FIELDS",
        evidenceComplete ? "OK" : ReasonCode.INCOMPLETE_EVIDENCE.name()));

    // 6. Currency must match exactly.
    boolean currencyOk = true;
    if (evidenceComplete && !offer.currency().equals(policy.currency())) {
      currencyOk = false;
      blockers.add(ReasonCode.CURRENCY_MISMATCH);
    }
    checks.add(new RuleCheckDetail(
        "Currency Match", currencyOk, safe(policy.currency()),
        safe(offer.currency()), currencyOk ? "OK" : ReasonCode.CURRENCY_MISMATCH.name()));

    // 7. Quantity within mandate.
    boolean qtyOk = offer.quantity() <= policy.maxQuantity();
    if (!qtyOk) {
      blockers.add(ReasonCode.QUANTITY_MISMATCH);
    }
    checks.add(new RuleCheckDetail(
        "Quantity Cap", qtyOk, "<= " + policy.maxQuantity(),
        String.valueOf(offer.quantity()), qtyOk ? "OK" : ReasonCode.QUANTITY_MISMATCH.name()));

    // 8. Merchant allowlist; unknown merchants follow the configured behavior.
    boolean merchantAllowlisted = policy.allowedMerchantIds() != null
        && policy.allowedMerchantIds().contains(offer.merchantId());
    if (!merchantAllowlisted) {
      if (policy.newMerchantBehavior() == Policy.NewMerchantBehavior.ALLOW) {
        merchantAllowlisted = true;
      } else if (policy.newMerchantBehavior() == Policy.NewMerchantBehavior.HOLD
          || policy.newMerchantBehavior() == Policy.NewMerchantBehavior.REQUIRE_APPROVAL) {
        holders.add(ReasonCode.NEW_MERCHANT_HOLD);
      } else {
        blockers.add(ReasonCode.MERCHANT_NOT_ALLOWLISTED);
      }
    }
    checks.add(new RuleCheckDetail(
        "Merchant Whitelist", merchantAllowlisted,
        policy.allowedMerchantIds() != null ? String.join(", ", policy.allowedMerchantIds()) : "NONE",
        safe(offer.merchantId()),
        merchantAllowlisted ? "OK"
            : ((policy.newMerchantBehavior() == Policy.NewMerchantBehavior.HOLD
                || policy.newMerchantBehavior() == Policy.NewMerchantBehavior.REQUIRE_APPROVAL)
                ? ReasonCode.NEW_MERCHANT_HOLD.name()
                : ReasonCode.MERCHANT_NOT_ALLOWLISTED.name())));

    // 9. Price caps (Max Unit Price & Max Total).
    boolean unitPriceOk = offer.unitPrice() == null
        || offer.unitPrice().compareTo(policy.maxUnitPrice()) <= 0;
    if (!unitPriceOk) {
      blockers.add(ReasonCode.UNIT_PRICE_ABOVE_CAP);
    }
    checks.add(new RuleCheckDetail(
        "Max Item Limit", unitPriceOk,
        "<= " + policy.maxUnitPrice().toPlainString() + " " + safe(policy.currency()),
        offer.unitPrice() != null ? offer.unitPrice().toPlainString() : "N/A",
        unitPriceOk ? "OK" : ReasonCode.UNIT_PRICE_ABOVE_CAP.name()));

    boolean totalPriceOk = offer.totalPrice() == null
        || offer.totalPrice().compareTo(policy.maxTotal()) <= 0;
    if (!totalPriceOk) {
      blockers.add(ReasonCode.PRICE_ABOVE_MAX_TOTAL);
    }
    checks.add(new RuleCheckDetail(
        "Max Order Total Limit", totalPriceOk,
        "<= " + policy.maxTotal().toPlainString() + " " + safe(policy.currency()),
        offer.totalPrice() != null ? offer.totalPrice().toPlainString() : "N/A",
        totalPriceOk ? "OK" : ReasonCode.PRICE_ABOVE_MAX_TOTAL.name()));

    // 10. Offer freshness.
    boolean freshOk = offer.observedAt() == null
        || Duration.between(offer.observedAt(), now).getSeconds() <= policy.offerMaxAgeSeconds();
    if (!freshOk) {
      blockers.add(ReasonCode.STALE_OFFER);
    }

    // 11. Velocity cap.
    boolean velocityOk = context.purchasesToday() < policy.maxPurchasesPerDay();
    if (!velocityOk) {
      blockers.add(ReasonCode.VELOCITY_EXCEEDED);
    }
    checks.add(new RuleCheckDetail(
        "Daily Velocity Cap", velocityOk, "< " + policy.maxPurchasesPerDay(),
        String.valueOf(context.purchasesToday()),
        velocityOk ? "OK" : ReasonCode.VELOCITY_EXCEEDED.name()));

    // 12. Remaining weekly budget (only when the mandate defines a weekly spend cap).
    boolean budgetOk = true;
    if (context.remainingBudget() != null
        && offer.totalPrice() != null
        && offer.totalPrice().compareTo(context.remainingBudget()) > 0) {
      budgetOk = false;
      blockers.add(ReasonCode.BUDGET_EXHAUSTED);
    }
    checks.add(new RuleCheckDetail(
        "Weekly Budget Cap", budgetOk,
        context.remainingBudget() != null ? "<= " + context.remainingBudget().toPlainString() : "UNBOUNDED",
        offer.totalPrice() != null ? offer.totalPrice().toPlainString() : "N/A",
        budgetOk ? "OK" : ReasonCode.BUDGET_EXHAUSTED.name()));

    // 13. Human approval threshold → HOLD, never a bypass.
    if (blockers.isEmpty()
        && policy.requireHumanApprovalAbove() != null
        && offer.totalPrice() != null
        && offer.totalPrice().compareTo(policy.requireHumanApprovalAbove()) > 0) {
      holders.add(ReasonCode.APPROVAL_REQUIRED);
    }

    // 14. Ambiguous product identity or low AI confidence → human review.
    if (blockers.isEmpty() && isBlank(offer.canonicalSku())) {
      holders.add(ReasonCode.PRODUCT_IDENTITY_UNCERTAIN);
    }
    if (blockers.isEmpty()
        && offer.aiMatchConfidence() != null
        && offer.aiMatchConfidence().compareTo(MIN_AI_CONFIDENCE) < 0) {
      holders.add(ReasonCode.PRODUCT_IDENTITY_UNCERTAIN);
    }

    // 15. Anomalous steep discount (>55% off baseline) → human review HOLD.
    boolean discountNormal = true;
    if (blockers.isEmpty()
        && offer.baselinePrice() != null
        && offer.baselinePrice().compareTo(BigDecimal.ZERO) > 0
        && offer.unitPrice() != null
        && offer.exactSkuMatch()) {
      BigDecimal floorPrice = offer.baselinePrice()
          .multiply(ANOMALOUS_DISCOUNT_FLOOR_RATIO)
          .setScale(2, RoundingMode.HALF_UP);
      if (offer.unitPrice().compareTo(floorPrice) < 0) {
        discountNormal = false;
        holders.add(ReasonCode.ANOMALOUS_DISCOUNT_HOLD);
      }
    }
    checks.add(new RuleCheckDetail(
        "Anomalous Discount Guard", discountNormal,
        offer.baselinePrice() != null ? ">= 45% of " + offer.baselinePrice().toPlainString() : "N/A",
        offer.unitPrice() != null ? offer.unitPrice().toPlainString() : "N/A",
        discountNormal ? "OK" : ReasonCode.ANOMALOUS_DISCOUNT_HOLD.name()));

    // 16. Drop Price Band Guardrail (Target Price vs. Hard Ceiling Cap).
    BigDecimal hardCeiling = policy.maxTotal() != null ? policy.maxTotal() : policy.maxUnitPrice();
    boolean dropPriceOk = offer.totalPrice() == null
        || hardCeiling == null
        || offer.totalPrice().compareTo(hardCeiling) <= 0;
    if (!dropPriceOk) {
      blockers.add(ReasonCode.ITEM_PRICE_CAP_EXCEEDED);
    }
    String totalStr = offer.totalPrice() != null ? offer.totalPrice().setScale(2, RoundingMode.HALF_UP).toPlainString() : "N/A";
    String ceilingStr = hardCeiling != null ? hardCeiling.setScale(2, RoundingMode.HALF_UP).toPlainString() : "N/A";
    String bandLabel = policy.targetPrice() != null
        ? "Target $" + policy.targetPrice().setScale(2, RoundingMode.HALF_UP).toPlainString() + " / Hard Ceiling $" + ceilingStr
        : "Hard Ceiling $" + ceilingStr;
    String dropPriceMessage = dropPriceOk
        ? "Offer $" + totalStr + " is within " + bandLabel
        : "Offer $" + totalStr + " exceeds " + bandLabel;
    checks.add(new RuleCheckDetail(
        "DROP_PRICE_GUARDRAIL",
        dropPriceOk,
        "<= $" + ceilingStr + " (" + bandLabel + ")",
        dropPriceMessage,
        dropPriceOk ? ReasonCode.WITHIN_DROP_GUARDRAIL_BAND.name() : ReasonCode.ITEM_PRICE_CAP_EXCEEDED.name()));

    // 17. Seller Reputation & Negative Red Flag Check.
    RuntimeGuardrailSettings settings = resolveSettings();
    double minRating = policy.effectiveMinSellerRating() != null ? policy.effectiveMinSellerRating() : 4.5;
    double sellerRating = offer.getSellerRating();
    int reviewCount = offer.getSellerReviewCount();
    boolean verifiedHistory = Boolean.TRUE.equals(offer.getVerifiedSellerHistory());
    int redFlags = offer.getNegativeRedFlagCount();
    boolean strictSeller = settings.strictSellerVerification() && !settings.demoBypassEnabled();
    boolean hasRedFlags = strictSeller && redFlags > 0;
    boolean ratingTooLow = strictSeller && (sellerRating < minRating || !verifiedHistory);
    if (hasRedFlags) {
      blockers.add(ReasonCode.SELLER_NEGATIVE_REVIEW_RED_FLAG);
    }
    if (ratingTooLow) {
      blockers.add(ReasonCode.SELLER_RATING_TOO_LOW);
    }
    boolean sellerOk = !hasRedFlags && !ratingTooLow;
    String sellerActual = String.format(Locale.ROOT, "%.1f★ (%d reviews · %d red flags)%s",
        sellerRating, reviewCount, redFlags, strictSeller ? "" : " [Demo Relaxed]");
    String sellerReason = hasRedFlags
        ? ReasonCode.SELLER_NEGATIVE_REVIEW_RED_FLAG.name()
        : (ratingTooLow ? ReasonCode.SELLER_RATING_TOO_LOW.name() : "OK");
    checks.add(new RuleCheckDetail(
        "SELLER_REPUTATION_CHECK",
        sellerOk,
        strictSeller ? String.format(Locale.ROOT, ">= %.1f★ · 0 Red Flags", minRating) : "Demo Seller Bypass",
        sellerActual,
        sellerReason));

    // 18. Price drift detection (±2% tolerance between ranking and capture).
    if (context.previousUnitPrice() != null && offer.unitPrice() != null) {
      BigDecimal drift = offer.unitPrice().subtract(context.previousUnitPrice()).abs();
      BigDecimal tolerance = context.previousUnitPrice().multiply(PRICE_DRIFT_TOLERANCE);
      if (drift.compareTo(tolerance) > 0) {
        blockers.add(ReasonCode.PRICE_DRIFT_DETECTED);
      }
    }

    if (settings.guardianAutoApproveDemoMode() || settings.demoBypassEnabled()) {
      holders.clear();
      if (settings.guardianAutoApproveDemoMode()) {
        blockers.remove(ReasonCode.MERCHANT_NOT_ALLOWLISTED);
        blockers.remove(ReasonCode.NEW_MERCHANT_HOLD);
      }
      if (settings.demoBypassEnabled()) {
        blockers.remove(ReasonCode.MERCHANT_NOT_ALLOWLISTED);
        blockers.remove(ReasonCode.SELLER_NEGATIVE_REVIEW_RED_FLAG);
        blockers.remove(ReasonCode.SELLER_RATING_TOO_LOW);
        blockers.remove(ReasonCode.STALE_OFFER);
        blockers.remove(ReasonCode.VELOCITY_EXCEEDED);
        blockers.remove(ReasonCode.ITEM_PRICE_CAP_EXCEEDED);
        blockers.remove(ReasonCode.UNIT_PRICE_ABOVE_CAP);
        blockers.remove(ReasonCode.PRICE_ABOVE_MAX_TOTAL);
        blockers.remove(ReasonCode.BUDGET_EXHAUSTED);
      }
    }

    Decision decision;
    List<ReasonCode> codes = new ArrayList<>();
    if (!blockers.isEmpty()) {
      decision = Decision.BLOCK;
      codes.addAll(blockers);
    } else if (!holders.isEmpty()) {
      decision = Decision.HOLD;
      codes.addAll(holders);
    } else {
      decision = Decision.ALLOW;
      codes.add(ReasonCode.WITHIN_POLICY);
      if (dropPriceOk && policy.targetPrice() != null) {
        codes.add(ReasonCode.WITHIN_DROP_GUARDRAIL_BAND);
      }
      if (policy.targetPrice() != null
          && offer.totalPrice() != null
          && offer.totalPrice().compareTo(policy.targetPrice()) <= 0) {
        codes.add(ReasonCode.BELOW_TARGET_PRICE);
      }
    }

    return new GuardianDecisionResult(
        decision,
        List.copyOf(codes),
        policy.policyVersion(),
        now,
        now.plus(DECISION_TTL_MINUTES, ChronoUnit.MINUTES),
        List.copyOf(checks));
  }

  private boolean containsInjectionSignature(String listingTitle) {
    if (listingTitle == null || listingTitle.isBlank()) {
      return false;
    }
    String lower = listingTitle.toLowerCase(Locale.ROOT);
    return PROMPT_INJECTION_SIGNATURES.stream().anyMatch(lower::contains);
  }

  private boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  private String safe(String s) {
    return s == null ? "" : s;
  }
}
