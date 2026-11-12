package com.buybound.guardian.dto;

import com.buybound.guardian.domain.Policy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Request body for POST /api/guardian/evaluations. Stateless: nothing is persisted. */
public record EvaluationRequest(
    @NotNull @Valid PolicyDto policy,
    @NotNull @Valid OfferDto offer,
    @Valid ContextDto context) {

  public record PolicyDto(
      @NotBlank String mandateId,
      @Min(1) int version,
      @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
      @NotNull BigDecimal maxUnitPrice,
      @NotNull BigDecimal maxTotal,
      @Min(1) int maxQuantity,
      @NotNull List<String> allowedMerchantIds,
      @Min(0) int maxPurchasesPerDay,
      @Min(1) long offerMaxAgeSeconds,
      @NotNull Policy.NewMerchantBehavior newMerchantBehavior,
      BigDecimal requireHumanApprovalAbove,
      BigDecimal maxWeeklySpend,
      BigDecimal targetPrice,
      @NotBlank String policyVersion,
      Policy.MandateStatus mandateStatus,
      Double minSellerRating) {}

  public record OfferDto(
      @NotBlank String merchantId,
      String canonicalSku,
      String variant,
      @Min(1) int quantity,
      @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
      @NotNull BigDecimal unitPrice,
      @NotNull BigDecimal totalPrice,
      @NotNull Instant observedAt,
      String source,
      String evidenceHash,
      Double sellerRating,
      Integer sellerReviewCount,
      Boolean verifiedSellerHistory,
      Integer negativeRedFlagCount,
      String sellerRedFlagSummary,
      String estimatedDelivery) {}

  public record ContextDto(
      @Min(0) Integer purchasesToday,
      BigDecimal remainingBudget,
      Instant now) {}
}
