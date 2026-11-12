package com.buybound.payment;

/** Result of one provider capture or authorization attempt. Never fabricated — only returned after a live call. */
public record PaymentResult(
    String providerReference,
    Status status,
    boolean simulated,
    String message,
    String approveUrl) {

  public PaymentResult(String providerReference, Status status, boolean simulated, String message) {
    this(providerReference, status, simulated, message, null);
  }

  public enum Status {
    CAPTURED,
    AUTHORIZED,
    PAYER_ACTION_REQUIRED,
    VOIDED,
    FAILED,
    RECONCILIATION_REQUIRED
  }
}
