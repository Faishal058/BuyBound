package com.buybound.purchase.dto;

import com.buybound.catalog.dto.Channel3ProductOfferDto;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public record InstantBuyRequest(
    @Valid Channel3ProductOfferDto offer,
    UUID mandateId,
    UUID watchId,
    BigDecimal targetPrice,
    String naturalLanguagePrompt,
    Boolean bypassAgent,
    String title,
    BigDecimal price,
    String currency,
    String merchantName,
    String merchantDomain,
    String productUrl,
    String imageUrl,
    String channel3ProductId) {

  public InstantBuyRequest(
      Channel3ProductOfferDto offer,
      UUID mandateId,
      UUID watchId,
      BigDecimal targetPrice,
      String naturalLanguagePrompt) {
    this(offer, mandateId, watchId, targetPrice, naturalLanguagePrompt,
        false, null, null, null, null, null, null, null, null);
  }

  public boolean isBypassAgent() {
    return Boolean.TRUE.equals(bypassAgent);
  }

  public Channel3ProductOfferDto resolvedOffer() {
    if (offer != null) {
      return offer;
    }
    String safeTitle = (title != null && !title.isBlank()) ? title.trim() : "Direct Marketplace Item";
    BigDecimal unitPrice = price != null && price.compareTo(BigDecimal.ZERO) > 0
        ? price.setScale(2, RoundingMode.HALF_UP)
        : new BigDecimal("100.00");
    String safeCurrency = (currency != null && !currency.isBlank())
        ? currency.trim().toUpperCase(Locale.ROOT)
        : "USD";
    String domain = (merchantDomain != null && !merchantDomain.isBlank())
        ? merchantDomain.trim().toLowerCase(Locale.ROOT)
        : ((merchantName != null && !merchantName.isBlank())
            ? merchantName.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.-]+", "") + ".com"
            : "bestbuy.com");
    String mName = (merchantName != null && !merchantName.isBlank()) ? merchantName.trim() : domain;
    String sku = (channel3ProductId != null && !channel3ProductId.isBlank())
        ? channel3ProductId.trim()
        : safeTitle.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    return new Channel3ProductOfferDto(
        sku, safeTitle, safeTitle, null, null, mName, domain,
        unitPrice, unitPrice, BigDecimal.ZERO, BigDecimal.ZERO,
        safeCurrency, productUrl, imageUrl, "IN_STOCK", "new",
        new BigDecimal("3.50"), "direct-human-buy", Instant.now());
  }
}

