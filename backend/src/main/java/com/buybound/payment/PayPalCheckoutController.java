package com.buybound.payment;

import com.buybound.payment.PayPalCheckoutService.CapturePayPalOrderResponse;
import com.buybound.payment.PayPalCheckoutService.CreatePayPalOrderRequest;
import com.buybound.payment.PayPalCheckoutService.CreatePayPalOrderResponse;
import com.buybound.payment.PayPalCheckoutService.OAuthSessionResult;
import com.buybound.payment.PayPalCheckoutService.PayPalConfigResponse;
import com.buybound.user.service.SessionTokenService;
import com.buybound.watch.dto.ActiveMandateTelemetryResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Real-time PayPal Sandbox OAuth, Vault v3, and Orders v2 Checkout endpoints (/api/paypal).
 * Client Secret stays strictly on the backend and is never exposed via API responses.
 */
@RestController
@RequestMapping("/api/paypal")
public class PayPalCheckoutController {
  private final PayPalCheckoutService service;
  private final ObjectProvider<SessionTokenService> sessionTokens;

  public PayPalCheckoutController(PayPalCheckoutService service) {
    this(service, null);
  }

  @Autowired
  public PayPalCheckoutController(
      PayPalCheckoutService service,
      ObjectProvider<SessionTokenService> sessionTokens) {
    this.service = service;
    this.sessionTokens = sessionTokens;
  }

  @GetMapping("/config")
  public PayPalConfigResponse config() {
    return service.getConfig();
  }

  @PostMapping("/start-login")
  public Map<String, Object> startLogin() {
    return service.startPayPalLogin();
  }

  @PostMapping("/vault-login-callback")
  public ActiveMandateTelemetryResponse vaultLoginCallback(
      @RequestBody Map<String, String> body,
      HttpServletRequest httpReq,
      HttpServletResponse httpRes) {
    String setupTokenId = body != null
        ? (body.get("setupTokenId") != null ? body.get("setupTokenId") : body.get("approval_session_id"))
        : null;
    OAuthSessionResult result = service.vaultLoginCallback(setupTokenId);
    if (result != null) {
      SessionTokenService tokens = sessionTokens != null ? sessionTokens.getIfAvailable() : null;
      if (tokens != null && result.userId() != null) {
        tokens.attachSession(result.userId(), httpReq, httpRes);
      }
      return result.telemetry();
    }
    return null;
  }

  @PostMapping("/oauth-callback")
  public ActiveMandateTelemetryResponse oauthCallback(
      @RequestBody Map<String, String> body,
      HttpServletRequest httpReq,
      HttpServletResponse httpRes) {
    String code = body != null ? body.get("code") : null;
    OAuthSessionResult result = service.oauthCallbackWithSession(code);
    if (result != null) {
      SessionTokenService tokens = sessionTokens != null ? sessionTokens.getIfAvailable() : null;
      if (tokens != null && result.userId() != null) {
        tokens.attachSession(result.userId(), httpReq, httpRes);
      }
      return result.telemetry();
    }
    return service.oauthCallback(code);
  }

  @PostMapping("/create-order")
  public CreatePayPalOrderResponse createOrder(@Valid @RequestBody CreatePayPalOrderRequest request) {
    return service.createOrder(request);
  }

  @PostMapping("/capture-order/{paypalOrderId}")
  public CapturePayPalOrderResponse captureOrder(
      @PathVariable String paypalOrderId,
      @RequestHeader(value = "PayPal-Mock-Response", required = false) String mockResponseHeader,
      @RequestBody(required = false) Map<String, String> body) {
    String resolvedMock = mockResponseHeader != null && !mockResponseHeader.isBlank()
        ? mockResponseHeader
        : (body != null ? body.get("mockResponseCode") : null);
    return service.captureOrder(paypalOrderId, resolvedMock);
  }

  @GetMapping("/orders/{orderId}/verify")
  public PayPalPaymentAdapter.PayPalOrderVerificationResult verifyOrder(@PathVariable String orderId) {
    return service.verifyOrderLive(orderId);
  }
}
