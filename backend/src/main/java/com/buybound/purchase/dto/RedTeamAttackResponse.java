package com.buybound.purchase.dto;

import com.buybound.ai.dto.AiOfferForensicsResult;

public record RedTeamAttackResponse(
    String attackType,
    String attackLabel,
    HuntedOfferDto evaluatedOffer,
    AiOfferForensicsResult aiForensics,
    PurchaseIntentResponse intent,
    GuardianDecisionResponse decision,
    String summary) {}

