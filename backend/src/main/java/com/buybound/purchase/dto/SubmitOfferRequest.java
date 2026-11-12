package com.buybound.purchase.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/**
 * Candidate offer submitted for a watch. In production this arrives from the
 * discovery adapter; in the MVP demo it is submitted explicitly so the
 * Guardian evaluation path is exercisable without a Channel3 key.
 */
public record SubmitOfferRequest(
    @NotBlank String merchantId,
    String canonicalSku,
    String variant,
    @Min(1) int quantity,
    @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
    @NotNull @DecimalMin(value = "0.01") BigDecimal unitPrice,
    @NotNull @DecimalMin(value = "0.01") BigDecimal totalPrice,
    String source) {}
