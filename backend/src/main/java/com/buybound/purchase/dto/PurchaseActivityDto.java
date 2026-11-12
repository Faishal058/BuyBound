package com.buybound.purchase.dto;

import com.buybound.purchase.domain.IntentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Combined real-time PayPal Sandbox and Autonomous Agent Purchase Activity view model.
 */
public record PurchaseActivityDto(
    UUID intentId,
    UUID watchId,
    UUID mandateId,
    Instant timestamp,
    String productName,
    String merchantName,
    String merchantDomain,
    String imageUrl,
    String productUrl,
    String purchaseMode,
    IntentStatus intentStatus,
    String paymentStatusLabel,
    String paypalOrderId,
    String paypalCaptureId,
    String paypalAuthorizationId,
    String approvalUrl,
    String payerEmail,
    String payerId,
    BigDecimal amount,
    String currency,
    BigDecimal updatedSandboxWalletBalance,
    String auditSummary,
    String reasonCodes) {}

