package com.buybound.user.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Per-client HMAC-SHA256 session token issuer & validator.
 * Eliminates the global logged_in DB collision so concurrent users/judges have isolated sessions.
 */
@Component
public class SessionTokenService {
  public static final String SESSION_COOKIE_NAME = "buybound_session";
  public static final String SESSION_HEADER_NAME = "X-BuyBound-Session";
  private static final long SESSION_TTL_SECONDS = 60L * 60L * 24L;

  private final byte[] signingKey;
  private final Map<String, Instant> revokedTokenIds = new ConcurrentHashMap<>();

  public SessionTokenService(@Value("${app.session.hmac-secret:}") String configuredSecret) {
    if (configuredSecret != null && !configuredSecret.isBlank()) {
      this.signingKey = configuredSecret.trim().getBytes(StandardCharsets.UTF_8);
    } else {
      byte[] random = new byte[32];
      new SecureRandom().nextBytes(random);
      this.signingKey = random;
    }
  }

  public String issueToken(UUID userId) {
    if (userId == null) {
      return null;
    }
    long expiresAt = Instant.now().plusSeconds(SESSION_TTL_SECONDS).getEpochSecond();
    String jti = UUID.randomUUID().toString();
    String payload = userId + "|" + jti + "|" + expiresAt;
    String encodedPayload = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    String signature = sign(encodedPayload);
    return encodedPayload + "." + signature;
  }

  public Optional<UUID> resolveUserId(HttpServletRequest request) {
    if (request == null) {
      return Optional.empty();
    }
    String rawToken = extractToken(request);
    if (rawToken == null || rawToken.isBlank()) {
      return Optional.empty();
    }
    return verifyToken(rawToken);
  }

  public Optional<UUID> verifyToken(String token) {
    if (token == null || !token.contains(".")) {
      return Optional.empty();
    }
    String[] parts = token.trim().split("\\.", 2);
    if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
      return Optional.empty();
    }
    String expectedSig = sign(parts[0]);
    if (!MessageDigest.isEqual(
        expectedSig.getBytes(StandardCharsets.UTF_8),
        parts[1].getBytes(StandardCharsets.UTF_8))) {
      return Optional.empty();
    }
    try {
      String decoded = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
      String[] fields = decoded.split("\\|", 3);
      if (fields.length != 3) {
        return Optional.empty();
      }
      UUID userId = UUID.fromString(fields[0]);
      String jti = fields[1];
      long exp = Long.parseLong(fields[2]);
      Instant now = Instant.now();
      if (now.getEpochSecond() > exp) {
        return Optional.empty();
      }
      if (revokedTokenIds.containsKey(jti)) {
        return Optional.empty();
      }
      return Optional.of(userId);
    } catch (Exception ex) {
      return Optional.empty();
    }
  }

  public String attachSession(UUID userId, HttpServletRequest request, HttpServletResponse response) {
    String token = issueToken(userId);
    if (token == null || response == null) {
      return token;
    }
    boolean secure = request != null && (request.isSecure()
        || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")));
    ResponseCookie cookie = ResponseCookie.from(SESSION_COOKIE_NAME, token)
        .httpOnly(true)
        .secure(secure)
        .sameSite(secure ? "None" : "Lax")
        .path("/")
        .maxAge(SESSION_TTL_SECONDS)
        .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    response.setHeader(SESSION_HEADER_NAME, token);
    return token;
  }

  public void revokeAndClearSession(HttpServletRequest request, HttpServletResponse response) {
    String token = extractToken(request);
    if (token != null && token.contains(".")) {
      try {
        String encoded = token.split("\\.", 2)[0];
        String decoded = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        String[] fields = decoded.split("\\|", 3);
        if (fields.length == 3) {
          Instant exp = Instant.ofEpochSecond(Long.parseLong(fields[2]));
          revokedTokenIds.put(fields[1], exp);
          Instant now = Instant.now();
          revokedTokenIds.entrySet().removeIf(e -> e.getValue().isBefore(now));
        }
      } catch (Exception ignored) {
        // Ignore malformed token during logout cleanup
      }
    }
    if (response != null) {
      boolean secure = request != null && (request.isSecure()
          || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")));
      ResponseCookie cleared = ResponseCookie.from(SESSION_COOKIE_NAME, "")
          .httpOnly(true)
          .secure(secure)
          .sameSite(secure ? "None" : "Lax")
          .path("/")
          .maxAge(0)
          .build();
      response.addHeader(HttpHeaders.SET_COOKIE, cleared.toString());
      response.setHeader(SESSION_HEADER_NAME, "");
    }
  }

  private String extractToken(HttpServletRequest request) {
    if (request == null) {
      return null;
    }
    String header = request.getHeader(SESSION_HEADER_NAME);
    if (header != null && !header.isBlank()) {
      return header.trim();
    }
    Cookie[] cookies = request.getCookies();
    if (cookies != null) {
      for (Cookie c : cookies) {
        if (SESSION_COOKIE_NAME.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
          return c.getValue().trim();
        }
      }
    }
    return null;
  }

  private String sign(String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
      byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to sign session token", ex);
    }
  }
}

