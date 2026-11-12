package com.buybound.guardian.dto;

import com.buybound.guardian.domain.Decision;
import com.buybound.guardian.domain.ReasonCode;
import com.buybound.guardian.domain.RuleCheckDetail;
import java.time.Instant;
import java.util.List;

/** Response body for POST /api/guardian/evaluations. */
public record EvaluationResponse(
    Decision decision,
    List<ReasonCode> reasonCodes,
    String policyVersion,
    Instant evaluatedAt,
    Instant expiresAt,
    List<RuleCheckDetail> ruleChecks) {

  public EvaluationResponse(
      Decision decision,
      List<ReasonCode> reasonCodes,
      String policyVersion,
      Instant evaluatedAt,
      Instant expiresAt) {
    this(decision, reasonCodes, policyVersion, evaluatedAt, expiresAt, List.of());
  }
}
