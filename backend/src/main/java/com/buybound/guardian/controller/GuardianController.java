package com.buybound.guardian.controller;

import com.buybound.guardian.domain.EvaluationContext;
import com.buybound.guardian.domain.GuardianDecisionResult;
import com.buybound.guardian.domain.Offer;
import com.buybound.guardian.domain.Policy;
import com.buybound.guardian.dto.EvaluationRequest;
import com.buybound.guardian.dto.EvaluationResponse;
import com.buybound.guardian.dto.RuntimeGuardrailSettings;
import com.buybound.guardian.service.GuardianEvaluator;
import com.buybound.guardian.service.RuntimeGuardrailSettingsService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stateless Guardian evaluation playground. Nothing is persisted here; this
 * endpoint exists so judges and developers can see exactly how the deterministic
 * policy engine reasons about a policy + offer pair.
 */
@RestController
@RequestMapping("/api/guardian")
public class GuardianController {
  private final GuardianEvaluator evaluator;

  @Autowired(required = false)
  private RuntimeGuardrailSettingsService runtimeSettingsService;

  public GuardianController(GuardianEvaluator evaluator) {
    this.evaluator = evaluator;
  }

  @GetMapping("/runtime-settings")
  public ResponseEntity<RuntimeGuardrailSettings> getRuntimeSettings() {
    RuntimeGuardrailSettings current = runtimeSettingsService != null
        ? runtimeSettingsService.getSettings()
        : RuntimeGuardrailSettingsService.currentSnapshot();
    return ResponseEntity.ok(current);
  }

  @PutMapping("/runtime-settings")
  public ResponseEntity<RuntimeGuardrailSettings> updateRuntimeSettings(
      @RequestBody RuntimeGuardrailSettings request) {
    RuntimeGuardrailSettings updated = runtimeSettingsService != null
        ? runtimeSettingsService.updateSettings(request)
        : request;
    return ResponseEntity.ok(updated);
  }

  @PostMapping("/evaluations")
  public ResponseEntity<EvaluationResponse> evaluate(@Valid @RequestBody EvaluationRequest request) {
    EvaluationRequest.PolicyDto p = request.policy();
    EvaluationRequest.OfferDto o = request.offer();
    EvaluationRequest.ContextDto c = request.context();

    Policy policy = new Policy(
        p.mandateId(), p.version(), p.currency(), p.maxUnitPrice(), p.maxTotal(),
        p.maxQuantity(), p.allowedMerchantIds(), p.maxPurchasesPerDay(),
        p.offerMaxAgeSeconds(), p.newMerchantBehavior(), p.requireHumanApprovalAbove(),
        p.maxWeeklySpend(), p.targetPrice(), p.policyVersion(),
        p.mandateStatus() != null ? p.mandateStatus() : Policy.MandateStatus.ACTIVE,
        p.minSellerRating());

    String evidenceHash = o.evidenceHash() != null ? o.evidenceHash() : sha256(
        o.merchantId() + "|" + o.canonicalSku() + "|" + o.variant() + "|"
            + o.quantity() + "|" + o.currency() + "|" + o.unitPrice());

    Offer offer = new Offer(
        o.merchantId(), o.canonicalSku(), o.variant(), o.quantity(), o.currency(),
        o.unitPrice(), o.totalPrice(), o.observedAt(), o.source(), evidenceHash,
        null, null, true, false, false, null,
        o.sellerRating(), o.sellerReviewCount(), o.verifiedSellerHistory(),
        o.negativeRedFlagCount(), o.sellerRedFlagSummary(), o.estimatedDelivery());

    EvaluationContext context = new EvaluationContext(
        c != null && c.purchasesToday() != null ? c.purchasesToday() : 0,
        c != null ? c.remainingBudget() : null,
        c != null ? c.now() : null);

    GuardianDecisionResult result = evaluator.evaluate(policy, offer, context);
    return ResponseEntity.ok(new EvaluationResponse(
        result.decision(), result.reasonCodes(), result.policyVersion(),
        result.evaluatedAt(), result.expiresAt(), result.ruleChecks()));
  }

  private String sha256(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
