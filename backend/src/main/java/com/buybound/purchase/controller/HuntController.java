package com.buybound.purchase.controller;

import com.buybound.purchase.dto.HuntWatchResponse;
import com.buybound.purchase.dto.RedTeamAttackRequest;
import com.buybound.purchase.dto.RedTeamAttackResponse;
import com.buybound.purchase.service.HuntOrchestratorService;
import com.buybound.purchase.service.OvernightDropScheduler;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HuntController {
  private final HuntOrchestratorService orchestrator;

  @Autowired(required = false)
  private OvernightDropScheduler scheduler;

  public HuntController(HuntOrchestratorService orchestrator) {
    this.orchestrator = orchestrator;
  }

  @PostMapping("/watches/{watchId}/hunt")
  public HuntWatchResponse huntWatch(
      @PathVariable UUID watchId,
      @RequestParam(name = "simulateDrop", defaultValue = "false") boolean simulateDrop) {
    return orchestrator.huntWatch(watchId, simulateDrop);
  }

  @PostMapping("/watches/hunt-now")
  public Map<String, Object> huntNow() {
    int executed = scheduler != null ? scheduler.runPollCycle(true) : 0;
    return Map.of(
        "status", "COMPLETED",
        "watchesEvaluated", executed,
        "message", executed > 0
            ? "Autonomous agent evaluated " + executed + " active watch(es)."
            : "No eligible active uncaptured watches found.");
  }

  @PostMapping("/demo/red-team-attack")
  public RedTeamAttackResponse redTeamAttack(@Valid @RequestBody RedTeamAttackRequest request) {
    return orchestrator.runRedTeamAttack(request);
  }
}
