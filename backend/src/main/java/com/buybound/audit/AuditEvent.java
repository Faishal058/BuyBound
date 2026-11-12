package com.buybound.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEvent {
  @Id
  private UUID id;

  @Column(name = "event_type", nullable = false, length = 200)
  private String eventType;

  @Column(name = "entity_type", length = 100)
  private String entityType;

  @Column(name = "entity_id", length = 200)
  private String entityId;

  @Column(name = "correlation_id", nullable = false, length = 200)
  private String correlationId;

  @Column(name = "reason_codes", columnDefinition = "TEXT")
  private String reasonCodes;

  @Column(name = "policy_version", length = 50)
  private String policyVersion;

  @Column(length = 1000)
  private String summary;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  protected AuditEvent() {}

  public AuditEvent(String eventType, String entityType, String entityId, String correlationId,
                    String reasonCodes, String policyVersion, String summary) {
    this.id = UUID.randomUUID();
    this.eventType = eventType;
    this.entityType = entityType;
    this.entityId = entityId;
    this.correlationId = correlationId;
    this.reasonCodes = reasonCodes;
    this.policyVersion = policyVersion;
    this.summary = summary;
  }

  @PrePersist
  void prePersist() {
    occurredAt = Instant.now();
  }

  public UUID getId() { return id; }
  public String getEventType() { return eventType; }
  public String getEntityType() { return entityType; }
  public String getEntityId() { return entityId; }
  public String getCorrelationId() { return correlationId; }
  public String getReasonCodes() { return reasonCodes; }
  public String getPolicyVersion() { return policyVersion; }
  public String getSummary() { return summary; }
  public Instant getOccurredAt() { return occurredAt; }
}
