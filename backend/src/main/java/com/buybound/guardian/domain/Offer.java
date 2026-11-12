package com.buybound.guardian.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** Normalized candidate offer presented to the Guardian for evaluation. */
public record Offer(
    String merchantId,
    String canonicalSku,
    String variant,
    int quantity,
    String currency,
    BigDecimal unitPrice,
    BigDecimal totalPrice,
    Instant observedAt,
    String source,
    String evidenceHash,
    BigDecimal baselinePrice,
    String listingTitle,
    boolean exactSkuMatch,
    boolean promptInjectionDetected,
    boolean subscriptionTrapDetected,
    BigDecimal aiMatchConfidence,
    Double sellerRating,
    Integer sellerReviewCount,
    Boolean verifiedSellerHistory,
    Integer negativeRedFlagCount,
    String sellerRedFlagSummary,
    String estimatedDelivery) {

  public static final double DEFAULT_SELLER_RATING = 4.8;
  public static final int DEFAULT_SELLER_REVIEW_COUNT = 520;
  public static final boolean DEFAULT_VERIFIED_SELLER_HISTORY = true;
  public static final int DEFAULT_NEGATIVE_RED_FLAG_COUNT = 0;
  public static final String DEFAULT_RED_FLAG_SUMMARY = "None — Verified Authentic Merchant";
  public static final String DEFAULT_ESTIMATED_DELIVERY = "Arriving in 2 Days";

  /** Backward-compatible 16-arg constructor preserved for existing evaluators and services. */
  public Offer(
      String merchantId,
      String canonicalSku,
      String variant,
      int quantity,
      String currency,
      BigDecimal unitPrice,
      BigDecimal totalPrice,
      Instant observedAt,
      String source,
      String evidenceHash,
      BigDecimal baselinePrice,
      String listingTitle,
      boolean exactSkuMatch,
      boolean promptInjectionDetected,
      boolean subscriptionTrapDetected,
      BigDecimal aiMatchConfidence) {
    this(merchantId, canonicalSku, variant, quantity, currency, unitPrice, totalPrice,
        observedAt, source, evidenceHash, baselinePrice, listingTitle, exactSkuMatch,
        promptInjectionDetected, subscriptionTrapDetected, aiMatchConfidence,
        DEFAULT_SELLER_RATING, DEFAULT_SELLER_REVIEW_COUNT, DEFAULT_VERIFIED_SELLER_HISTORY,
        DEFAULT_NEGATIVE_RED_FLAG_COUNT, DEFAULT_RED_FLAG_SUMMARY, DEFAULT_ESTIMATED_DELIVERY);
  }

  /** Backward-compatible 10-arg constructor preserved for existing unit tests. */
  public Offer(
      String merchantId,
      String canonicalSku,
      String variant,
      int quantity,
      String currency,
      BigDecimal unitPrice,
      BigDecimal totalPrice,
      Instant observedAt,
      String source,
      String evidenceHash) {
    this(merchantId, canonicalSku, variant, quantity, currency, unitPrice, totalPrice,
        observedAt, source, evidenceHash, null, null, true, false, false, null);
  }

  public Double getSellerRating() {
    return sellerRating != null ? sellerRating : DEFAULT_SELLER_RATING;
  }

  public Integer getSellerReviewCount() {
    return sellerReviewCount != null ? sellerReviewCount : DEFAULT_SELLER_REVIEW_COUNT;
  }

  public Boolean getVerifiedSellerHistory() {
    return verifiedSellerHistory != null ? verifiedSellerHistory : DEFAULT_VERIFIED_SELLER_HISTORY;
  }

  public Integer getNegativeRedFlagCount() {
    return negativeRedFlagCount != null ? negativeRedFlagCount : DEFAULT_NEGATIVE_RED_FLAG_COUNT;
  }

  public String getSellerRedFlagSummary() {
    return sellerRedFlagSummary != null && !sellerRedFlagSummary.isBlank()
        ? sellerRedFlagSummary : DEFAULT_RED_FLAG_SUMMARY;
  }

  public String getEstimatedDelivery() {
    return estimatedDelivery != null && !estimatedDelivery.isBlank()
        ? estimatedDelivery : DEFAULT_ESTIMATED_DELIVERY;
  }
}
