package com.buybound.health;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, non-sensitive runtime info for the UI settings page and telemetry bar.
 * Never expose secrets here.
 */
@RestController
public class InfoController {
  @Value("${app.payments.mode:sandbox}")
  private String paymentsMode;

  @Value("${app.discovery.mode:channel3}")
  private String discoveryMode;

  @Value("${app.ai.provider:groq}")
  private String aiProvider;

  @Value("${app.ai.model:openai/gpt-oss-120b}")
  private String aiModel;

  @Value("${app.channel3.api-key:}")
  private String channel3Key;

  @Value("${app.ai.api-key:}")
  private String aiKey;

  @GetMapping("/api/info")
  public Map<String, Object> info() {
    return Map.of(
        "name", "buybound",
        "version", "0.5.0",
        "paymentsMode", paymentsMode,
        "discoveryMode", discoveryMode,
        "aiProvider", aiProvider,
        "aiModel", aiModel,
        "channel3Configured", channel3Key != null && !channel3Key.isBlank(),
        "aiConfigured", aiKey != null && !aiKey.isBlank());
  }
}
