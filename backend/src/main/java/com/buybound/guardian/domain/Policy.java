package com.buybound.guardian.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Spending policy under evaluation. Mirrors contracts/guardian-policy.schema.json
 * and the OpenAPI Mandate schema. Immutable value object — the evaluator never
 * mutates policy and no LLM output can alter it.
 */
public record Policy(
    String mandateId,
    int version,
    String currency,
    BigDecimal maxUnitPrice,
    BigDecimal maxTotal,
    int maxQuantity,
    List<String> allowedMerchantIds,
    int maxPurchasesPerDay,
    long offerMaxAgeSeconds,
    NewMerchantBehavior newMerchantBehavior,
    BigDecimal requireHumanApprovalAbove,
    BigDecimal maxWeeklySpend,
    BigDecimal targetPrice,
    String policyVersion,
    MandateStatus mandateStatus,
    Double minSellerRating) {

  public Policy(
      String mandateId,
      int version,
      String currency,
      BigDecimal maxUnitPrice,
      BigDecimal maxTotal,
      int maxQuantity,
      List<String> allowedMerchantIds,
      int maxPurchasesPerDay,
      long offerMaxAgeSeconds,
      NewMerchantBehavior newMerchantBehavior,
      BigDecimal requireHumanApprovalAbove,
      BigDecimal maxWeeklySpend,
      BigDecimal targetPrice,
      String policyVersion,
      MandateStatus mandateStatus) {
    this(mandateId, version, currency, maxUnitPrice, maxTotal, maxQuantity,
        allowedMerchantIds, maxPurchasesPerDay, offerMaxAgeSeconds, newMerchantBehavior,
        requireHumanApprovalAbove, maxWeeklySpend, targetPrice, policyVersion,
        mandateStatus, 4.5);
  }

  public Double effectiveMinSellerRating() {
    return minSellerRating != null ? minSellerRating : 4.5;
  }

  public enum NewMerchantBehavior { ALLOW, HOLD, REQUIRE_APPROVAL, BLOCK }

  public enum MandateStatus { ACTIVE, PAUSED, REVOKED }
}
