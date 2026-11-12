package com.buybound.purchase.repository;

import com.buybound.purchase.domain.IntentStatus;
import com.buybound.purchase.domain.PurchaseIntent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseIntentRepository extends JpaRepository<PurchaseIntent, UUID> {
  List<PurchaseIntent> findByWatchIdOrderByCreatedAtDesc(UUID watchId);

  List<PurchaseIntent> findAllByOrderByCreatedAtDesc();

  Optional<PurchaseIntent> findFirstByProviderOrderIdOrderByCreatedAtDesc(String providerOrderId);

  long countByWatchIdAndCreatedAtAfterAndStatusNot(UUID watchId, Instant since, IntentStatus status);
}
