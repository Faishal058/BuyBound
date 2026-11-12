package com.buybound.purchase.controller;

import com.buybound.purchase.dto.DemoCaptureResponse;
import com.buybound.purchase.dto.GuardianDecisionResponse;
import com.buybound.purchase.dto.HuntedOfferDto;
import com.buybound.purchase.dto.InstantBuyRequest;
import com.buybound.purchase.dto.InstantBuyResponse;
import com.buybound.purchase.dto.OfferEvaluationResponse;
import com.buybound.purchase.dto.PurchaseActivityDto;
import com.buybound.purchase.dto.PurchaseIntentResponse;
import com.buybound.purchase.dto.SubmitOfferRequest;
import com.buybound.purchase.service.HuntOrchestratorService;
import com.buybound.purchase.service.PurchaseService;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api")
public class PurchaseController {
  private final PurchaseService service;
  private final HuntOrchestratorService orchestrator;

  public PurchaseController(PurchaseService service, HuntOrchestratorService orchestrator) {
    this.service = service;
    this.orchestrator = orchestrator;
  }

  @PostMapping("/watches/{watchId}/offers")
  public ResponseEntity<OfferEvaluationResponse> submitOffer(
      @PathVariable UUID watchId, @Valid @RequestBody SubmitOfferRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.submitOffer(watchId, request));
  }

  @GetMapping("/watches/{watchId}/purchase-intents")
  public List<PurchaseIntentResponse> intentsForWatch(@PathVariable UUID watchId) {
    return service.listForWatch(watchId);
  }

  @GetMapping("/purchase-intents")
  public List<PurchaseIntentResponse> listAllIntents() {
    return service.listAll();
  }

  @GetMapping("/offers")
  public List<HuntedOfferDto> listAllOffers() {
    return service.listAllOffers();
  }

  @GetMapping("/purchase-intents/{intentId}")
  public PurchaseIntentResponse get(@PathVariable UUID intentId) {
    return service.get(intentId);
  }

  @GetMapping("/purchase-intents/{intentId}/guardian-decision")
  public GuardianDecisionResponse decision(@PathVariable UUID intentId) {
    return service.decision(intentId);
  }

  @PostMapping("/purchase-intents/{intentId}/approve")
  public ResponseEntity<OfferEvaluationResponse> approve(@PathVariable UUID intentId) {
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.approve(intentId));
  }

  @PostMapping("/purchases/{intentId}/approve-hold")
  public DemoCaptureResponse approveHold(@PathVariable UUID intentId) {
    return service.approveHold(intentId);
  }

  @PostMapping("/purchases/{intentId}/reject-hold")
  public DemoCaptureResponse rejectHold(@PathVariable UUID intentId) {
    return service.rejectHold(intentId);
  }

  @PostMapping("/purchases/{intentId}/sync-paypal")
  public DemoCaptureResponse syncPayPal(@PathVariable UUID intentId) {
    return service.syncPayPal(intentId);
  }

  @PostMapping("/purchase-intents/{intentId}/demo-capture")
  public DemoCaptureResponse demoCapture(@PathVariable UUID intentId) {
    return service.demoCapture(intentId);
  }

  @PostMapping("/purchase-intents/{intentId}/capture")
  public DemoCaptureResponse capture(@PathVariable UUID intentId) {
    return service.capture(intentId);
  }

  @PostMapping("/purchases/instant-buy")
  public InstantBuyResponse instantBuy(@Valid @RequestBody InstantBuyRequest request) {
    return orchestrator.instantBuy(request);
  }

  @GetMapping("/purchases/activity")
  public List<PurchaseActivityDto> activity() {
    return service.getActivityFeed();
  }
}
