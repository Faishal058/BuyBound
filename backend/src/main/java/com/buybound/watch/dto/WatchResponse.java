package com.buybound.watch.dto;

import com.buybound.catalog.service.BuiltInMarketplaceCatalog;
import com.buybound.watch.domain.Watch;
import com.buybound.watch.domain.WatchStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WatchResponse(
    UUID id, String productName, String productId, String sku, String variant,
    String merchantId, UUID mandateId, int mandateVersion, String currency,
    BigDecimal targetPrice, WatchStatus status, String naturalLanguagePrompt,
    String referenceProductUrl, BigDecimal baselineMarketPrice, Instant lastHuntedAt,
    Instant createdAt, Instant updatedAt) {

  @JsonProperty("imageUrl")
  public String imageUrl() {
    String lookup = (productName != null && !productName.isBlank()) ? productName : sku;
    return BuiltInMarketplaceCatalog.resolveProductImage(lookup, null);
  }

  /** Backward-compatible 13-arg constructor preserved for existing controller unit tests. */
  public WatchResponse(
      UUID id, String productName, String productId, String sku, String variant,
      String merchantId, UUID mandateId, int mandateVersion, String currency,
      BigDecimal targetPrice, WatchStatus status, Instant createdAt, Instant updatedAt) {
    this(id, productName, productId, sku, variant, merchantId, mandateId, mandateVersion,
        currency, targetPrice, status, null, null, null, null, createdAt, updatedAt);
  }

  public static WatchResponse of(Watch watch, UUID mandateId, int mandateVersion) {
    return new WatchResponse(
        watch.getId(), watch.getProductName(), watch.getProductId(), watch.getSku(),
        watch.getVariant(), watch.getMerchantId(), mandateId, mandateVersion,
        watch.getCurrency(), watch.getTargetPrice(), watch.getStatus(),
        watch.getNaturalLanguagePrompt(), watch.getReferenceProductUrl(),
        watch.getBaselineMarketPrice(), watch.getLastHuntedAt(),
        watch.getCreatedAt(), watch.getUpdatedAt());
  }
}
