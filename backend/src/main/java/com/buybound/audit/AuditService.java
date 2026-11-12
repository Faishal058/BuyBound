package com.buybound.audit;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Append-only audit trail. Automatically redacts emails, passwords, tokens, and API secrets
 * before persisting so sensitive PII/credentials cannot leak into logs or audit views.
 */
@Service
public class AuditService {
  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("([a-zA-Z0-9._%+-])[a-zA-Z0-9._%+-]*@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})");
  private static final Pattern BEARER_PATTERN =
      Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9._\\-+/=]+");
  private static final Pattern TOKEN_PREFIX_PATTERN =
      Pattern.compile("\\b(sk-[A-Za-z0-9_-]{8,}|gsk_[A-Za-z0-9_-]{8,}|AIza[A-Za-z0-9_-]{10,}|whsec_[A-Za-z0-9_-]{8,}|A21AA[A-Za-z0-9_.-]{10,}|vlt_[A-Za-z0-9_-]{6,})\\b");
  private static final Pattern SECRET_KV_PATTERN =
      Pattern.compile("(?i)(password|passwordHash|clientSecret|client_secret|apiKey|api_key|secret|vaultToken|paypalVaultToken|payment_token|setup_token|vault_id|payerId|paypalPayerId|payer_id|shippingStreet|shipping_street|postalCode|postal_code|shippingPostalCode|zip)\\s*[:=]\\s*[^\\s,;\"']+");
  private static final Pattern PHONE_PATTERN =
      Pattern.compile("(?:\\+?\\d{1,3}[\\s.-])?\\(?\\d{3}\\)?[\\s.-]\\d{3}[\\s.-]\\d{4}\\b");
  private static final Pattern STREET_ADDRESS_PATTERN =
      Pattern.compile("\\b\\d{1,5}\\s+[A-Za-z0-9.\\s]{2,30}\\s+(?:St|Street|Ave|Avenue|Rd|Road|Blvd|Boulevard|Way|Ln|Lane|Dr|Drive|Ct|Court)\\b(?:,\\s*[A-Za-z\\s]{2,20})?(?:,\\s*[A-Z]{2}\\s+\\d{5}(?:-\\d{4})?)?", Pattern.CASE_INSENSITIVE);
  private static final Pattern POSTAL_CODE_PATTERN =
      Pattern.compile("(?:(?<=,\\s)|(?<=\\b[A-Z]{2}\\s))\\d{5}(?:-\\d{4})?\\b|\\b\\d{5}-\\d{4}\\b");

  private final AuditEventRepository repository;

  public AuditService(AuditEventRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public void record(String eventType, String entityType, String entityId, String correlationId,
                     String reasonCodes, String policyVersion, String summary) {
    repository.save(new AuditEvent(
        eventType,
        entityType,
        entityId,
        correlationId,
        reasonCodes,
        policyVersion,
        redactSensitiveData(summary)));
  }

  @Transactional(readOnly = true)
  public List<AuditEvent> list(int limit, String eventType, String correlationId) {
    List<AuditEvent> events = repository.findAll(
        PageRequest.of(0, Math.min(Math.max(limit, 1), 200),
            Sort.by(Sort.Direction.DESC, "occurredAt"))).getContent();
    return events.stream()
        .filter(e -> eventType == null || eventType.isBlank() || eventType.equals(e.getEventType()))
        .filter(e -> correlationId == null || correlationId.isBlank()
            || correlationId.equals(e.getCorrelationId()))
        .toList();
  }

  public static String redactSensitiveData(String raw) {
    if (raw == null || raw.isBlank()) {
      return raw;
    }
    String sanitized = SECRET_KV_PATTERN.matcher(raw).replaceAll("$1=[REDACTED]");
    sanitized = BEARER_PATTERN.matcher(sanitized).replaceAll("$1[REDACTED]");
    sanitized = TOKEN_PREFIX_PATTERN.matcher(sanitized).replaceAll("[REDACTED_TOKEN]");
    sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll("$1***@$2");
    sanitized = STREET_ADDRESS_PATTERN.matcher(sanitized).replaceAll("[REDACTED_ADDRESS]");
    sanitized = POSTAL_CODE_PATTERN.matcher(sanitized).replaceAll("[REDACTED_POSTAL]");
    sanitized = PHONE_PATTERN.matcher(sanitized).replaceAll("[REDACTED_PHONE]");
    return sanitized;
  }
}
