package com.buybound.guardian.domain;

import java.time.Instant;
import java.util.List;

/** Deterministic outcome of one Guardian evaluation with full rule-by-rule trace. */
public record GuardianDecisionResult(
    Decision decision,
    List<ReasonCode> reasonCodes,
    String policyVersion,
    Instant evaluatedAt,
    Instant expiresAt,
    List<RuleCheckDetail> ruleChecks) {

  public GuardianDecisionResult(
      Decision decision,
      List<ReasonCode> reasonCodes,
      String policyVersion,
      Instant evaluatedAt,
      Instant expiresAt) {
    this(decision, reasonCodes, policyVersion, evaluatedAt, expiresAt, List.of());
  }
}
