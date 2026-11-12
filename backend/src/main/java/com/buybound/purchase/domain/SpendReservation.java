package com.buybound.purchase.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Atomic spend reservation. The UNIQUE constraint on intent_id plus the
 * surrounding transaction is what prevents concurrent workers from
 * over-reserving — never a process-local lock.
 */
@Entity
@Table(name = "spend_reservations")
public class SpendReservation {
  @Id
  private UUID id;

  @Column(name = "intent_id", nullable = false, unique = true)
  private UUID intentId;

  @Column(name = "watch_id", nullable = false)
  private UUID watchId;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ReservationStatus status = ReservationStatus.ACTIVE;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected SpendReservation() {}

  public SpendReservation(UUID intentId, UUID watchId, BigDecimal amount) {
    this.id = UUID.randomUUID();
    this.intentId = intentId;
    this.watchId = watchId;
    this.amount = amount;
    this.createdAt = Instant.now();
  }

  @PrePersist
  void prePersist() {
    if (createdAt == null) createdAt = Instant.now();
  }

  public UUID getId() { return id; }
  public UUID getIntentId() { return intentId; }
  public UUID getWatchId() { return watchId; }
  public BigDecimal getAmount() { return amount; }
  public ReservationStatus getStatus() { return status; }
  public void setStatus(ReservationStatus status) { this.status = status; }
  public Instant getCreatedAt() { return createdAt; }

  public enum ReservationStatus { ACTIVE, RELEASED, SETTLED, COMMITTED }
}
