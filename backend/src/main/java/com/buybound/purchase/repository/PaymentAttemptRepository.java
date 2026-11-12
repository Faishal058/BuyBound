package com.buybound.purchase.repository;

import com.buybound.purchase.domain.PaymentAttempt;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {
  List<PaymentAttempt> findByIntentIdOrderByCreatedAtDesc(UUID intentId);
}
