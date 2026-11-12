package com.buybound.purchase.service;

import com.buybound.common.exception.ConflictException;
import com.buybound.guardian.service.RuntimeGuardrailSettingsService;
import com.buybound.purchase.domain.IntentStatus;
import com.buybound.purchase.domain.OfferSnapshot;
import com.buybound.purchase.domain.PurchaseIntent;
import com.buybound.watch.domain.Mandate;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * Deterministic payload integrity and SSRF/URL security validator enforced before
 * any PayPal Sandbox order creation or settlement capture executes.
 */
public final class CheckoutSecurityValidator {
  private CheckoutSecurityValidator() {}

  /**
   * Validates that an untrusted merchant URL is a safe public HTTPS URL and not an SSRF,
   * loopback, cloud-metadata, DNS-rebinding, or script scheme target.
   */
  public static boolean isSafeExternalUrl(String rawUrl, boolean allowLocalCallback) {
    return isSafeExternalUrl(rawUrl, allowLocalCallback, host -> {
      try {
        return InetAddress.getAllByName(host);
      } catch (UnknownHostException ex) {
        return null;
      }
    });
  }

  public static boolean isSafeExternalUrl(
      String rawUrl,
      boolean allowLocalCallback,
      Function<String, InetAddress[]> dnsResolver) {
    if (rawUrl == null || rawUrl.isBlank()) {
      return true;
    }
    String trimmed = rawUrl.trim();
    String lower = trimmed.toLowerCase(Locale.ROOT);
    if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("file:")) {
      return false;
    }
    URI uri;
    try {
      uri = URI.create(trimmed);
    } catch (Exception ex) {
      return false;
    }
    String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
    String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";
    if (host.isBlank()) {
      return false;
    }
    boolean isLocalHost = "localhost".equals(host)
        || host.endsWith(".localhost")
        || host.endsWith(".local")
        || host.endsWith(".localdomain")
        || host.endsWith(".internal")
        || "metadata.google.internal".equals(host)
        || "instance-data".equals(host);
    if (isLocalHost) {
      return (allowLocalCallback || RuntimeGuardrailSettingsService.currentSnapshot().demoBypassEnabled())
          && ("http".equals(scheme) || "https".equals(scheme));
    }
    if (RuntimeGuardrailSettingsService.currentSnapshot().demoBypassEnabled()
        && ("http".equals(scheme) || "https".equals(scheme))) {
      return true;
    }
    if (!"https".equals(scheme)) {
      return false;
    }
    if (isPrivateOrMetadataIp(host)) {
      return false;
    }
    if (dnsResolver != null) {
      InetAddress[] resolved = dnsResolver.apply(host);
      if (resolved != null) {
        for (InetAddress addr : resolved) {
          if (isBlockedInetAddress(addr)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  private static boolean isPrivateOrMetadataIp(String host) {
    String cleanHost = host.startsWith("[") && host.endsWith("]")
        ? host.substring(1, host.length() - 1)
        : host;
    if ("::1".equals(cleanHost) || "0.0.0.0".equals(cleanHost) || cleanHost.startsWith("fe80:")) {
      return true;
    }
    if (!cleanHost.matches("^\\d{1,3}(\\.\\d{1,3}){3}$")) {
      return false;
    }
    try {
      InetAddress addr = InetAddress.getByName(cleanHost);
      return isBlockedInetAddress(addr);
    } catch (Exception ex) {
      return true;
    }
  }

  private static boolean isBlockedInetAddress(InetAddress addr) {
    if (addr == null) {
      return true;
    }
    if (addr.isLoopbackAddress()
        || addr.isAnyLocalAddress()
        || addr.isLinkLocalAddress()
        || addr.isSiteLocalAddress()
        || addr.isMulticastAddress()) {
      return true;
    }
    byte[] bytes = addr.getAddress();
    if (bytes.length == 4) {
      int b0 = bytes[0] & 0xFF;
      int b1 = bytes[1] & 0xFF;
      // 169.254.0.0/16 (cloud metadata), 127.0.0.0/8, 10.0.0.0/8, 100.64.0.0/10 (CGNAT)
      if (b0 == 127 || b0 == 10 || b0 == 0) {
        return true;
      }
      if (b0 == 169 && b1 == 254) {
        return true;
      }
      if (b0 == 172 && b1 >= 16 && b1 <= 31) {
        return true;
      }
      if (b0 == 192 && b1 == 168) {
        return true;
      }
      if (b0 == 100 && b1 >= 64 && b1 <= 127) {
        return true;
      }
    } else if (bytes.length == 16) {
      int firstByte = bytes[0] & 0xFF;
      int secondByte = bytes[1] & 0xFF;
      // fc00::/7 Unique Local IPv6, fe80::/10 Link-Local
      if ((firstByte & 0xFE) == 0xFC) {
        return true;
      }
      if (firstByte == 0xFE && (secondByte & 0xC0) == 0x80) {
        return true;
      }
    }
    return false;
  }

  /**
   * Verifies server-side that a purchase intent's price, currency, merchant ID, SKU,
   * quantity, cryptographic evidence hash, and external URLs match the Guardian-approved
   * snapshot and active mandate before any payment action executes.
   */
  public static void verifyCheckoutPayloadIntegrity(PurchaseIntent intent, Mandate mandate) {
    if (intent == null) {
      throw new ConflictException("Security rejection: purchase intent is missing.");
    }
    if (intent.getStatus() == IntentStatus.CAPTURED) {
      throw new ConflictException("Security rejection: duplicate capture replay blocked for already-settled intent.");
    }
    if (intent.getStatus() == IntentStatus.BLOCKED
        || intent.getStatus() == IntentStatus.CANCELLED
        || intent.getStatus() == IntentStatus.REJECTED_BY_USER) {
      throw new ConflictException("Security rejection: cannot execute payment on " + intent.getStatus() + " intent.");
    }

    if (RuntimeGuardrailSettingsService.currentSnapshot().demoBypassEnabled()) {
      // Demo bypass still rejects replayed/blocked intents above, while allowing flexible sandbox checkout
      if (intent.getMaxTotal() == null || intent.getMaxTotal().compareTo(BigDecimal.ZERO) <= 0) {
        throw new ConflictException("Security rejection: invalid checkout amount.");
      }
      if (intent.getOfferSnapshot() != null && !isSafeExternalUrl(intent.getOfferSnapshot().getProductUrl(), true)) {
        throw new ConflictException("Security rejection: unsafe script/file redirect URL blocked.");
      }
      return;
    }

    OfferSnapshot snap = intent.getOfferSnapshot();
    if (snap == null) {
      throw new ConflictException("Security rejection: missing verified OfferSnapshot on intent.");
    }

    if (intent.getMaxTotal() == null || intent.getMaxTotal().compareTo(BigDecimal.ZERO) <= 0) {
      throw new ConflictException("Security rejection: invalid checkout amount.");
    }
    if (snap.getTotalPrice() == null || intent.getMaxTotal().compareTo(snap.getTotalPrice()) != 0) {
      throw new ConflictException("Security rejection: checkout amount mismatch against verified OfferSnapshot.");
    }
    if (mandate != null && mandate.getMaxTotal() != null
        && intent.getMaxTotal().compareTo(mandate.getMaxTotal()) > 0) {
      throw new ConflictException("Security rejection: checkout amount exceeds active mandate ceiling.");
    }

    if (intent.getCurrency() == null || snap.getCurrency() == null
        || !intent.getCurrency().equalsIgnoreCase(snap.getCurrency())) {
      throw new ConflictException("Security rejection: currency mismatch against OfferSnapshot.");
    }
    if (mandate != null && mandate.getCurrency() != null
        && !mandate.getCurrency().equalsIgnoreCase(intent.getCurrency())) {
      throw new ConflictException("Security rejection: currency mismatch against active mandate.");
    }

    if (intent.getMerchantId() == null || snap.getMerchantId() == null
        || !intent.getMerchantId().equalsIgnoreCase(snap.getMerchantId())) {
      throw new ConflictException("Security rejection: merchant ID mismatch against OfferSnapshot.");
    }
    if (snap.getCanonicalSku() != null && intent.getCanonicalSku() != null
        && !snap.getCanonicalSku().equalsIgnoreCase(intent.getCanonicalSku())) {
      throw new ConflictException("Security rejection: canonical SKU mismatch against OfferSnapshot.");
    }
    if (intent.getQuantity() != snap.getQuantity() || intent.getQuantity() <= 0) {
      throw new ConflictException("Security rejection: quantity mismatch against OfferSnapshot.");
    }

    if (intent.getEvidenceHash() != null && snap.getEvidenceHash() != null) {
      String recomputed = PurchaseService.evidenceHash(
          snap.getMerchantId(),
          snap.getCanonicalSku(),
          snap.getVariant(),
          snap.getQuantity(),
          snap.getCurrency(),
          snap.getUnitPrice(),
          snap.getTotalPrice(),
          snap.getObservedAt());
      if (!Objects.equals(recomputed, snap.getEvidenceHash())
          || !Objects.equals(recomputed, intent.getEvidenceHash())) {
        throw new ConflictException("Security rejection: OfferSnapshot cryptographic evidence hash tampering detected.");
      }
    }

    if (!isSafeExternalUrl(snap.getProductUrl(), false)) {
      throw new ConflictException("Security rejection: untrusted or SSRF-risk merchant redirect URL blocked.");
    }
  }
}

