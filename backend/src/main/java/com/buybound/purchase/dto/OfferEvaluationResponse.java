package com.buybound.purchase.dto;

import com.buybound.purchase.domain.GuardianDecisionRecord;
import com.buybound.purchase.domain.PurchaseIntent;

public record OfferEvaluationResponse(
    PurchaseIntentResponse intent,
    GuardianDecisionResponse decision) {

  public static OfferEvaluationResponse of(PurchaseIntent intent, GuardianDecisionRecord decision) {
    return new OfferEvaluationResponse(
        PurchaseIntentResponse.of(intent), GuardianDecisionResponse.of(decision));
  }
}
