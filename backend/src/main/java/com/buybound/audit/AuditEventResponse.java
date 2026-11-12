package com.buybound.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuditEventResponse(
    UUID id, String eventType, String entityType, String entityId, String correlationId,
    String reasonCodes, String policyVersion, String summary, Instant occurredAt) {

  public static AuditEventResponse of(AuditEvent event) {
    return new AuditEventResponse(
        event.getId(), event.getEventType(), event.getEntityType(), event.getEntityId(),
        event.getCorrelationId(), event.getReasonCodes(), event.getPolicyVersion(),
        event.getSummary(), event.getOccurredAt());
  }

  public static List<AuditEventResponse> ofAll(List<AuditEvent> events) {
    return events.stream().map(AuditEventResponse::of).toList();
  }
}
