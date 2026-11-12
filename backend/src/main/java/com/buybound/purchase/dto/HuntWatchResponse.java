package com.buybound.purchase.dto;

import com.buybound.watch.dto.WatchResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record HuntWatchResponse(
    WatchResponse watch,
    String query,
    BigDecimal baselineMarketPrice,
    int offersScanned,
    List<HuntedOfferDto> evaluatedOffers,
    PurchaseIntentResponse selectedIntent,
    GuardianDecisionResponse decision,
    DemoCaptureResponse paypalExecution,
    Instant huntedAt,
    String summary) {}

