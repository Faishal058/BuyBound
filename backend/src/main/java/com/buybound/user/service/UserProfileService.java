package com.buybound.user.service;

import com.buybound.audit.AuditService;
import com.buybound.common.exception.ConflictException;
import com.buybound.common.exception.UnauthorizedException;
import com.buybound.purchase.domain.SpendReservation;
import com.buybound.purchase.repository.SpendReservationRepository;
import com.buybound.user.domain.UserProfile;
import com.buybound.user.dto.UserProfileDtos.LoginRequest;
import com.buybound.user.dto.UserProfileDtos.SignupRequest;
import com.buybound.user.dto.UserProfileDtos.UpdateProfileRequest;
import com.buybound.user.dto.UserProfileDtos.UserProfileResponse;
import com.buybound.user.repository.UserProfileRepository;
import com.buybound.watch.domain.Mandate;
import com.buybound.watch.dto.ActiveMandateTelemetryResponse;
import com.buybound.watch.repository.MandateRepository;
import com.buybound.watch.service.MandateService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages shopper authentication state, BCrypt password login/signup,
 * and real-time PayPal Sandbox OAuth/Checkout identity binding.
 */
@Service
public class UserProfileService {
  private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

  public record SandboxBuyerIdentity(String fullName, String paypalEmail) {}

  private final UserProfileRepository profiles;
  private final MandateRepository mandates;
  private final SpendReservationRepository reservations;
  private final MandateService mandateService;
  private final AuditService audit;

  public UserProfileService(
      UserProfileRepository profiles,
      MandateRepository mandates,
      SpendReservationRepository reservations,
      MandateService mandateService,
      AuditService audit) {
    this.profiles = profiles;
    this.mandates = mandates;
    this.reservations = reservations;
    this.mandateService = mandateService;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public UserProfileResponse getProfile() {
    ActiveMandateTelemetryResponse telemetry = mandateService.getActiveTelemetry();
    return toLoggedOutResponse(telemetry);
  }

  @Transactional(readOnly = true)
  public UserProfileResponse getProfile(UUID userId) {
    ActiveMandateTelemetryResponse telemetry = mandateService.getActiveTelemetry();
    if (userId == null) {
      return toLoggedOutResponse(telemetry);
    }
    return profiles.findById(userId)
        .map(p -> toResponse(p, telemetry, true))
        .orElseGet(() -> toLoggedOutResponse(telemetry));
  }

  @Transactional
  public UserProfileResponse login(LoginRequest req) {
    if (req == null) {
      throw new UnauthorizedException("Email address and password are required.");
    }

    String cleanEmail = normalizeEmail(req.email());
    if (cleanEmail == null || req.password() == null || req.password().isBlank()) {
      throw new UnauthorizedException("Email address and password are required.");
    }

    UserProfile profile = profiles.findByEmailIgnoreCase(cleanEmail)
        .orElseThrow(() -> new UnauthorizedException("Invalid email or password."));

    if (profile.getPasswordHash() == null || !verifyPassword(req.password(), profile.getPasswordHash())) {
      throw new UnauthorizedException("Invalid email or password.");
    }

    if (!isBcryptHash(profile.getPasswordHash())) {
      profile.setPasswordHash(hashPassword(req.password()));
    }

    profile.setLoggedIn(true);
    profiles.save(profile);

    if (profile.getPaypalPayerId() != null && !profile.getPaypalPayerId().isBlank() && !mandates.findAll().isEmpty()) {
      mandateService.syncVerifiedPayPalBuyer(
          profile.getPaypalPayerId(), profile.getPaypalEmail(), profile.getFullName(), null);
    }

    audit.record("user.logged_in", "user", profile.getId().toString(),
        profile.getId().toString(), null, "1.0",
        "Signed in shopper profile " + profile.getId() + ".");

    return toResponse(profile, mandateService.getActiveTelemetry(), true);
  }

  @Transactional
  public UserProfileResponse signup(SignupRequest req) {
    if (req == null) {
      throw new IllegalArgumentException("Signup details are required.");
    }
    String cleanName = trimToNull(req.fullName());
    String cleanEmail = normalizeEmail(req.email());
    String rawPassword = req.password();
    if (cleanName == null) {
      throw new IllegalArgumentException("Full name is required.");
    }
    if (cleanEmail == null || !cleanEmail.contains("@")) {
      throw new IllegalArgumentException("Please provide a valid email address.");
    }
    if (rawPassword == null || rawPassword.length() < 6) {
      throw new IllegalArgumentException("Password must be at least 6 characters.");
    }

    Optional<UserProfile> existingOpt = profiles.findByEmailIgnoreCase(cleanEmail);
    if (existingOpt.isPresent() && existingOpt.get().getPasswordHash() != null) {
      throw new ConflictException("An account with this email already exists. Please sign in instead.");
    }

    UserProfile profile = existingOpt.orElseGet(() ->
        new UserProfile(cleanName, cleanEmail, trimToNull(req.phone()), hashPassword(rawPassword), true));
    profile.setFullName(cleanName);
    profile.setEmail(cleanEmail);
    profile.setPhone(trimToNull(req.phone()));
    profile.setPasswordHash(hashPassword(rawPassword));
    profile.setPaypalEmail(normalizeEmail(req.paypalEmail()));
    profile.setPaypalPayerId(null);
    profile.setLoggedIn(true);

    profiles.save(profile);

    resetSpendState(req.maxWeeklySpend(), req.maxUnitPrice());

    audit.record("user.signed_up", "user", profile.getId().toString(),
        profile.getId().toString(), null, "1.0",
        "Registered BuyBound shopper profile " + profile.getId() + ".");

    return toResponse(profile, mandateService.getActiveTelemetry(), true);
  }

  /**
   * Signs in or links a shopper using verified payer identity returned directly by
   * PayPal Sandbox OAuth (/v1/identity/openidconnect/userinfo).
   */
  @Transactional
  public UserProfileResponse loginOrConnectFromPayPalOAuth(
      String payerId, String payerEmail, String payerName) {
    String cleanPayerId = trimToNull(payerId);
    String cleanEmail = normalizeEmail(payerEmail);
    String cleanName = trimToNull(payerName);
    if (cleanPayerId == null && cleanEmail == null) {
      throw new UnauthorizedException("Verified PayPal Sandbox payer identity is required.");
    }

    Optional<UserProfile> activeOpt = profiles.findFirstByLoggedInTrueOrderByUpdatedAtDesc();
    UserProfile profile;
    if (activeOpt.isPresent()) {
      profile = activeOpt.get();
    } else {
      Optional<UserProfile> existing = cleanPayerId != null
          ? profiles.findFirstByPaypalPayerIdOrderByUpdatedAtDesc(cleanPayerId)
          : Optional.empty();
      if (existing.isEmpty() && cleanEmail != null) {
        existing = profiles.findByEmailIgnoreCase(cleanEmail)
            .or(() -> profiles.findFirstByPaypalEmailIgnoreCaseOrderByUpdatedAtDesc(cleanEmail));
      }
      if (existing.isPresent()) {
        profile = existing.get();
      } else {
        String emailToStore = cleanEmail != null ? cleanEmail : cleanPayerId.toLowerCase(Locale.ROOT) + "@paypal.sandbox";
        String nameToStore = cleanName != null ? cleanName : "PayPal Sandbox Shopper";
        profile = new UserProfile(nameToStore, emailToStore, null, null, true);
      }
    }

    if (cleanPayerId != null) {
      profile.setPaypalPayerId(cleanPayerId);
    }
    if (cleanEmail != null) {
      profile.setPaypalEmail(cleanEmail);
    }
    if (cleanName != null && (profile.getFullName() == null || profile.getFullName().isBlank())) {
      profile.setFullName(cleanName);
    }
    profile.setLoggedIn(true);
    profiles.save(profile);

    audit.record("user.paypal_oauth_authenticated", "user", profile.getId().toString(),
        profile.getId().toString(), null, "1.0",
        "Authenticated shopper via live PayPal Sandbox OAuth (payer "
            + (cleanPayerId != null ? cleanPayerId : "verified") + ").");

    return toResponse(profile, mandateService.getActiveTelemetry(), true);
  }

  @Transactional
  public UserProfileResponse updateProfile(UpdateProfileRequest req) {
    if (req == null) {
      throw new IllegalArgumentException("Profile payload is required.");
    }
    UserProfile profile = profiles.findFirstByLoggedInTrueOrderByUpdatedAtDesc()
        .orElseThrow(() -> new ConflictException("Please sign in to update your profile settings."));
    return applyProfileUpdate(profile, req);
  }

  @Transactional
  public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest req) {
    if (req == null) {
      throw new IllegalArgumentException("Profile payload is required.");
    }
    UserProfile profile = profiles.findById(userId)
        .orElseThrow(() -> new ConflictException("Please sign in to update your profile settings."));
    return applyProfileUpdate(profile, req);
  }

  private UserProfileResponse applyProfileUpdate(UserProfile profile, UpdateProfileRequest req) {

    String nextName = trimToNull(req.fullName());
    if (nextName != null) {
      profile.setFullName(nextName);
    }

    String nextEmail = normalizeEmail(req.email());
    if (nextEmail != null && !nextEmail.equalsIgnoreCase(profile.getEmail())) {
      Optional<UserProfile> duplicate = profiles.findByEmailIgnoreCase(nextEmail);
      if (duplicate.isPresent() && !duplicate.get().getId().equals(profile.getId())) {
        throw new ConflictException("Another account already uses " + nextEmail + ".");
      }
      profile.setEmail(nextEmail);
    }

    if (req.phone() != null) profile.setPhone(trimToNull(req.phone()));
    if (req.shippingStreet() != null) profile.setShippingStreet(trimToNull(req.shippingStreet()));
    if (req.shippingCity() != null) profile.setShippingCity(trimToNull(req.shippingCity()));
    if (req.shippingState() != null) profile.setShippingState(trimToNull(req.shippingState()));
    if (req.shippingPostalCode() != null) profile.setShippingPostalCode(trimToNull(req.shippingPostalCode()));
    if (req.shippingCountry() != null) {
      String country = trimToNull(req.shippingCountry());
      profile.setShippingCountry(country != null ? country : "United States");
    }
    if (req.notifyPriceDrops() != null) profile.setNotifyPriceDrops(req.notifyPriceDrops());
    if (req.notifyHeldSms() != null) profile.setNotifyHeldSms(req.notifyHeldSms());
    if (req.notifyReceiptEmails() != null) profile.setNotifyReceiptEmails(req.notifyReceiptEmails());

    if (req.paypalEmail() != null) {
      String nextPayPalEmail = normalizeEmail(req.paypalEmail());
      if (nextPayPalEmail == null) {
        profile.setPaypalEmail(null);
        profile.setPaypalPayerId(null);
        mandateService.disconnectPayPal();
      } else {
        profile.setPaypalEmail(nextPayPalEmail);
      }
    }

    profiles.save(profile);
    audit.record("user.profile_updated", "user", profile.getId().toString(),
        profile.getId().toString(), null, "1.0",
        "Updated profile settings for shopper " + profile.getId() + ".");

    return toResponse(profile, mandateService.getActiveTelemetry(), true);
  }

  @Transactional
  public UserProfileResponse resetWallet() {
    return resetWallet(null);
  }

  @Transactional
  public UserProfileResponse resetWallet(UUID userId) {
    resetSpendState(null, null);
    String entityId = userId != null
        ? userId.toString()
        : profiles.findFirstByLoggedInTrueOrderByUpdatedAtDesc()
            .map(p -> p.getId().toString())
            .orElse("system");
    audit.record("user.wallet_reset", "wallet", entityId, entityId, null, "1.0",
        "Reset weekly spend reservations to $0.00.");
    return userId != null ? getProfile(userId) : getProfile();
  }

  @Transactional
  public UserProfileResponse disconnectPayPal() {
    mandateService.disconnectPayPal();
    profiles.findFirstByLoggedInTrueOrderByUpdatedAtDesc().ifPresent(p -> {
      p.setPaypalEmail(null);
      p.setPaypalPayerId(null);
      profiles.save(p);
    });
    return getProfile();
  }

  @Transactional
  public UserProfileResponse disconnectPayPal(UUID userId) {
    mandateService.disconnectPayPal();
    if (userId != null) {
      profiles.findById(userId).ifPresent(p -> {
        p.setPaypalEmail(null);
        p.setPaypalPayerId(null);
        profiles.save(p);
      });
    }
    return getProfile(userId);
  }

  @Transactional
  public UserProfileResponse logout() {
    mandateService.disconnectPayPal();
    List<UserProfile> activeSessions = profiles.findByLoggedInTrue();
    for (UserProfile p : activeSessions) {
      p.setLoggedIn(false);
      p.setPaypalEmail(null);
      p.setPaypalPayerId(null);
      profiles.save(p);
      audit.record("user.logged_out", "user", p.getId().toString(),
          p.getId().toString(), null, "1.0", "Signed out shopper profile " + p.getId() + ".");
    }
    return getProfile();
  }

  @Transactional
  public UserProfileResponse logout(UUID userId) {
    if (userId != null) {
      profiles.findById(userId).ifPresent(p -> audit.record(
          "user.logged_out", "user", p.getId().toString(),
          p.getId().toString(), null, "1.0",
          "Signed out isolated browser session for shopper " + p.getId() + "."));
    }
    return toLoggedOutResponse(mandateService.getActiveTelemetry());
  }

  /**
   * Synchronizes the currently signed-in UserProfile with the verified Payer ID,
   * email, and name returned from live PayPal Sandbox OAuth or Orders Capture.
   */
  @Transactional
  public void syncPayPalOAuthLogin(String payerId, String payerEmail, String payerName) {
    recordSandboxCaptureWalletDeduction(null, payerId, payerEmail, payerName);
  }

  /**
   * Synchronously deducts captured PayPal amount from the active shopper's tracked
   * PayPal Sandbox Wallet balance and binds the verified Payer ID/email returned by PayPal.
   */
  @Transactional
  public BigDecimal recordSandboxCaptureWalletDeduction(
      BigDecimal capturedAmount, String payerId, String payerEmail, String payerName) {
    String cleanPayerId = trimToNull(payerId);
    String cleanEmail = normalizeEmail(payerEmail);
    Optional<UserProfile> activeOpt = profiles.findFirstByLoggedInTrueOrderByUpdatedAtDesc();
    if (activeOpt.isEmpty()) {
      activeOpt = profiles.findFirstByOrderByCreatedAtAsc();
    }
    if (activeOpt.isEmpty()) {
      return null;
    }
    UserProfile profile = activeOpt.get();
    if (capturedAmount != null && capturedAmount.compareTo(BigDecimal.ZERO) > 0) {
      profile.deductSandboxWalletBalance(capturedAmount);
    }
    if (cleanPayerId != null) {
      profile.setPaypalPayerId(cleanPayerId);
    }
    if (cleanEmail != null) {
      profile.setPaypalEmail(cleanEmail);
    }
    if (payerName != null && !payerName.isBlank()
        && (profile.getFullName() == null || profile.getFullName().isBlank())) {
      profile.setFullName(payerName.trim());
    }
    profiles.save(profile);
    return profile.getSandboxWalletBalance();
  }

  private void resetSpendState(BigDecimal customWeeklyCap, BigDecimal customUnitCap) {
    List<SpendReservation> active = reservations.findByStatus(SpendReservation.ReservationStatus.ACTIVE);
    List<SpendReservation> settled = reservations.findByStatus(SpendReservation.ReservationStatus.SETTLED);
    for (SpendReservation r : active) {
      r.setStatus(SpendReservation.ReservationStatus.RELEASED);
      reservations.save(r);
    }
    for (SpendReservation r : settled) {
      r.setStatus(SpendReservation.ReservationStatus.RELEASED);
      reservations.save(r);
    }

    List<Mandate> allMandates = mandates.findAll();
    for (Mandate m : allMandates) {
      if (customWeeklyCap != null && customWeeklyCap.compareTo(BigDecimal.ZERO) > 0) {
        m.setMaxWeeklySpend(customWeeklyCap.setScale(2, RoundingMode.HALF_UP));
      }
      if (customUnitCap != null && customUnitCap.compareTo(BigDecimal.ZERO) > 0) {
        m.setMaxUnitPrice(customUnitCap.setScale(2, RoundingMode.HALF_UP));
        m.setMaxTotal(customUnitCap.setScale(2, RoundingMode.HALF_UP));
      }
      mandates.save(m);
    }
  }

  private void clearActiveSessionsExcept(UUID keepId) {
    for (UserProfile other : profiles.findByLoggedInTrue()) {
      if (!other.getId().equals(keepId)) {
        other.setLoggedIn(false);
        profiles.save(other);
      }
    }
  }

  private UserProfileResponse toResponse(
      UserProfile p, ActiveMandateTelemetryResponse t, boolean isLoggedIn) {
    String linkedPayPalEmail = p.getPaypalEmail() != null && !p.getPaypalEmail().isBlank()
        ? p.getPaypalEmail()
        : t.paypalPayerEmail();
    String linkedPayerId = p.getPaypalPayerId() != null && !p.getPaypalPayerId().isBlank()
        ? p.getPaypalPayerId()
        : t.paypalPayerId();
    boolean paypalConnected = t.paypalConnected()
        || (linkedPayerId != null && !linkedPayerId.isBlank());
    return new UserProfileResponse(
        p.getId(),
        p.getFullName(),
        p.getEmail(),
        p.getPhone(),
        p.getShippingStreet(),
        p.getShippingCity(),
        p.getShippingState(),
        p.getShippingPostalCode(),
        p.getShippingCountry(),
        p.isNotifyPriceDrops(),
        p.isNotifyHeldSms(),
        p.isNotifyReceiptEmails(),
        linkedPayPalEmail,
        linkedPayerId,
        isLoggedIn,
        paypalConnected,
        t.sandboxBalance() != null ? t.sandboxBalance() : p.getSandboxWalletBalance(),
        t.maxWeeklySpend(),
        t.maxUnitPrice(),
        t.weeklySpent(),
        t.remainingBudget(),
        t.currency(),
        p.getCreatedAt(),
        p.getUpdatedAt());
  }

  private UserProfileResponse toLoggedOutResponse(ActiveMandateTelemetryResponse t) {
    return new UserProfileResponse(
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        "United States",
        true,
        true,
        true,
        null,
        null,
        false,
        false,
        null,
        t.maxWeeklySpend(),
        t.maxUnitPrice(),
        t.weeklySpent(),
        t.remainingBudget(),
        t.currency(),
        null,
        null);
  }

  public static String hashPassword(String rawPassword) {
    return PASSWORD_ENCODER.encode(rawPassword);
  }

  public static boolean isBcryptHash(String storedHash) {
    return storedHash != null
        && (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$"));
  }

  private static boolean verifyPassword(String rawPassword, String storedHash) {
    if (rawPassword == null || rawPassword.isBlank() || storedHash == null || storedHash.isBlank()) {
      return false;
    }
    if (isBcryptHash(storedHash)) {
      return PASSWORD_ENCODER.matches(rawPassword, storedHash);
    }
    if (!storedHash.contains(":")) {
      return false;
    }
    String[] parts = storedHash.split(":", 2);
    try {
      byte[] salt = Base64.getDecoder().decode(parts[0]);
      byte[] expected = Base64.getDecoder().decode(parts[1]);
      byte[] actual = sha256(salt, rawPassword);
      return MessageDigest.isEqual(expected, actual);
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }

  private static byte[] sha256(byte[] salt, String rawPassword) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update(salt);
      return md.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }

  private static String normalizeEmail(String email) {
    String clean = trimToNull(email);
    return clean != null ? clean.toLowerCase(Locale.ROOT) : null;
  }

  private static String trimToNull(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  @Transactional(readOnly = true)
  public Optional<SandboxBuyerIdentity> findPreferredSandboxBuyer() {
    Optional<UserProfile> candidate = profiles.findFirstByLoggedInTrueOrderByUpdatedAtDesc();
    return candidate.map(p -> new SandboxBuyerIdentity(
        trimToNull(p.getFullName()),
        normalizeEmail(p.getPaypalEmail())));
  }
}
