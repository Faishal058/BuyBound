package com.buybound.catalog.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record Channel3SearchResponse(
    String query,
    String productTitle,
    String canonicalProductId,
    BigDecimal baselineMarketPrice,
    List<Channel3ProductOfferDto> offers,
    Instant fetchedAt) {}

