package com.buybound.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_webhook_events")
public class ProviderWebhookEvent {
  @Id
  private UUID id;

  @Column(name = "provider_event_id", nullable = false, unique = true, length = 200)
  private String providerEventId;

  @Column(name = "event_type", nullable = false, length = 200)
  private String eventType;

  @Column(columnDefinition = "TEXT")
  private String payload;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  @Column(nullable = false)
  private boolean processed;

  protected ProviderWebhookEvent() {}

  public ProviderWebhookEvent(String providerEventId, String eventType, String payload) {
    this.id = UUID.randomUUID();
    this.providerEventId = providerEventId;
    this.eventType = eventType;
    this.payload = payload;
  }

  @PrePersist
  void prePersist() {
    receivedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public String getProviderEventId() { return providerEventId; }
  public String getEventType() { return eventType; }
  public boolean isProcessed() { return processed; }
  public void setProcessed(boolean processed) { this.processed = processed; }
}
