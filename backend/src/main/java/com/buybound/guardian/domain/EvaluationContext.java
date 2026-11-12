package com.buybound.guardian.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Runtime context the evaluator needs but that is not part of the policy or
 * the offer: velocity counters, remaining budget, and optional previous price
 * for drift detection, supplied by the caller (normally loaded from durable
 * state by the purchase service).
 */
public record EvaluationContext(
    int purchasesToday,
    BigDecimal remainingBudget,
    Instant now,
    BigDecimal previousUnitPrice) {

  /** Convenience constructor without price drift detection. */
  public EvaluationContext(int purchasesToday, BigDecimal remainingBudget, Instant now) {
    this(purchasesToday, remainingBudget, now, null);
  }

  public EvaluationContext {
    if (now == null) {
      now = Instant.now();
    }
  }
}

