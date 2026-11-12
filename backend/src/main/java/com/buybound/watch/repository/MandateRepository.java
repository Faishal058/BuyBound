package com.buybound.watch.repository;

import com.buybound.watch.domain.Mandate;
import com.buybound.watch.domain.MandateStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MandateRepository extends JpaRepository<Mandate, UUID> {
  Optional<Mandate> findByWatchIdAndStatus(UUID watchId, MandateStatus status);

  List<Mandate> findByWatchIdOrderByVersionDesc(UUID watchId);

  List<Mandate> findByStatusOrderByCreatedAtDesc(MandateStatus status);

  Optional<Mandate> findFirstByStatusOrderByCreatedAtDesc(MandateStatus status);

  List<Mandate> findAllByOrderByCreatedAtDesc();
}
