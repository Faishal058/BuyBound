package com.buybound.watch.repository;

import com.buybound.watch.domain.Watch;
import com.buybound.watch.domain.WatchStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WatchRepository extends JpaRepository<Watch, UUID> {
  List<Watch> findByStatus(WatchStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT w FROM Watch w WHERE w.id = :id")
  Optional<Watch> findByIdForUpdate(@Param("id") UUID id);
}
