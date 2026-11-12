package com.buybound.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Aggregated marketplace product card combining competing live merchant offers for one canonical product.
 */
public record MarketplaceProductDto(
    String productId,
    String title,
    String brand,
    String category,
    String description,
    String imageUrl,
    BigDecimal bestPrice,
    BigDecimal baselinePrice,
    BigDecimal bestSavings,
    BigDecimal savingsPercent,
    String bestMerchantName,
    String bestMerchantDomain,
    int storeCount,
    List<Channel3ProductOfferDto> offers) {}

