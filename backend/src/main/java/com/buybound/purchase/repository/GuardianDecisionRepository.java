package com.buybound.purchase.repository;

import com.buybound.purchase.domain.GuardianDecisionRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuardianDecisionRepository extends JpaRepository<GuardianDecisionRecord, UUID> {
  Optional<GuardianDecisionRecord> findByIntentId(UUID intentId);
}
