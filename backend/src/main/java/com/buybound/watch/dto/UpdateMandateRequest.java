package com.buybound.watch.dto;

import com.buybound.watch.domain.MandateStatus;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;

public record UpdateMandateRequest(
    @Positive BigDecimal maxUnitPrice,
    @Positive BigDecimal maxTotal,
    @Positive BigDecimal maxWeeklySpend,
    BigDecimal requireHumanApprovalAbove,
    List<String> allowedMerchantIds,
    MandateStatus status,
    String newMerchantBehavior) {

  public UpdateMandateRequest(
      BigDecimal maxUnitPrice,
      BigDecimal maxTotal,
      BigDecimal maxWeeklySpend,
      BigDecimal requireHumanApprovalAbove,
      List<String> allowedMerchantIds) {
    this(maxUnitPrice, maxTotal, maxWeeklySpend, requireHumanApprovalAbove, allowedMerchantIds, null, null);
  }
}

