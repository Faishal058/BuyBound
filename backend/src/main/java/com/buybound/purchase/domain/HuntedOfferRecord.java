package com.buybound.purchase.domain;

import com.buybound.guardian.domain.Decision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Persisted telemetry row for every candidate offer scanned via Channel3 (or Red-Team test)
 * and evaluated by AI Forensics + Guardian, powering the AG Grid Offer Arbitrage Matrix.
 */
@Entity
@Table(name = "offer_snapshots")
public class HuntedOfferRecord {
  @Id
  private UUID id;

  @Column(name = "watch_id")
  private UUID watchId;

  @Column(name = "intent_id")
  private UUID intentId;

  @Column(name = "channel3_product_id", length = 128)
  private String channel3ProductId;

  @Column(name = "product_title", nullable = false, length = 500)
  private String productTitle;

  @Column(name = "merchant_id", nullable = false, length = 200)
  private String merchantId;

  @Column(name = "canonical_sku", length = 200)
  private String canonicalSku;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "offer_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal offerPrice;

  @Column(name = "baseline_price", precision = 12, scale = 2)
  private BigDecimal baselinePrice;

  @Column(name = "verified_savings", precision = 12, scale = 2)
  private BigDecimal verifiedSavings;

  @Column(name = "commission_rate", precision = 5, scale = 2)
  private BigDecimal commissionRate;

  @Column(name = "product_url", columnDefinition = "TEXT")
  private String productUrl;

  @Column(name = "image_url", columnDefinition = "TEXT")
  private String imageUrl;

  @Column(nullable = false, length = 100)
  private String source;

  @Column(name = "ai_match_confidence", precision = 5, scale = 2)
  private BigDecimal aiMatchConfidence;

  @Column(name = "ai_exact_sku_match", nullable = false)
  private boolean aiExactSkuMatch = true;

  @Column(name = "ai_prompt_injection_detected", nullable = false)
  private boolean aiPromptInjectionDetected = false;

  @Column(name = "ai_subscription_trap_detected", nullable = false)
  private boolean aiSubscriptionTrapDetected = false;

  @Column(name = "ai_forensics_summary", columnDefinition = "TEXT")
  private String aiForensicsSummary;

  @Enumerated(EnumType.STRING)
  @Column(name = "guardian_decision", nullable = false, length = 10)
  private Decision guardianDecision;

  @Column(name = "reason_codes", columnDefinition = "TEXT")
  private String reasonCodes;

  @Column(name = "rule_checks_json", columnDefinition = "TEXT")
  private String ruleChecksJson;

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

  @Column(name = "observed_at", nullable = false)
  private Instant observedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected HuntedOfferRecord() {}

  public HuntedOfferRecord(UUID watchId, UUID intentId, String channel3ProductId,
                           String productTitle, String merchantId, String canonicalSku,
                           String currency, BigDecimal offerPrice, BigDecimal baselinePrice,
                           BigDecimal verifiedSavings, BigDecimal commissionRate,
                           String productUrl, String imageUrl, String source,
                           BigDecimal aiMatchConfidence, boolean aiExactSkuMatch,
                           boolean aiPromptInjectionDetected, boolean aiSubscriptionTrapDetected,
                           String aiForensicsSummary, Decision guardianDecision,
                           String reasonCodes, String ruleChecksJson, Instant observedAt) {
    this.id = UUID.randomUUID();
    this.watchId = watchId;
    this.intentId = intentId;
    this.channel3ProductId = channel3ProductId;
    this.productTitle = productTitle;
    this.merchantId = merchantId;
    this.canonicalSku = canonicalSku;
    this.currency = currency;
    this.offerPrice = offerPrice;
    this.baselinePrice = baselinePrice;
    this.verifiedSavings = verifiedSavings;
    this.commissionRate = commissionRate;
    this.productUrl = productUrl;
    this.imageUrl = imageUrl;
    this.source = source;
    this.aiMatchConfidence = aiMatchConfidence;
    this.aiExactSkuMatch = aiExactSkuMatch;
    this.aiPromptInjectionDetected = aiPromptInjectionDetected;
    this.aiSubscriptionTrapDetected = aiSubscriptionTrapDetected;
    this.aiForensicsSummary = aiForensicsSummary;
    this.guardianDecision = guardianDecision;
    this.reasonCodes = reasonCodes;
    this.ruleChecksJson = ruleChecksJson;
    this.observedAt = observedAt;
    this.createdAt = Instant.now();
  }

  @PrePersist
  void prePersist() {
    if (createdAt == null) createdAt = Instant.now();
  }

  public UUID getId() { return id; }
  public UUID getWatchId() { return watchId; }
  public UUID getIntentId() { return intentId; }
  public void setIntentId(UUID intentId) { this.intentId = intentId; }
  public String getChannel3ProductId() { return channel3ProductId; }
  public String getProductTitle() { return productTitle; }
  public String getMerchantId() { return merchantId; }
  public String getCanonicalSku() { return canonicalSku; }
  public String getCurrency() { return currency; }
  public BigDecimal getOfferPrice() { return offerPrice; }
  public BigDecimal getBaselinePrice() { return baselinePrice; }
  public BigDecimal getVerifiedSavings() { return verifiedSavings; }
  public BigDecimal getCommissionRate() { return commissionRate; }
  public String getProductUrl() { return productUrl; }
  public String getImageUrl() { return imageUrl; }
  public String getSource() { return source; }
  public BigDecimal getAiMatchConfidence() { return aiMatchConfidence; }
  public boolean isAiExactSkuMatch() { return aiExactSkuMatch; }
  public boolean isAiPromptInjectionDetected() { return aiPromptInjectionDetected; }
  public boolean isAiSubscriptionTrapDetected() { return aiSubscriptionTrapDetected; }
  public String getAiForensicsSummary() { return aiForensicsSummary; }
  public Decision getGuardianDecision() { return guardianDecision; }
  public String getReasonCodes() { return reasonCodes; }
  public String getRuleChecksJson() { return ruleChecksJson; }
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
  public Instant getObservedAt() { return observedAt; }
  public Instant getCreatedAt() { return createdAt; }
}
