package com.buybound.watch.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Versioned spending authority. A new version is created whenever limits change;
 * Guardian decisions and intents always reference the exact version they were
 * evaluated against.
 */
@Entity
@Table(name = "mandates")
public class Mandate {
  @Id
  private UUID id;

  @Column(nullable = false)
  private int version;

  @Column(name = "watch_id", nullable = false)
  private UUID watchId;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "max_unit_price", nullable = false, precision = 19, scale = 4)
  private BigDecimal maxUnitPrice;

  @Column(name = "max_total", nullable = false, precision = 19, scale = 4)
  private BigDecimal maxTotal;

  @Column(name = "max_quantity", nullable = false)
  private int maxQuantity;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "mandate_allowed_merchants",
      joinColumns = @JoinColumn(name = "mandate_id"))
  @Column(name = "merchant_id", nullable = false, length = 200)
  private List<String> allowedMerchantIds = new ArrayList<>();

  @Column(name = "max_purchases_per_day", nullable = false)
  private int maxPurchasesPerDay;

  @Column(name = "offer_max_age_seconds", nullable = false)
  private long offerMaxAgeSeconds;

  @Enumerated(EnumType.STRING)
  @Column(name = "new_merchant_behavior", nullable = false, length = 32)
  private NewMerchantBehavior newMerchantBehavior;

  @Column(name = "require_human_approval_above", precision = 19, scale = 4)
  private BigDecimal requireHumanApprovalAbove;

  @Column(name = "max_weekly_spend", precision = 19, scale = 4)
  private BigDecimal maxWeeklySpend;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MandateStatus status = MandateStatus.ACTIVE;

  @Column(name = "paypal_vault_token", length = 128)
  private String paypalVaultToken;

  @Column(name = "paypal_payer_email", length = 255)
  private String paypalPayerEmail;

  @Column(name = "paypal_payer_name", length = 255)
  private String paypalPayerName;

  @Column(name = "paypal_payer_id", length = 128)
  private String paypalPayerId;

  @Column(name = "sandbox_balance", precision = 12, scale = 2)
  private BigDecimal sandboxBalance;

  @Column(name = "min_seller_rating")
  private Double minSellerRating = 4.5;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "revocation_reason", length = 255)
  private String revocationReason;

  protected Mandate() {}

  public Mandate(UUID watchId, int version, String currency, BigDecimal maxUnitPrice,
                 BigDecimal maxTotal, int maxQuantity, List<String> allowedMerchantIds,
                 int maxPurchasesPerDay, long offerMaxAgeSeconds,
                 NewMerchantBehavior newMerchantBehavior, BigDecimal requireHumanApprovalAbove,
                 BigDecimal maxWeeklySpend) {
    this.id = UUID.randomUUID();
    this.watchId = watchId;
    this.version = version;
    this.currency = currency;
    this.maxUnitPrice = maxUnitPrice;
    this.maxTotal = maxTotal;
    this.maxQuantity = maxQuantity;
    this.allowedMerchantIds = new ArrayList<>(allowedMerchantIds);
    this.maxPurchasesPerDay = maxPurchasesPerDay;
    this.offerMaxAgeSeconds = offerMaxAgeSeconds;
    this.newMerchantBehavior = newMerchantBehavior;
    this.requireHumanApprovalAbove = requireHumanApprovalAbove;
    this.maxWeeklySpend = maxWeeklySpend;
    this.createdAt = Instant.now();
  }

  @PrePersist
  void prePersist() {
    if (createdAt == null) createdAt = Instant.now();
  }

  public UUID getId() { return id; }
  public int getVersion() { return version; }
  public UUID getWatchId() { return watchId; }
  public String getCurrency() { return currency; }
  public BigDecimal getMaxUnitPrice() { return maxUnitPrice; }
  public void setMaxUnitPrice(BigDecimal maxUnitPrice) { this.maxUnitPrice = maxUnitPrice; }
  public BigDecimal getMaxTotal() { return maxTotal; }
  public void setMaxTotal(BigDecimal maxTotal) { this.maxTotal = maxTotal; }
  public int getMaxQuantity() { return maxQuantity; }
  public List<String> getAllowedMerchantIds() { return List.copyOf(allowedMerchantIds); }
  public void setAllowedMerchantIds(List<String> merchants) {
    if (this.allowedMerchantIds == null) {
      this.allowedMerchantIds = new java.util.ArrayList<>();
    }
    try {
      this.allowedMerchantIds.clear();
      if (merchants != null) {
        this.allowedMerchantIds.addAll(merchants);
      }
    } catch (UnsupportedOperationException ex) {
      this.allowedMerchantIds = merchants != null
          ? new java.util.ArrayList<>(merchants)
          : new java.util.ArrayList<>();
    }
  }
  public int getMaxPurchasesPerDay() { return maxPurchasesPerDay; }
  public long getOfferMaxAgeSeconds() { return offerMaxAgeSeconds; }
  public NewMerchantBehavior getNewMerchantBehavior() { return newMerchantBehavior; }
  public void setNewMerchantBehavior(NewMerchantBehavior newMerchantBehavior) {
    if (newMerchantBehavior != null) {
      this.newMerchantBehavior = newMerchantBehavior;
    }
  }
  public BigDecimal getRequireHumanApprovalAbove() { return requireHumanApprovalAbove; }
  public void setRequireHumanApprovalAbove(BigDecimal requireHumanApprovalAbove) {
    this.requireHumanApprovalAbove = requireHumanApprovalAbove;
  }
  public BigDecimal getMaxWeeklySpend() { return maxWeeklySpend; }
  public void setMaxWeeklySpend(BigDecimal maxWeeklySpend) { this.maxWeeklySpend = maxWeeklySpend; }
  public MandateStatus getStatus() { return status; }
  public void setStatus(MandateStatus status) { this.status = status; }
  public String getPaypalVaultToken() { return paypalVaultToken; }
  public void setPaypalVaultToken(String paypalVaultToken) { this.paypalVaultToken = paypalVaultToken; }
  public String getPaypalPayerEmail() { return paypalPayerEmail; }
  public void setPaypalPayerEmail(String paypalPayerEmail) { this.paypalPayerEmail = paypalPayerEmail; }
  public String getPaypalPayerName() { return paypalPayerName; }
  public void setPaypalPayerName(String paypalPayerName) { this.paypalPayerName = paypalPayerName; }
  public String getPaypalPayerId() { return paypalPayerId; }
  public void setPaypalPayerId(String paypalPayerId) { this.paypalPayerId = paypalPayerId; }
  public BigDecimal getSandboxBalance() { return sandboxBalance; }
  public void setSandboxBalance(BigDecimal sandboxBalance) { this.sandboxBalance = sandboxBalance; }
  public Double getMinSellerRating() { return minSellerRating != null ? minSellerRating : 4.5; }
  public void setMinSellerRating(Double minSellerRating) { this.minSellerRating = minSellerRating != null ? minSellerRating : 4.5; }
  public BigDecimal deductSandboxBalance(BigDecimal amount) {
    if (sandboxBalance == null) {
      return null;
    }
    BigDecimal delta = amount != null ? amount.max(BigDecimal.ZERO) : BigDecimal.ZERO;
    this.sandboxBalance = sandboxBalance.subtract(delta).max(BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP);
    return this.sandboxBalance;
  }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getRevokedAt() { return revokedAt; }
  public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
  public String getRevocationReason() { return revocationReason; }
  public void setRevocationReason(String revocationReason) { this.revocationReason = revocationReason; }

  public enum NewMerchantBehavior { ALLOW, HOLD, REQUIRE_APPROVAL, BLOCK }
}
