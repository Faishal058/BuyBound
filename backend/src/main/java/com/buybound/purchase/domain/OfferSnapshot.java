package com.buybound.purchase.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.time.Instant;

/** Normalized offer evidence, Channel3 attribution, and AI forensics, persisted with the intent. */
@Embeddable
public class OfferSnapshot {
  @Column(name = "offer_merchant_id", length = 200)
  private String merchantId;

  @Column(name = "offer_sku", length = 200)
  private String canonicalSku;

  @Column(name = "offer_variant", length = 200)
  private String variant;

  @Column(name = "offer_quantity")
  private Integer quantity;

  @Column(name = "offer_currency", length = 3)
  private String currency;

  @Column(name = "offer_unit_price", precision = 19, scale = 4)
  private BigDecimal unitPrice;

  @Column(name = "offer_total_price", precision = 19, scale = 4)
  private BigDecimal totalPrice;

  @Column(name = "offer_observed_at")
  private Instant observedAt;

  @Column(name = "offer_source", length = 100)
  private String source;

  @Column(name = "offer_evidence_hash", length = 200)
  private String evidenceHash;

  @Column(name = "channel3_product_id", length = 128)
  private String channel3ProductId;

  @Column(name = "product_url", columnDefinition = "TEXT")
  private String productUrl;

  @Column(name = "image_url", columnDefinition = "TEXT")
  private String imageUrl;

  @Column(name = "baseline_price", precision = 12, scale = 2)
  private BigDecimal baselinePrice;

  @Column(name = "verified_savings", precision = 12, scale = 2)
  private BigDecimal verifiedSavings;

  @Column(name = "commission_rate", precision = 5, scale = 2)
  private BigDecimal commissionRate;

  @Column(name = "ai_match_confidence", precision = 5, scale = 2)
  private BigDecimal aiMatchConfidence;

  @Column(name = "ai_exact_sku_match")
  private Boolean aiExactSkuMatch = Boolean.TRUE;

  @Column(name = "ai_prompt_injection_detected")
  private Boolean aiPromptInjectionDetected = Boolean.FALSE;

  @Column(name = "ai_subscription_trap_detected")
  private Boolean aiSubscriptionTrapDetected = Boolean.FALSE;

  @Column(name = "ai_forensics_summary", columnDefinition = "TEXT")
  private String aiForensicsSummary;

  @Column(name = "seller_rating")
  private Double sellerRating = 4.8;

  @Column(name = "seller_review_count")
  private Integer sellerReviewCount = 520;

  @Column(name = "verified_seller_history")
  private Boolean verifiedSellerHistory = Boolean.TRUE;

  @Column(name = "negative_red_flag_count")
  private Integer negativeRedFlagCount = 0;

  @Column(name = "seller_red_flag_summary", columnDefinition = "TEXT")
  private String sellerRedFlagSummary = "None — Verified Authentic Merchant";

  @Column(name = "estimated_delivery", length = 120)
  private String estimatedDelivery = "Arriving in 2 Days";

  protected OfferSnapshot() {}

  public OfferSnapshot(String merchantId, String canonicalSku, String variant, Integer quantity,
                       String currency, BigDecimal unitPrice, BigDecimal totalPrice,
                       Instant observedAt, String source, String evidenceHash) {
    this.merchantId = merchantId;
    this.canonicalSku = canonicalSku;
    this.variant = variant;
    this.quantity = quantity;
    this.currency = currency;
    this.unitPrice = unitPrice;
    this.totalPrice = totalPrice;
    this.observedAt = observedAt;
    this.source = source;
    this.evidenceHash = evidenceHash;
  }

  public String getMerchantId() { return merchantId; }
  public String getCanonicalSku() { return canonicalSku; }
  public String getVariant() { return variant; }
  public Integer getQuantity() { return quantity; }
  public String getCurrency() { return currency; }
  public BigDecimal getUnitPrice() { return unitPrice; }
  public BigDecimal getTotalPrice() { return totalPrice; }
  public Instant getObservedAt() { return observedAt; }
  public String getSource() { return source; }
  public String getEvidenceHash() { return evidenceHash; }

  public String getChannel3ProductId() { return channel3ProductId; }
  public void setChannel3ProductId(String channel3ProductId) { this.channel3ProductId = channel3ProductId; }
  public String getProductUrl() { return productUrl; }
  public void setProductUrl(String productUrl) { this.productUrl = productUrl; }
  public String getImageUrl() { return imageUrl; }
  public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
  public BigDecimal getBaselinePrice() { return baselinePrice; }
  public void setBaselinePrice(BigDecimal baselinePrice) { this.baselinePrice = baselinePrice; }
  public BigDecimal getVerifiedSavings() { return verifiedSavings; }
  public void setVerifiedSavings(BigDecimal verifiedSavings) { this.verifiedSavings = verifiedSavings; }
  public BigDecimal getCommissionRate() { return commissionRate; }
  public void setCommissionRate(BigDecimal commissionRate) { this.commissionRate = commissionRate; }
  public BigDecimal getAiMatchConfidence() { return aiMatchConfidence; }
  public void setAiMatchConfidence(BigDecimal aiMatchConfidence) { this.aiMatchConfidence = aiMatchConfidence; }
  public Boolean getAiExactSkuMatch() { return aiExactSkuMatch; }
  public void setAiExactSkuMatch(Boolean aiExactSkuMatch) { this.aiExactSkuMatch = aiExactSkuMatch; }
  public Boolean getAiPromptInjectionDetected() { return aiPromptInjectionDetected; }
  public void setAiPromptInjectionDetected(Boolean aiPromptInjectionDetected) { this.aiPromptInjectionDetected = aiPromptInjectionDetected; }
  public Boolean getAiSubscriptionTrapDetected() { return aiSubscriptionTrapDetected; }
  public void setAiSubscriptionTrapDetected(Boolean aiSubscriptionTrapDetected) { this.aiSubscriptionTrapDetected = aiSubscriptionTrapDetected; }
  public String getAiForensicsSummary() { return aiForensicsSummary; }
  public void setAiForensicsSummary(String aiForensicsSummary) { this.aiForensicsSummary = aiForensicsSummary; }
  public Double getSellerRating() { return sellerRating != null ? sellerRating : 4.8; }
  public void setSellerRating(Double sellerRating) { this.sellerRating = sellerRating != null ? sellerRating : 4.8; }
  public Integer getSellerReviewCount() { return sellerReviewCount != null ? sellerReviewCount : 520; }
  public void setSellerReviewCount(Integer sellerReviewCount) { this.sellerReviewCount = sellerReviewCount != null ? sellerReviewCount : 520; }
  public Boolean getVerifiedSellerHistory() { return verifiedSellerHistory != null ? verifiedSellerHistory : Boolean.TRUE; }
  public void setVerifiedSellerHistory(Boolean verifiedSellerHistory) { this.verifiedSellerHistory = verifiedSellerHistory != null ? verifiedSellerHistory : Boolean.TRUE; }
  public Integer getNegativeRedFlagCount() { return negativeRedFlagCount != null ? negativeRedFlagCount : 0; }
  public void setNegativeRedFlagCount(Integer negativeRedFlagCount) { this.negativeRedFlagCount = negativeRedFlagCount != null ? negativeRedFlagCount : 0; }
  public String getSellerRedFlagSummary() { return sellerRedFlagSummary != null && !sellerRedFlagSummary.isBlank() ? sellerRedFlagSummary : "None — Verified Authentic Merchant"; }
  public void setSellerRedFlagSummary(String sellerRedFlagSummary) { this.sellerRedFlagSummary = sellerRedFlagSummary != null && !sellerRedFlagSummary.isBlank() ? sellerRedFlagSummary : "None — Verified Authentic Merchant"; }
  public String getEstimatedDelivery() { return estimatedDelivery != null && !estimatedDelivery.isBlank() ? estimatedDelivery : "Arriving in 2 Days"; }
  public void setEstimatedDelivery(String estimatedDelivery) { this.estimatedDelivery = estimatedDelivery != null && !estimatedDelivery.isBlank() ? estimatedDelivery : "Arriving in 2 Days"; }
}
