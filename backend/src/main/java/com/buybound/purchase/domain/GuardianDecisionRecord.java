package com.buybound.purchase.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Persisted Guardian verdict bound to one intent. Immutable after creation. */
@Entity
@Table(name = "guardian_decisions")
public class GuardianDecisionRecord {
  @Id
  private UUID id;

  @Column(name = "intent_id")
  private UUID intentId;

  @Column(name = "mandate_id", nullable = false)
  private UUID mandateId;

  @Column(name = "mandate_version", nullable = false)
  private int mandateVersion;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private com.buybound.guardian.domain.Decision decision;

  @Column(name = "reason_codes", nullable = false, columnDefinition = "TEXT")
  private String reasonCodes;

  @Column(name = "policy_version", nullable = false, length = 50)
  private String policyVersion;

  @Column(name = "evidence_hash", nullable = false, length = 200)
  private String evidenceHash;

  @Column(name = "rule_checks_json", columnDefinition = "TEXT")
  private String ruleChecksJson;

  @Column(name = "evaluated_at", nullable = false)
  private Instant evaluatedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  protected GuardianDecisionRecord() {}

  public GuardianDecisionRecord(UUID intentId, UUID mandateId, int mandateVersion,
                                com.buybound.guardian.domain.Decision decision,
                                String reasonCodes, String policyVersion, String evidenceHash,
                                Instant evaluatedAt, Instant expiresAt) {
    this.id = UUID.randomUUID();
    this.intentId = intentId;
    this.mandateId = mandateId;
    this.mandateVersion = mandateVersion;
    this.decision = decision;
    this.reasonCodes = reasonCodes;
    this.policyVersion = policyVersion;
    this.evidenceHash = evidenceHash;
    this.evaluatedAt = evaluatedAt;
    this.expiresAt = expiresAt;
  }

  public UUID getId() { return id; }
  public UUID getIntentId() { return intentId; }
  public UUID getMandateId() { return mandateId; }
  public int getMandateVersion() { return mandateVersion; }
  public com.buybound.guardian.domain.Decision getDecision() { return decision; }
  public String getReasonCodes() { return reasonCodes; }
  public String getPolicyVersion() { return policyVersion; }
  public String getEvidenceHash() { return evidenceHash; }
  public String getRuleChecksJson() { return ruleChecksJson; }
  public void setRuleChecksJson(String ruleChecksJson) { this.ruleChecksJson = ruleChecksJson; }
  public Instant getEvaluatedAt() { return evaluatedAt; }
  public Instant getExpiresAt() { return expiresAt; }
}
