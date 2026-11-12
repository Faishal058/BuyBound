package com.buybound.watch.dto;

import com.buybound.watch.domain.MandateStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ActiveMandateTelemetryResponse(
    UUID mandateId,
    UUID watchId,
    MandateStatus status,
    String currency,
    BigDecimal maxWeeklySpend,
    BigDecimal weeklySpent,
    BigDecimal remainingBudget,
    int activeWatchesCount,
    int totalWatchesCount,
    boolean paypalConnected,
    boolean vaultConnected,
    String maskedVaultToken,
    String paypalPayerEmail,
    BigDecimal totalVerifiedSavings,
    long blockedThreatsCount,
    long heldApprovalsCount,
    long capturedCount,
    BigDecimal totalCommissionEarned,
    BigDecimal maxUnitPrice,
    List<String> allowedMerchants,
    BigDecimal sandboxBalance,
    String paypalPayerName,
    String paypalPayerId,
    String newMerchantBehavior) {

  public ActiveMandateTelemetryResponse(
      UUID mandateId,
      UUID watchId,
      MandateStatus status,
      String currency,
      BigDecimal maxWeeklySpend,
      BigDecimal weeklySpent,
      BigDecimal remainingBudget,
      int activeWatchesCount,
      int totalWatchesCount,
      boolean paypalConnected,
      boolean vaultConnected,
      String maskedVaultToken,
      String paypalPayerEmail,
      BigDecimal totalVerifiedSavings,
      long blockedThreatsCount,
      long heldApprovalsCount,
      long capturedCount,
      BigDecimal totalCommissionEarned,
      BigDecimal maxUnitPrice,
      List<String> allowedMerchants,
      BigDecimal sandboxBalance,
      String paypalPayerName,
      String paypalPayerId) {
    this(
        mandateId, watchId, status, currency, maxWeeklySpend, weeklySpent, remainingBudget,
        activeWatchesCount, totalWatchesCount, paypalConnected, vaultConnected, maskedVaultToken,
        paypalPayerEmail, totalVerifiedSavings, blockedThreatsCount, heldApprovalsCount, capturedCount,
        totalCommissionEarned, maxUnitPrice, allowedMerchants, sandboxBalance, paypalPayerName,
        paypalPayerId, "HOLD");
  }

  public ActiveMandateTelemetryResponse(
      UUID mandateId,
      UUID watchId,
      MandateStatus status,
      String currency,
      BigDecimal maxWeeklySpend,
      BigDecimal weeklySpent,
      BigDecimal remainingBudget,
      int activeWatchesCount,
      int totalWatchesCount,
      boolean paypalConnected,
      String rawVaultToken,
      String paypalPayerEmail,
      BigDecimal totalVerifiedSavings,
      long blockedThreatsCount,
      long heldApprovalsCount,
      long capturedCount,
      BigDecimal totalCommissionEarned,
      BigDecimal maxUnitPrice,
      List<String> allowedMerchants,
      BigDecimal sandboxBalance,
      String paypalPayerName,
      String paypalPayerId) {
    this(
        mandateId, watchId, status, currency, maxWeeklySpend, weeklySpent, remainingBudget,
        activeWatchesCount, totalWatchesCount, paypalConnected,
        rawVaultToken != null && !rawVaultToken.isBlank(),
        maskVaultToken(rawVaultToken),
        paypalPayerEmail,
        totalVerifiedSavings, blockedThreatsCount, heldApprovalsCount, capturedCount,
        totalCommissionEarned, maxUnitPrice, allowedMerchants, sandboxBalance, paypalPayerName,
        paypalPayerId, "HOLD");
  }

  public ActiveMandateTelemetryResponse(
      UUID mandateId,
      UUID watchId,
      MandateStatus status,
      String currency,
      BigDecimal maxWeeklySpend,
      BigDecimal weeklySpent,
      BigDecimal remainingBudget,
      int activeWatchesCount,
      int totalWatchesCount,
      boolean paypalConnected,
      String rawVaultToken,
      String paypalPayerEmail,
      BigDecimal totalVerifiedSavings,
      long blockedThreatsCount,
      long heldApprovalsCount,
      long capturedCount,
      BigDecimal totalCommissionEarned,
      BigDecimal maxUnitPrice,
      List<String> allowedMerchants,
      BigDecimal sandboxBalance,
      String paypalPayerName) {
    this(
        mandateId, watchId, status, currency, maxWeeklySpend, weeklySpent, remainingBudget,
        activeWatchesCount, totalWatchesCount, paypalConnected, rawVaultToken, paypalPayerEmail,
        totalVerifiedSavings, blockedThreatsCount, heldApprovalsCount, capturedCount,
        totalCommissionEarned, maxUnitPrice, allowedMerchants, sandboxBalance, paypalPayerName, null);
  }

  public ActiveMandateTelemetryResponse(
      UUID mandateId,
      UUID watchId,
      MandateStatus status,
      String currency,
      BigDecimal maxWeeklySpend,
      BigDecimal weeklySpent,
      BigDecimal remainingBudget,
      int activeWatchesCount,
      int totalWatchesCount,
      boolean paypalConnected,
      String rawVaultToken,
      String paypalPayerEmail,
      BigDecimal totalVerifiedSavings,
      long blockedThreatsCount,
      long heldApprovalsCount,
      long capturedCount,
      BigDecimal totalCommissionEarned,
      BigDecimal maxUnitPrice,
      List<String> allowedMerchants) {
    this(
        mandateId, watchId, status, currency, maxWeeklySpend, weeklySpent, remainingBudget,
        activeWatchesCount, totalWatchesCount, paypalConnected, rawVaultToken, paypalPayerEmail,
        totalVerifiedSavings, blockedThreatsCount, heldApprovalsCount, capturedCount,
        totalCommissionEarned, maxUnitPrice, allowedMerchants,
        new BigDecimal("5000.00"), null, null);
  }

  public static String maskVaultToken(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return null;
    }
    String trimmed = rawToken.trim();
    if (trimmed.startsWith("vlt_••••")) {
      return trimmed;
    }
    String last4 = trimmed.length() <= 4 ? trimmed : trimmed.substring(trimmed.length() - 4);
    return "vlt_••••" + last4;
  }
}
