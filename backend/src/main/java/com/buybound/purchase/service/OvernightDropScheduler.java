package com.buybound.purchase.service;

import com.buybound.audit.AuditService;
import com.buybound.guardian.service.RuntimeGuardrailSettingsService;
import com.buybound.purchase.domain.IntentStatus;
import com.buybound.purchase.repository.PurchaseIntentRepository;
import com.buybound.watch.domain.Watch;
import com.buybound.watch.domain.WatchStatus;
import com.buybound.watch.repository.WatchRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Unattended autonomous overnight price-drop poller.
 * Evaluates active watches against live discovery + Guardian guardrails on a fixed schedule
 * when enabled via {@code buybound.poller.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "buybound.poller.enabled", havingValue = "true", matchIfMissing = true)
public class OvernightDropScheduler {
  private static final Logger log = LoggerFactory.getLogger(OvernightDropScheduler.class);

  private final WatchRepository watches;
  private final PurchaseIntentRepository intents;
  private final HuntOrchestratorService huntOrchestrator;
  private final AuditService audit;

  @Autowired(required = false)
  private RuntimeGuardrailSettingsService runtimeSettings;

  public OvernightDropScheduler(
      WatchRepository watches,
      PurchaseIntentRepository intents,
      HuntOrchestratorService huntOrchestrator,
      AuditService audit) {
    this.watches = watches;
    this.intents = intents;
    this.huntOrchestrator = huntOrchestrator;
    this.audit = audit;
  }

  @Scheduled(fixedDelayString = "${buybound.poller.interval-ms:60000}", initialDelay = 15000)
  public void pollActiveWatches() {
    runPollCycle(false);
  }

  public int runPollCycle(boolean manualTrigger) {
    boolean ignoreWindow = manualTrigger
        || (runtimeSettings != null && runtimeSettings.getSettings().ignoreOvernightWindow());
    List<Watch> activeWatches = watches.findByStatus(WatchStatus.ACTIVE);
    if (activeWatches.isEmpty()) {
      return 0;
    }

    int executed = 0;
    for (Watch watch : activeWatches) {
      boolean alreadyCaptured = intents.findByWatchIdOrderByCreatedAtDesc(watch.getId()).stream()
          .anyMatch(i -> i.getStatus() == IntentStatus.CAPTURED);
      if (alreadyCaptured) {
        continue;
      }

      try {
        var result = huntOrchestrator.huntWatch(watch.getId());
        String decisionSummary = result.decision() != null ? result.decision().decision().name() : "NO_MATCH";
        audit.record(
            "AUTONOMOUS_OVERNIGHT_POLL_EXECUTED",
            "watch",
            watch.getId().toString(),
            watch.getId().toString(),
            decisionSummary,
            "1.0",
            "Autonomous overnight poller evaluated " + watch.getProductName()
                + " (ignoreWindow=" + ignoreWindow + ", "
                + result.offersScanned() + " offers scanned -> " + decisionSummary + ").");
        executed++;
      } catch (Exception ex) {
        log.warn("Autonomous overnight poll skipped for watch {}: {}", watch.getId(), ex.getMessage());
      }
    }
    return executed;
  }
}

