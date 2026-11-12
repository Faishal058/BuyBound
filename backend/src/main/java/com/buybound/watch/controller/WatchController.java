package com.buybound.watch.controller;

import com.buybound.watch.dto.CreateWatchRequest;
import com.buybound.watch.dto.MandateResponse;
import com.buybound.watch.dto.WatchResponse;
import com.buybound.watch.service.WatchService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/watches")
public class WatchController {
  private final WatchService service;

  public WatchController(WatchService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<WatchResponse> create(@Valid @RequestBody CreateWatchRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
  }

  @GetMapping
  public List<WatchResponse> list() {
    return service.list();
  }

  @GetMapping("/{watchId}")
  public WatchResponse get(@PathVariable UUID watchId) {
    return service.get(watchId);
  }

  @GetMapping("/{watchId}/mandate")
  public MandateResponse mandate(@PathVariable UUID watchId) {
    return service.mandate(watchId);
  }

  @PostMapping("/{watchId}/pause")
  public Map<String, Object> pause(@PathVariable UUID watchId) {
    WatchResponse watch = service.pause(watchId);
    return Map.of("watchId", watch.id().toString(), "status", watch.status().name());
  }

  @PostMapping("/{watchId}/revoke")
  public Map<String, Object> revoke(@PathVariable UUID watchId) {
    WatchResponse watch = service.revoke(watchId);
    return Map.of(
        "watchId", watch.id().toString(),
        "status", watch.status().name(),
        "message", "Future autonomous buying blocked. In-flight orders are reconciled, not promised as cancelled.");
  }

  /**
   * Emergency Kill Switch ("Disable Agent Buying"): immediately revokes all mandates
   * and watches across the system, halting autonomous buying.
   */
  @PostMapping("/kill-switch")
  public Map<String, Object> killSwitch() {
    return service.killSwitch();
  }
}
