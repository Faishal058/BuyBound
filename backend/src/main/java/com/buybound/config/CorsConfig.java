package com.buybound.config;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Tightened CORS configuration for local development and Render production deployments.
 * Configurable via CORS_ALLOWED_ORIGINS / APP_CORS_ORIGINS and APP_FRONTEND_URL.
 */
@Configuration
public class CorsConfig {
  @Value("${app.cors.allowed-origins:${CORS_ALLOWED_ORIGINS:${APP_CORS_ORIGINS:http://localhost:5173,http://localhost:3000}}}")
  private String allowedOrigins;

  @Value("${app.frontend-url:${APP_FRONTEND_URL:}}")
  private String frontendUrl;

  @Bean
  public WebMvcConfigurer corsConfigurer() {
    Set<String> resolved = new LinkedHashSet<>();
    for (String raw : allowedOrigins.split(",")) {
      String clean = normalizeOrigin(raw);
      if (!clean.isEmpty()) {
        resolved.add(clean);
      }
    }
    String normalizedFrontend = normalizeOrigin(frontendUrl);
    if (!normalizedFrontend.isEmpty() && !resolved.contains("*")) {
      resolved.add(normalizedFrontend);
    }
    if (resolved.isEmpty()) {
      resolved.add("http://localhost:5173");
      resolved.add("http://localhost:3000");
    }

    boolean wildcard = resolved.contains("*");
    String[] finalPatterns = resolved.toArray(String[]::new);
    return new WebMvcConfigurer() {
      @Override
      public void addCorsMappings(CorsRegistry registry) {
        CorsRegistration reg = registry.addMapping("/api/**")
            .allowedOriginPatterns(finalPatterns)
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("Content-Type", "Authorization", "X-BuyBound-Session", "PayPal-Mock-Response", "X-Requested-With")
            .exposedHeaders("X-BuyBound-Session", "Retry-After")
            .maxAge(3600);
        if (!wildcard) {
          reg.allowCredentials(true);
        }
      }
    };
  }

  private static String normalizeOrigin(String raw) {
    if (raw == null) {
      return "";
    }
    String trimmed = raw.trim();
    if (trimmed.isEmpty() || "*".equals(trimmed)) {
      return trimmed;
    }
    trimmed = trimmed.replaceAll("/+$", "");
    if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
      return "https://" + trimmed;
    }
    return trimmed;
  }
}
