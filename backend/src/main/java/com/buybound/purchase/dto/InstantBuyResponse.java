package com.buybound.purchase.dto;

import com.buybound.ai.dto.AiOfferForensicsResult;
import com.buybound.watch.dto.WatchResponse;

public record InstantBuyResponse(
    WatchResponse watch,
    HuntedOfferDto offer,
    AiOfferForensicsResult forensics,
    PurchaseIntentResponse intent,
    GuardianDecisionResponse decision,
    DemoCaptureResponse paypalExecution,
    String summary) {}

