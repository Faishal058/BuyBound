package com.buybound.purchase.domain;

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

/** Record of one provider payment attempt. Never invented — only persisted after a call. */
@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt {
  @Id
  private UUID id;

  @Column(name = "intent_id", nullable = false)
  private UUID intentId;

  @Column(nullable = false, length = 50)
  private String adapter;

  @Column(name = "provider_reference", length = 200)
  private String providerReference;

  @Column(name = "paypal_order_id", length = 128)
  private String paypalOrderId;

  @Column(name = "paypal_authorization_id", length = 128)
  private String paypalAuthorizationId;

  @Column(name = "paypal_capture_id", length = 128)
  private String paypalCaptureId;

  @Column(name = "payment_intent_type", length = 32)
  private String paymentIntentType;

  @Column(precision = 19, scale = 4)
  private BigDecimal amount;

  @Column(length = 3)
  private String currency;

  @Column(name = "paypal_payer_id", length = 128)
  private String paypalPayerId;

  @Column(nullable = false)
  private boolean simulated;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private IntentStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected PaymentAttempt() {}

  public PaymentAttempt(UUID intentId, String adapter, String providerReference,
                        boolean simulated, IntentStatus status) {
    this.id = UUID.randomUUID();
    this.intentId = intentId;
    this.adapter = adapter;
    this.providerReference = providerReference;
    this.simulated = simulated;
    this.status = status;
    this.createdAt = Instant.now();
  }

  @PrePersist
  void prePersist() {
    if (createdAt == null) createdAt = Instant.now();
  }

  public UUID getId() { return id; }
  public UUID getIntentId() { return intentId; }
  public String getAdapter() { return adapter; }
  public String getProviderReference() { return providerReference; }
  public String getPaypalOrderId() { return paypalOrderId; }
  public void setPaypalOrderId(String paypalOrderId) { this.paypalOrderId = paypalOrderId; }
  public String getPaypalAuthorizationId() { return paypalAuthorizationId; }
  public void setPaypalAuthorizationId(String paypalAuthorizationId) { this.paypalAuthorizationId = paypalAuthorizationId; }
  public String getPaypalCaptureId() { return paypalCaptureId; }
  public void setPaypalCaptureId(String paypalCaptureId) { this.paypalCaptureId = paypalCaptureId; }
  public String getPaymentIntentType() { return paymentIntentType; }
  public void setPaymentIntentType(String paymentIntentType) { this.paymentIntentType = paymentIntentType; }
  public BigDecimal getAmount() { return amount; }
  public void setAmount(BigDecimal amount) { this.amount = amount; }
  public String getCurrency() { return currency; }
  public void setCurrency(String currency) { this.currency = currency; }
  public String getPaypalPayerId() { return paypalPayerId; }
  public void setPaypalPayerId(String paypalPayerId) { this.paypalPayerId = paypalPayerId; }
  public boolean isSimulated() { return simulated; }
  public IntentStatus getStatus() { return status; }
  public Instant getCreatedAt() { return createdAt; }
}
