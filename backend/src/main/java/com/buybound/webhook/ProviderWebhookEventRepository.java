package com.buybound.webhook;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ProviderWebhookEventRepository extends JpaRepository<ProviderWebhookEvent, UUID> {
  Optional<ProviderWebhookEvent> findByProviderEventId(String providerEventId);
}
