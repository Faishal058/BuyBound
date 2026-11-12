package com.buybound.watch.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.List;

/** Request body for POST /api/watches. */
public record CreateWatchRequest(
    @NotBlank String productName,
    String productId,
    String sku,
    String variant,
    @NotBlank String merchantId,
    @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter ISO code") String currency,
    @NotNull @DecimalMin(value = "0.01", message = "must be positive") BigDecimal targetPrice,
    @NotNull @DecimalMin(value = "0.01", message = "must be positive") BigDecimal maxUnitPrice,
    @NotNull @DecimalMin(value = "0.01", message = "must be positive") BigDecimal maxTotal,
    @Min(1) @Max(10) int maxQuantity,
    List<String> allowedMerchantIds,
    Integer maxPurchasesPerDay,
    Long offerMaxAgeSeconds,
    String newMerchantBehavior,
    BigDecimal requireHumanApprovalAbove,
    BigDecimal maxWeeklySpend,
    Boolean requiresHumanApproval,
    String naturalLanguagePrompt,
    String referenceProductUrl,
    BigDecimal baselineMarketPrice,
    Double minSellerRating) {}
