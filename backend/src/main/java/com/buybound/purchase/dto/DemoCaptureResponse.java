package com.buybound.purchase.dto;

import com.buybound.purchase.domain.IntentStatus;
import java.util.UUID;

public record DemoCaptureResponse(
    UUID intentId,
    IntentStatus status,
    boolean simulated,
    String providerReference,
    String message,
    String paypalOrderId,
    String paypalAuthorizationId,
    String paypalCaptureId,
    String approvalUrl) {

  public DemoCaptureResponse(UUID intentId, IntentStatus status, boolean simulated,
                             String providerReference, String message) {
    this(intentId, status, simulated, providerReference, message, null, null, null, null);
  }
}
