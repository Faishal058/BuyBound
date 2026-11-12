package com.buybound.ai.dto;

import java.math.BigDecimal;

public record AiOfferForensicsResult(
    BigDecimal aiMatchConfidence,
    boolean exactSkuMatch,
    boolean promptInjectionDetected,
    boolean subscriptionTrapDetected,
    String aiForensicsSummary,
    String recommendedAction) {}

