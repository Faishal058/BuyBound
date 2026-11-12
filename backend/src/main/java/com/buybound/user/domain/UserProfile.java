package com.buybound.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Authenticated shopper profile storing personal contact info, default shipping
 * address, notification preferences, and linked PayPal Sandbox buyer metadata.
 */
@Entity
@Table(name = "user_profiles")
public class UserProfile {
  @Id
  private UUID id;

  @Column(name = "full_name", nullable = false, length = 200)
  private String fullName;

  @Column(nullable = false, unique = true, length = 255)
  private String email;

  @Column(length = 60)
  private String phone;

  @Column(name = "password_hash", length = 255)
  private String passwordHash;

  @Column(name = "shipping_street", length = 255)
  private String shippingStreet;

  @Column(name = "shipping_city", length = 120)
  private String shippingCity;

  @Column(name = "shipping_state", length = 120)
  private String shippingState;

  @Column(name = "shipping_postal_code", length = 40)
  private String shippingPostalCode;

  @Column(name = "shipping_country", length = 100)
  private String shippingCountry = "United States";

  @Column(name = "notify_price_drops", nullable = false)
  private boolean notifyPriceDrops = true;

  @Column(name = "notify_held_sms", nullable = false)
  private boolean notifyHeldSms = true;

  @Column(name = "notify_receipt_emails", nullable = false)
  private boolean notifyReceiptEmails = true;

  @Column(name = "paypal_email", length = 255)
  private String paypalEmail;

  @Column(name = "paypal_payer_id", length = 128)
  private String paypalPayerId;

  @Column(name = "sandbox_wallet_balance", precision = 12, scale = 2)
  private java.math.BigDecimal sandboxWalletBalance = new java.math.BigDecimal("5000.00");

  @Column(name = "logged_in", nullable = false)
  private boolean loggedIn = true;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected UserProfile() {}

  public UserProfile(String fullName, String email, String phone, String passwordHash, boolean loggedIn) {
    this.id = UUID.randomUUID();
    this.fullName = fullName;
    this.email = email;
    this.phone = phone;
    this.passwordHash = passwordHash;
    this.loggedIn = loggedIn;
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
    if (shippingCountry == null || shippingCountry.isBlank()) shippingCountry = "United States";
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public String getFullName() { return fullName; }
  public void setFullName(String fullName) { this.fullName = fullName; }
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public String getPhone() { return phone; }
  public void setPhone(String phone) { this.phone = phone; }
  public String getPasswordHash() { return passwordHash; }
  public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
  public String getShippingStreet() { return shippingStreet; }
  public void setShippingStreet(String shippingStreet) { this.shippingStreet = shippingStreet; }
  public String getShippingCity() { return shippingCity; }
  public void setShippingCity(String shippingCity) { this.shippingCity = shippingCity; }
  public String getShippingState() { return shippingState; }
  public void setShippingState(String shippingState) { this.shippingState = shippingState; }
  public String getShippingPostalCode() { return shippingPostalCode; }
  public void setShippingPostalCode(String shippingPostalCode) { this.shippingPostalCode = shippingPostalCode; }
  public String getShippingCountry() { return shippingCountry; }
  public void setShippingCountry(String shippingCountry) { this.shippingCountry = shippingCountry; }
  public boolean isNotifyPriceDrops() { return notifyPriceDrops; }
  public void setNotifyPriceDrops(boolean notifyPriceDrops) { this.notifyPriceDrops = notifyPriceDrops; }
  public boolean isNotifyHeldSms() { return notifyHeldSms; }
  public void setNotifyHeldSms(boolean notifyHeldSms) { this.notifyHeldSms = notifyHeldSms; }
  public boolean isNotifyReceiptEmails() { return notifyReceiptEmails; }
  public void setNotifyReceiptEmails(boolean notifyReceiptEmails) { this.notifyReceiptEmails = notifyReceiptEmails; }
  public String getPaypalEmail() { return paypalEmail; }
  public void setPaypalEmail(String paypalEmail) { this.paypalEmail = paypalEmail; }
  public String getPaypalPayerId() { return paypalPayerId; }
  public void setPaypalPayerId(String paypalPayerId) { this.paypalPayerId = paypalPayerId; }
  public java.math.BigDecimal getSandboxWalletBalance() {
    return sandboxWalletBalance != null ? sandboxWalletBalance : new java.math.BigDecimal("5000.00");
  }
  public void setSandboxWalletBalance(java.math.BigDecimal sandboxWalletBalance) {
    this.sandboxWalletBalance = sandboxWalletBalance;
  }
  public java.math.BigDecimal deductSandboxWalletBalance(java.math.BigDecimal amount) {
    java.math.BigDecimal current = getSandboxWalletBalance();
    java.math.BigDecimal delta = amount != null ? amount.max(java.math.BigDecimal.ZERO) : java.math.BigDecimal.ZERO;
    this.sandboxWalletBalance = current.subtract(delta).max(java.math.BigDecimal.ZERO)
        .setScale(2, java.math.RoundingMode.HALF_UP);
    return this.sandboxWalletBalance;
  }
  public boolean isLoggedIn() { return loggedIn; }
  public void setLoggedIn(boolean loggedIn) { this.loggedIn = loggedIn; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}

