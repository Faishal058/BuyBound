package com.buybound.audit;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-events")
public class AuditController {
  private final AuditService service;

  public AuditController(AuditService service) {
    this.service = service;
  }

  @GetMapping
  public List<AuditEventResponse> list(
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) String correlationId) {
    return AuditEventResponse.ofAll(service.list(limit, eventType, correlationId));
  }
}
