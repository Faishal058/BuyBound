package com.buybound.purchase.dto;

import com.buybound.purchase.domain.IntentStatus;
import com.buybound.purchase.domain.OfferSnapshot;
import com.buybound.purchase.domain.PurchaseIntent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PurchaseIntentResponse(
    UUID id, UUID watchId, UUID mandateId, int mandateVersion, IntentStatus status,
    String merchantId, String canonicalSku, String variant, int quantity, String currency,
    BigDecimal maxTotal, UUID guardianDecisionId, String policyVersion, String evidenceHash,
    OfferSnapshotDto offerSnapshot, String idempotencyKey, String providerOrderId,
    String paypalAuthorizationId, String paypalCaptureId, String approvalUrl,
    String failureReason, Instant createdAt, Instant updatedAt, String purchaseMode) {

  public PurchaseIntentResponse(
      UUID id, UUID watchId, UUID mandateId, int mandateVersion, IntentStatus status,
      String merchantId, String canonicalSku, String variant, int quantity, String currency,
      BigDecimal maxTotal, UUID guardianDecisionId, String policyVersion, String evidenceHash,
      OfferSnapshotDto offerSnapshot, String idempotencyKey, String providerOrderId,
      String paypalAuthorizationId, String paypalCaptureId, String approvalUrl,
      String failureReason, Instant createdAt, Instant updatedAt) {
    this(id, watchId, mandateId, mandateVersion, status, merchantId, canonicalSku, variant,
        quantity, currency, maxTotal, guardianDecisionId, policyVersion, evidenceHash,
        offerSnapshot, idempotencyKey, providerOrderId, paypalAuthorizationId, paypalCaptureId,
        approvalUrl, failureReason, createdAt, updatedAt, "AI_AGENT");
  }

  public static PurchaseIntentResponse of(PurchaseIntent intent) {
    return new PurchaseIntentResponse(
        intent.getId(), intent.getWatchId(), intent.getMandateId(),
        intent.getMandateVersion(), intent.getStatus(), intent.getMerchantId(),
        intent.getCanonicalSku(), intent.getVariant(), intent.getQuantity(),
        intent.getCurrency(), intent.getMaxTotal(), intent.getGuardianDecisionId(),
        intent.getPolicyVersion(), intent.getEvidenceHash(),
        intent.getOfferSnapshot() != null ? OfferSnapshotDto.of(intent.getOfferSnapshot()) : null,
        intent.getIdempotencyKey(), intent.getProviderOrderId(),
        intent.getPaypalAuthorizationId(), intent.getPaypalCaptureId(), intent.getApprovalUrl(),
        intent.getFailureReason(), intent.getCreatedAt(), intent.getUpdatedAt(),
        intent.getPurchaseMode());
  }

  public record OfferSnapshotDto(
      String merchantId, String canonicalSku, String variant, Integer quantity,
      String currency, BigDecimal unitPrice, BigDecimal totalPrice, Instant observedAt,
      String source, String evidenceHash,
      String channel3ProductId, String productUrl, String imageUrl,
      BigDecimal baselinePrice, BigDecimal verifiedSavings, BigDecimal commissionRate,
      BigDecimal aiMatchConfidence, Boolean aiExactSkuMatch,
      Boolean aiPromptInjectionDetected, Boolean aiSubscriptionTrapDetected,
      String aiForensicsSummary,
      Double sellerRating, Integer sellerReviewCount, Boolean verifiedSellerHistory,
      Integer negativeRedFlagCount, String sellerRedFlagSummary, String estimatedDelivery) {

    public static OfferSnapshotDto of(OfferSnapshot snapshot) {
      return new OfferSnapshotDto(
          snapshot.getMerchantId(), snapshot.getCanonicalSku(), snapshot.getVariant(),
          snapshot.getQuantity(), snapshot.getCurrency(), snapshot.getUnitPrice(),
          snapshot.getTotalPrice(), snapshot.getObservedAt(), snapshot.getSource(),
          snapshot.getEvidenceHash(),
          snapshot.getChannel3ProductId(), snapshot.getProductUrl(), snapshot.getImageUrl(),
          snapshot.getBaselinePrice(), snapshot.getVerifiedSavings(), snapshot.getCommissionRate(),
          snapshot.getAiMatchConfidence(), snapshot.getAiExactSkuMatch(),
          snapshot.getAiPromptInjectionDetected(), snapshot.getAiSubscriptionTrapDetected(),
          snapshot.getAiForensicsSummary(),
          snapshot.getSellerRating(), snapshot.getSellerReviewCount(),
          snapshot.getVerifiedSellerHistory(), snapshot.getNegativeRedFlagCount(),
          snapshot.getSellerRedFlagSummary(), snapshot.getEstimatedDelivery());
    }
  }
}
