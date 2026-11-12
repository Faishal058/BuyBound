package com.buybound.user.controller;

import com.buybound.user.dto.UserProfileDtos.LoginRequest;
import com.buybound.user.dto.UserProfileDtos.SignupRequest;
import com.buybound.user.dto.UserProfileDtos.UpdateProfileRequest;
import com.buybound.user.dto.UserProfileDtos.UserProfileResponse;
import com.buybound.user.service.SessionTokenService;
import com.buybound.user.service.UserProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for shopper authentication, per-client session isolation, profile management, and clean wallet resets.
 */
@RestController
@RequestMapping("/api/user")
public class UserProfileController {
  private final UserProfileService service;
  private final ObjectProvider<SessionTokenService> sessionTokens;

  public UserProfileController(UserProfileService service) {
    this(service, null);
  }

  @Autowired
  public UserProfileController(
      UserProfileService service,
      ObjectProvider<SessionTokenService> sessionTokens) {
    this.service = service;
    this.sessionTokens = sessionTokens;
  }

  @GetMapping("/profile")
  public UserProfileResponse profile(HttpServletRequest httpReq) {
    Optional<UUID> sessionUserId = resolveSessionUser(httpReq);
    if (sessionUserId.isPresent()) {
      return service.getProfile(sessionUserId.get());
    }
    return service.getProfile();
  }

  @PostMapping("/login")
  public UserProfileResponse login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpReq,
      HttpServletResponse httpRes) {
    UserProfileResponse response = service.login(request);
    return attachSession(response, httpReq, httpRes);
  }

  @PostMapping("/signup")
  public UserProfileResponse signup(
      @Valid @RequestBody SignupRequest request,
      HttpServletRequest httpReq,
      HttpServletResponse httpRes) {
    UserProfileResponse response = service.signup(request);
    return attachSession(response, httpReq, httpRes);
  }

  @PutMapping("/profile")
  public UserProfileResponse updateProfile(
      @RequestBody UpdateProfileRequest request,
      HttpServletRequest httpReq) {
    Optional<UUID> sessionUserId = resolveSessionUser(httpReq);
    if (sessionUserId.isPresent()) {
      return service.updateProfile(sessionUserId.get(), request);
    }
    return service.updateProfile(request);
  }

  @PostMapping("/reset-wallet")
  public UserProfileResponse resetWallet(HttpServletRequest httpReq) {
    Optional<UUID> sessionUserId = resolveSessionUser(httpReq);
    if (sessionUserId.isPresent()) {
      return service.resetWallet(sessionUserId.get());
    }
    return service.resetWallet();
  }

  @PostMapping("/disconnect-paypal")
  public UserProfileResponse disconnectPayPal(HttpServletRequest httpReq) {
    Optional<UUID> sessionUserId = resolveSessionUser(httpReq);
    if (sessionUserId.isPresent()) {
      return service.disconnectPayPal(sessionUserId.get());
    }
    return service.disconnectPayPal();
  }

  @PostMapping("/logout")
  public UserProfileResponse logout(HttpServletRequest httpReq, HttpServletResponse httpRes) {
    Optional<UUID> sessionUserId = resolveSessionUser(httpReq);
    SessionTokenService tokens = sessionTokens != null ? sessionTokens.getIfAvailable() : null;
    if (tokens != null) {
      tokens.revokeAndClearSession(httpReq, httpRes);
    }
    if (sessionUserId.isPresent()) {
      return service.logout(sessionUserId.get());
    }
    return service.logout();
  }

  private Optional<UUID> resolveSessionUser(HttpServletRequest httpReq) {
    SessionTokenService tokens = sessionTokens != null ? sessionTokens.getIfAvailable() : null;
    return tokens != null ? tokens.resolveUserId(httpReq) : Optional.empty();
  }

  private UserProfileResponse attachSession(
      UserProfileResponse response,
      HttpServletRequest httpReq,
      HttpServletResponse httpRes) {
    SessionTokenService tokens = sessionTokens != null ? sessionTokens.getIfAvailable() : null;
    if (tokens != null && response != null && response.id() != null && response.loggedIn()) {
      String token = tokens.attachSession(response.id(), httpReq, httpRes);
      return response.withSessionToken(token);
    }
    return response;
  }
}

