package com.buybound.guardian.service;

import com.buybound.audit.AuditService;
import com.buybound.guardian.dto.RuntimeGuardrailSettings;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Thread-safe runtime store for security & demo guardrail toggles.
 * Bridges Spring services and static validators cleanly while keeping unit test defaults strict.
 */
@Service
public class RuntimeGuardrailSettingsService {
  private static volatile RuntimeGuardrailSettings staticSnapshot = RuntimeGuardrailSettings.defaults();

  private final AtomicReference<RuntimeGuardrailSettings> current =
      new AtomicReference<>(RuntimeGuardrailSettings.defaults());
  private final ObjectProvider<AuditService> auditProvider;

  public RuntimeGuardrailSettingsService(ObjectProvider<AuditService> auditProvider) {
    this.auditProvider = auditProvider;
    staticSnapshot = current.get();
  }

  public RuntimeGuardrailSettings getSettings() {
    return current.get();
  }

  public RuntimeGuardrailSettings updateSettings(RuntimeGuardrailSettings next) {
    RuntimeGuardrailSettings sanitized = next != null ? next : RuntimeGuardrailSettings.defaults();
    current.set(sanitized);
    staticSnapshot = sanitized;
    AuditService audit = auditProvider != null ? auditProvider.getIfAvailable() : null;
    if (audit != null) {
      audit.record(
          "guardian.runtime_settings_updated",
          "system",
          "runtime-settings",
          "runtime-settings",
          null,
          "1.0",
          String.format(
              "Runtime guardrails updated: demoBypass=%s, strictSeller=%s, autoApprove=%s, rateLimitDisabled=%s, ignoreWindow=%s",
              sanitized.demoBypassEnabled(),
              sanitized.strictSellerVerification(),
              sanitized.guardianAutoApproveDemoMode(),
              sanitized.rateLimitDisabled(),
              sanitized.ignoreOvernightWindow()));
    }
    return sanitized;
  }

  public static RuntimeGuardrailSettings currentSnapshot() {
    return staticSnapshot;
  }

  public static void resetStaticDefaults() {
    staticSnapshot = RuntimeGuardrailSettings.defaults();
  }
}

