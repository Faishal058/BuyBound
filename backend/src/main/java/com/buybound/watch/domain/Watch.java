package com.buybound.watch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "watches")
public class Watch {
  @Id
  private UUID id;

  @Column(name = "product_name", nullable = false, length = 200)
  private String productName;

  @Column(name = "product_id", length = 200)
  private String productId;

  @Column(length = 200)
  private String sku;

  @Column(length = 200)
  private String variant;

  @Column(name = "merchant_id", nullable = false, length = 200)
  private String merchantId;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "target_price", nullable = false, precision = 19, scale = 4)
  private BigDecimal targetPrice;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private WatchStatus status = WatchStatus.ACTIVE;

  @Column(name = "natural_language_prompt", columnDefinition = "TEXT")
  private String naturalLanguagePrompt;

  @Column(name = "reference_product_url", columnDefinition = "TEXT")
  private String referenceProductUrl;

  @Column(name = "baseline_market_price", precision = 12, scale = 2)
  private BigDecimal baselineMarketPrice;

  @Column(name = "last_hunted_at")
  private Instant lastHuntedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Watch() {}

  public Watch(String productName, String productId, String sku, String variant,
               String merchantId, String currency, BigDecimal targetPrice) {
    this.id = UUID.randomUUID();
    this.productName = productName;
    this.productId = productId;
    this.sku = sku;
    this.variant = variant;
    this.merchantId = merchantId;
    this.currency = currency;
    this.targetPrice = targetPrice;
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public String getProductName() { return productName; }
  public String getProductId() { return productId; }
  public void setProductId(String productId) { this.productId = productId; }
  public String getSku() { return sku; }
  public String getVariant() { return variant; }
  public String getMerchantId() { return merchantId; }
  public String getCurrency() { return currency; }
  public BigDecimal getTargetPrice() { return targetPrice; }
  public WatchStatus getStatus() { return status; }
  public void setStatus(WatchStatus status) { this.status = status; }
  public String getNaturalLanguagePrompt() { return naturalLanguagePrompt; }
  public void setNaturalLanguagePrompt(String naturalLanguagePrompt) { this.naturalLanguagePrompt = naturalLanguagePrompt; }
  public String getReferenceProductUrl() { return referenceProductUrl; }
  public void setReferenceProductUrl(String referenceProductUrl) { this.referenceProductUrl = referenceProductUrl; }
  public BigDecimal getBaselineMarketPrice() { return baselineMarketPrice; }
  public void setBaselineMarketPrice(BigDecimal baselineMarketPrice) { this.baselineMarketPrice = baselineMarketPrice; }
  public Instant getLastHuntedAt() { return lastHuntedAt; }
  public void setLastHuntedAt(Instant lastHuntedAt) { this.lastHuntedAt = lastHuntedAt; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
