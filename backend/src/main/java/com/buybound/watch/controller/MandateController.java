package com.buybound.watch.controller;

import com.buybound.watch.dto.ActiveMandateTelemetryResponse;
import com.buybound.watch.dto.UpdateMandateRequest;
import com.buybound.watch.service.MandateService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mandates")
public class MandateController {
  private final MandateService mandateService;

  public MandateController(MandateService mandateService) {
    this.mandateService = mandateService;
  }

  @GetMapping("/active")
  public ActiveMandateTelemetryResponse active() {
    return mandateService.getActiveTelemetry();
  }

  @PostMapping("/{mandateId}/connect-paypal")
  public Map<String, Object> connectPayPal(@PathVariable UUID mandateId) {
    return mandateService.connectPayPalSetup(mandateId);
  }

  @PostMapping("/{mandateId}/complete-paypal")
  public Map<String, Object> completePayPal(@PathVariable UUID mandateId,
                                            @RequestBody Map<String, String> body) {
    String setupTokenId = body != null ? body.get("setupTokenId") : null;
    return mandateService.completePayPalVault(mandateId, setupTokenId);
  }

  @PostMapping("/{mandateId}/kill-switch")
  public Map<String, Object> killSwitch(@PathVariable UUID mandateId) {
    return mandateService.killSwitch(mandateId);
  }

  @PostMapping("/{mandateId}/restore")
  public Map<String, Object> restore(@PathVariable UUID mandateId) {
    return mandateService.restore(mandateId);
  }

  @PutMapping("/{mandateId}")
  public ActiveMandateTelemetryResponse updateLimits(
      @PathVariable UUID mandateId,
      @Valid @RequestBody UpdateMandateRequest request) {
    return mandateService.updateLimits(mandateId, request);
  }

  @PostMapping("/disconnect-paypal")
  public ActiveMandateTelemetryResponse disconnectPayPal() {
    return mandateService.disconnectPayPal();
  }

  @PostMapping("/reset-reservations")
  public Map<String, Object> resetReservations() {
    return mandateService.resetReservations();
  }
}

