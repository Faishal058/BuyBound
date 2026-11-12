package com.buybound.ai.dto;

import java.math.BigDecimal;
import java.util.List;

public record ParsedWatchBlueprintResponse(
    String title,
    String query,
    String sku,
    String variant,
    BigDecimal targetPrice,
    BigDecimal maxItemPrice,
    BigDecimal weeklySpendLimit,
    List<String> allowedMerchants,
    String currency,
    boolean blockSubscriptions,
    String aiExplanation) {}

