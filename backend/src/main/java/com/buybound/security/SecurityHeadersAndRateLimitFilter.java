package com.buybound.security;

import com.buybound.audit.AuditService;
import com.buybound.guardian.service.RuntimeGuardrailSettingsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Injects OWASP HTTP security headers across every response and enforces an in-memory
 * per-IP token-bucket rate limiter on authentication, AI, and autonomous hunt endpoints.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SecurityHeadersAndRateLimitFilter extends OncePerRequestFilter {

  private record BucketRule(String group, int maxRequestsPerMinute) {}

  private static final class TokenBucket {
    private long windowStartMs;
    private int count;

    TokenBucket(long nowMs) {
      this.windowStartMs = nowMs;
      this.count = 0;
    }

    synchronized boolean tryConsume(int limit, long nowMs) {
      if (nowMs - windowStartMs >= 60_000L) {
        windowStartMs = nowMs;
        count = 0;
      }
      if (count >= limit) {
        return false;
      }
      count++;
      return true;
    }
  }

  private final ObjectProvider<AuditService> auditProvider;
  private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

  @Autowired(required = false)
  private ObjectProvider<RuntimeGuardrailSettingsService> runtimeSettingsProvider;

  public SecurityHeadersAndRateLimitFilter(ObjectProvider<AuditService> auditProvider) {
    this.auditProvider = auditProvider;
  }

  private boolean isRateLimitDisabled() {
    if (runtimeSettingsProvider == null) {
      return false;
    }
    RuntimeGuardrailSettingsService svc = runtimeSettingsProvider.getIfAvailable();
    return svc != null && svc.getSettings().rateLimitDisabled();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {

    applyOwaspHeaders(response);

    if (!"OPTIONS".equalsIgnoreCase(request.getMethod()) && !isRateLimitDisabled()) {
      BucketRule rule = resolveRule(request.getRequestURI());
      if (rule != null) {
        String ip = resolveClientIp(request);
        String key = ip + ":" + rule.group();
        long nowMs = System.currentTimeMillis();
        TokenBucket bucket = buckets.computeIfAbsent(key, ignored -> new TokenBucket(nowMs));
        if (!bucket.tryConsume(rule.maxRequestsPerMinute(), nowMs)) {
          recordRateLimitAudit(rule.group(), ip, request.getRequestURI());
          response.setStatus(429);
          response.setHeader("Retry-After", "60");
          response.setContentType(MediaType.APPLICATION_JSON_VALUE);
          response.getWriter().write(
              "{\"timestamp\":\"" + Instant.now() + "\",\"status\":429,"
                  + "\"error\":\"Too Many Requests\","
                  + "\"message\":\"Rate limit exceeded for " + rule.group() + ". Retry after 60 seconds.\","
                  + "\"path\":\"" + escapeJson(request.getRequestURI()) + "\"}");
          return;
        }
      }
    }

    filterChain.doFilter(request, response);
  }

  private void applyOwaspHeaders(HttpServletResponse response) {
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("X-Frame-Options", "DENY");
    response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
    response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
    response.setHeader("Content-Security-Policy", "default-src 'self' https:; frame-ancestors 'none'");
  }

  private BucketRule resolveRule(String uri) {
    if (uri == null) {
      return null;
    }
    if (uri.startsWith("/api/user/login") || uri.startsWith("/api/user/signup")) {
      return new BucketRule("auth", 15);
    }
    if (uri.startsWith("/api/ai/")) {
      return new BucketRule("ai", 20);
    }
    if ((uri.startsWith("/api/watches/") && uri.endsWith("/hunt"))
        || uri.startsWith("/api/purchase-intents")) {
      return new BucketRule("checkout-hunt", 30);
    }
    return null;
  }

  private String resolveClientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      int comma = forwarded.indexOf(',');
      return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
    }
    String remote = request.getRemoteAddr();
    return (remote != null && !remote.isBlank()) ? remote : "unknown";
  }

  private void recordRateLimitAudit(String group, String ip, String uri) {
    try {
      AuditService audit = auditProvider != null ? auditProvider.getIfAvailable() : null;
      if (audit != null) {
        audit.record(
            "RATE_LIMIT_BLOCKED",
            "security",
            group,
            ip,
            "RATE_LIMIT_EXCEEDED",
            "1.0",
            "Blocked burst traffic on " + uri + " (" + group + " rate limit exceeded).");
      }
    } catch (Exception ignored) {
      // Do not fail 429 emission if audit DB is unavailable during a test slice
    }
  }

  private String escapeJson(String s) {
    return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  public void clearBuckets() {
    buckets.clear();
  }
}

