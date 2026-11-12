package com.buybound.guardian.dto;

/**
 * Runtime toggles controlling CISO security guardrails and demo automation mode.
 * All bypass flags default to false so strict CISO guardrails and unit tests pass by default.
 */
public record RuntimeGuardrailSettings(
    boolean demoBypassEnabled,
    boolean strictSellerVerification,
    boolean guardianAutoApproveDemoMode,
    boolean rateLimitDisabled,
    boolean ignoreOvernightWindow) {

  public static RuntimeGuardrailSettings defaults() {
    return new RuntimeGuardrailSettings(false, true, false, false, false);
  }
}

