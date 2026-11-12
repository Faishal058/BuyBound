package com.buybound.payment;

import com.buybound.purchase.domain.PurchaseIntent;

/**
 * Narrow payment adapter seam. Implementations receive a persisted intent ID's
 * data — never browser-supplied amounts — and must be idempotent on the
 * intent's idempotency key.
 */
public interface PaymentAdapter {
  /** Stable name for audit records, e.g. "stub" or "paypal". */
  String name();

  PaymentResult capture(PurchaseIntent intent);
}
