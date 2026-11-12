package com.buybound.watch.dto;

import com.buybound.watch.domain.Mandate;
import com.buybound.watch.domain.MandateStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MandateResponse(
    UUID id, int version, UUID watchId, String currency, BigDecimal maxUnitPrice,
    BigDecimal maxTotal, int maxQuantity, List<String> allowedMerchantIds,
    int maxPurchasesPerDay, long offerMaxAgeSeconds, String newMerchantBehavior,
    BigDecimal requireHumanApprovalAbove, BigDecimal maxWeeklySpend,
    MandateStatus status, boolean vaultConnected, String maskedVaultToken, String paypalPayerEmail,
    Instant createdAt, Instant revokedAt, String revocationReason) {

  public static MandateResponse of(Mandate mandate) {
    String rawToken = mandate.getPaypalVaultToken();
    return new MandateResponse(
        mandate.getId(), mandate.getVersion(), mandate.getWatchId(),
        mandate.getCurrency(), mandate.getMaxUnitPrice(), mandate.getMaxTotal(),
        mandate.getMaxQuantity(), mandate.getAllowedMerchantIds(),
        mandate.getMaxPurchasesPerDay(), mandate.getOfferMaxAgeSeconds(),
        mandate.getNewMerchantBehavior().name(), mandate.getRequireHumanApprovalAbove(),
        mandate.getMaxWeeklySpend(), mandate.getStatus(),
        rawToken != null && !rawToken.isBlank(),
        ActiveMandateTelemetryResponse.maskVaultToken(rawToken),
        mandate.getPaypalPayerEmail(),
        mandate.getCreatedAt(), mandate.getRevokedAt(), mandate.getRevocationReason());
  }
}
