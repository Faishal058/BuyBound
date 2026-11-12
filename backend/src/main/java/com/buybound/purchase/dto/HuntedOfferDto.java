package com.buybound.purchase.dto;

import com.buybound.guardian.domain.Decision;
import com.buybound.guardian.domain.RuleCheckDetail;
import com.buybound.purchase.domain.HuntedOfferRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record HuntedOfferDto(
    UUID id,
    UUID watchId,
    UUID intentId,
    String channel3ProductId,
    String productTitle,
    String merchantId,
    String canonicalSku,
    String currency,
    BigDecimal offerPrice,
    BigDecimal baselinePrice,
    BigDecimal verifiedSavings,
    BigDecimal commissionRate,
    String productUrl,
    String imageUrl,
    String source,
    BigDecimal aiMatchConfidence,
    boolean aiExactSkuMatch,
    boolean aiPromptInjectionDetected,
    boolean aiSubscriptionTrapDetected,
    String aiForensicsSummary,
    Decision guardianDecision,
    List<String> reasonCodes,
    List<RuleCheckDetail> ruleChecks,
    Instant observedAt,
    Instant createdAt,
    Double sellerRating,
    Integer sellerReviewCount,
    Boolean verifiedSellerHistory,
    Integer negativeRedFlagCount,
    String sellerRedFlagSummary,
    String estimatedDelivery) {

  public static HuntedOfferDto of(HuntedOfferRecord r, ObjectMapper mapper) {
    List<String> codes = r.getReasonCodes() != null && !r.getReasonCodes().isBlank()
        ? Arrays.stream(r.getReasonCodes().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()
        : List.of();
    List<RuleCheckDetail> checks = List.of();
    if (r.getRuleChecksJson() != null && !r.getRuleChecksJson().isBlank()) {
      try {
        checks = mapper.readValue(r.getRuleChecksJson(), new TypeReference<List<RuleCheckDetail>>() {});
      } catch (Exception ignored) {
        checks = List.of();
      }
    }
    return new HuntedOfferDto(
        r.getId(), r.getWatchId(), r.getIntentId(), r.getChannel3ProductId(),
        r.getProductTitle(), r.getMerchantId(), r.getCanonicalSku(), r.getCurrency(),
        r.getOfferPrice(), r.getBaselinePrice(), r.getVerifiedSavings(), r.getCommissionRate(),
        r.getProductUrl(), r.getImageUrl(), r.getSource(),
        r.getAiMatchConfidence(), r.isAiExactSkuMatch(), r.isAiPromptInjectionDetected(),
        r.isAiSubscriptionTrapDetected(), r.getAiForensicsSummary(),
        r.getGuardianDecision(), codes, checks, r.getObservedAt(), r.getCreatedAt(),
        r.getSellerRating(), r.getSellerReviewCount(), r.getVerifiedSellerHistory(),
        r.getNegativeRedFlagCount(), r.getSellerRedFlagSummary(), r.getEstimatedDelivery());
  }
}

