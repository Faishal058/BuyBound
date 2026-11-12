package com.buybound.purchase.dto;

import com.buybound.guardian.domain.Decision;
import com.buybound.guardian.domain.ReasonCode;
import com.buybound.guardian.domain.RuleCheckDetail;
import com.buybound.purchase.domain.GuardianDecisionRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record GuardianDecisionResponse(
    UUID id, UUID intentId, UUID mandateId, int mandateVersion, Decision decision,
    List<ReasonCode> reasonCodes, String policyVersion, String evidenceHash,
    Instant evaluatedAt, Instant expiresAt, List<RuleCheckDetail> ruleChecks) {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static GuardianDecisionResponse of(GuardianDecisionRecord record) {
    List<ReasonCode> codes = Arrays.stream(record.getReasonCodes().split(","))
        .map(String::trim).filter(s -> !s.isEmpty()).map(ReasonCode::valueOf).toList();
    List<RuleCheckDetail> checks = List.of();
    if (record.getRuleChecksJson() != null && !record.getRuleChecksJson().isBlank()) {
      try {
        checks = MAPPER.readValue(record.getRuleChecksJson(), new TypeReference<List<RuleCheckDetail>>() {});
      } catch (Exception ignored) {
        checks = List.of();
      }
    }
    return new GuardianDecisionResponse(
        record.getId(), record.getIntentId(), record.getMandateId(),
        record.getMandateVersion(), record.getDecision(), codes,
        record.getPolicyVersion(), record.getEvidenceHash(),
        record.getEvaluatedAt(), record.getExpiresAt(), checks);
  }
}
