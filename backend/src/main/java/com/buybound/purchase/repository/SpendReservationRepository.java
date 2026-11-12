package com.buybound.purchase.repository;

import com.buybound.purchase.domain.SpendReservation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SpendReservationRepository extends JpaRepository<SpendReservation, UUID> {
  Optional<SpendReservation> findByIntentId(UUID intentId);

  List<SpendReservation> findByStatus(SpendReservation.ReservationStatus status);

  @Query("SELECT COALESCE(SUM(r.amount), 0) FROM SpendReservation r WHERE r.watchId = :watchId"
      + " AND (r.status = com.buybound.purchase.domain.SpendReservation$ReservationStatus.ACTIVE"
      + "   OR r.status = com.buybound.purchase.domain.SpendReservation$ReservationStatus.SETTLED"
      + "   OR r.status = com.buybound.purchase.domain.SpendReservation$ReservationStatus.COMMITTED)"
      + " AND r.createdAt >= :since")
  BigDecimal sumActiveSince(UUID watchId, Instant since);

  @Query("SELECT COALESCE(SUM(r.amount), 0) FROM SpendReservation r"
      + " WHERE (r.status = com.buybound.purchase.domain.SpendReservation$ReservationStatus.ACTIVE"
      + "   OR r.status = com.buybound.purchase.domain.SpendReservation$ReservationStatus.SETTLED"
      + "   OR r.status = com.buybound.purchase.domain.SpendReservation$ReservationStatus.COMMITTED)"
      + " AND r.createdAt >= :since")
  BigDecimal sumGlobalSpentOrReservedSince(Instant since);
}
