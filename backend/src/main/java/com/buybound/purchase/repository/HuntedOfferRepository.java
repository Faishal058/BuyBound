package com.buybound.purchase.repository;

import com.buybound.purchase.domain.HuntedOfferRecord;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HuntedOfferRepository extends JpaRepository<HuntedOfferRecord, UUID> {
  List<HuntedOfferRecord> findAllByOrderByCreatedAtDesc();

  List<HuntedOfferRecord> findByWatchIdOrderByCreatedAtDesc(UUID watchId);
}

