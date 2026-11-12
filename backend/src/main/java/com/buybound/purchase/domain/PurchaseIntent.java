package com.buybound.purchase.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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

/**
 * Durable purchase intent with an explicit state machine. The payment adapter
 * may act only on an intent whose persisted fields exactly match a current,
 * unexpired Guardian ALLOW bound to the same mandate version, policy version
 * and evidence hash.
 */
@Entity
@Table(name = "purchase_intents")
public class PurchaseIntent {
  @Id
  private UUID id;

  @Column(name = "watch_id", nullable = false)
  private UUID watchId;

  @Column(name = "mandate_id", nullable = false)
  private UUID mandateId;

  @Column(name = "mandate_version", nullable = false)
  private int mandateVersion;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private IntentStatus status = IntentStatus.CREATED;

  @Column(name = "merchant_id", nullable = false, length = 200)
  private String merchantId;

  @Column(name = "canonical_sku", length = 200)
  private String canonicalSku;

  @Column(length = 200)
  private String variant;

  @Column(nullable = false)
  private int quantity;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "max_total", nullable = false, precision = 19, scale = 4)
  private BigDecimal maxTotal;

  @Column(name = "guardian_decision_id")
  private UUID guardianDecisionId;

  @Column(name = "policy_version", length = 50)
  private String policyVersion;

  @Column(name = "evidence_hash", length = 200)
  private String evidenceHash;

  @Embedded
  private OfferSnapshot offerSnapshot;

  @Column(name = "idempotency_key", nullable = false, length = 200, unique = true)
  private String idempotencyKey;

  @Column(name = "provider_order_id", length = 200)
  private String providerOrderId;

  @Column(name = "paypal_authorization_id", length = 128)
  private String paypalAuthorizationId;

  @Column(name = "paypal_capture_id", length = 128)
  private String paypalCaptureId;

  @Column(name = "approval_url", columnDefinition = "TEXT")
  private String approvalUrl;

  @Column(name = "failure_reason", length = 500)
  private String failureReason;

  @Column(name = "purchase_mode", length = 32)
  private String purchaseMode = "AI_AGENT";

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected PurchaseIntent() {}

  public PurchaseIntent(UUID watchId, UUID mandateId, int mandateVersion, String merchantId,
                        String canonicalSku, String variant, int quantity, String currency,
                        BigDecimal maxTotal, String idempotencyKey) {
    this.id = UUID.randomUUID();
    this.watchId = watchId;
    this.mandateId = mandateId;
    this.mandateVersion = mandateVersion;
    this.merchantId = merchantId;
    this.canonicalSku = canonicalSku;
    this.variant = variant;
    this.quantity = quantity;
    this.currency = currency;
    this.maxTotal = maxTotal;
    this.idempotencyKey = idempotencyKey;
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
  public UUID getWatchId() { return watchId; }
  public UUID getMandateId() { return mandateId; }
  public int getMandateVersion() { return mandateVersion; }
  public IntentStatus getStatus() { return status; }
  public void setStatus(IntentStatus status) { this.status = status; }
  public String getMerchantId() { return merchantId; }
  public String getCanonicalSku() { return canonicalSku; }
  public String getVariant() { return variant; }
  public int getQuantity() { return quantity; }
  public String getCurrency() { return currency; }
  public BigDecimal getMaxTotal() { return maxTotal; }
  public UUID getGuardianDecisionId() { return guardianDecisionId; }
  public void setGuardianDecisionId(UUID guardianDecisionId) { this.guardianDecisionId = guardianDecisionId; }
  public String getPolicyVersion() { return policyVersion; }
  public void setPolicyVersion(String policyVersion) { this.policyVersion = policyVersion; }
  public String getEvidenceHash() { return evidenceHash; }
  public void setEvidenceHash(String evidenceHash) { this.evidenceHash = evidenceHash; }
  public OfferSnapshot getOfferSnapshot() { return offerSnapshot; }
  public void setOfferSnapshot(OfferSnapshot offerSnapshot) { this.offerSnapshot = offerSnapshot; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public String getProviderOrderId() { return providerOrderId; }
  public void setProviderOrderId(String providerOrderId) { this.providerOrderId = providerOrderId; }
  public String getPaypalAuthorizationId() { return paypalAuthorizationId; }
  public void setPaypalAuthorizationId(String paypalAuthorizationId) { this.paypalAuthorizationId = paypalAuthorizationId; }
  public String getPaypalCaptureId() { return paypalCaptureId; }
  public void setPaypalCaptureId(String paypalCaptureId) { this.paypalCaptureId = paypalCaptureId; }
  public String getApprovalUrl() { return approvalUrl; }
  public void setApprovalUrl(String approvalUrl) { this.approvalUrl = approvalUrl; }
  public String getFailureReason() { return failureReason; }
  public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
  public String getPurchaseMode() { return purchaseMode != null ? purchaseMode : "AI_AGENT"; }
  public void setPurchaseMode(String purchaseMode) {
    this.purchaseMode = (purchaseMode != null && !purchaseMode.isBlank()) ? purchaseMode : "AI_AGENT";
  }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
