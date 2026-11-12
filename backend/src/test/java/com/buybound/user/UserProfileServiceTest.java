package com.buybound.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.buybound.audit.AuditService;
import com.buybound.common.exception.UnauthorizedException;
import com.buybound.purchase.domain.SpendReservation;
import com.buybound.purchase.repository.SpendReservationRepository;
import com.buybound.user.domain.UserProfile;
import com.buybound.user.dto.UserProfileDtos.LoginRequest;
import com.buybound.user.dto.UserProfileDtos.SignupRequest;
import com.buybound.user.dto.UserProfileDtos.UserProfileResponse;
import com.buybound.user.repository.UserProfileRepository;
import com.buybound.user.service.UserProfileService;
import com.buybound.watch.domain.MandateStatus;
import com.buybound.watch.dto.ActiveMandateTelemetryResponse;
import com.buybound.watch.repository.MandateRepository;
import com.buybound.watch.service.MandateService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {
  @Mock private UserProfileRepository profiles;
  @Mock private MandateRepository mandates;
  @Mock private SpendReservationRepository reservations;
  @Mock private MandateService mandateService;
  @Mock private AuditService audit;

  private ActiveMandateTelemetryResponse zeroTelemetry() {
    return new ActiveMandateTelemetryResponse(
        UUID.randomUUID(), UUID.randomUUID(), MandateStatus.ACTIVE, "USD",
        new BigDecimal("500.00"), new BigDecimal("0.00"), new BigDecimal("500.00"),
        3, 3, false, null, null,
        BigDecimal.ZERO, 0, 0, 0, BigDecimal.ZERO,
        new BigDecimal("500.00"), List.of("bestbuy.com"), null, null, null);
  }

  @Test
  void signupHashesPasswordAndLoginRequiresValidRegisteredCredentials() {
    UserProfileService service = new UserProfileService(profiles, mandates, reservations, mandateService, audit);

    when(profiles.findByEmailIgnoreCase("alex@example.com")).thenReturn(Optional.empty());
    when(profiles.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));
    when(reservations.findByStatus(any())).thenReturn(List.of());
    when(mandates.findAll()).thenReturn(List.of());
    when(mandateService.getActiveTelemetry()).thenReturn(zeroTelemetry());

    UserProfileResponse created = service.signup(new SignupRequest(
        "Alex Rivera", "Alex@example.com", "+1 415 555 0111", "SecretPass99",
        null, "USD", new BigDecimal("500.00"), new BigDecimal("300.00")));

    assertThat(created.email()).isEqualTo("alex@example.com");
    assertThat(created.loggedIn()).isTrue();
    assertThat(created.paypalConnected()).isFalse();
    assertThat(created.spentThisWeek()).isEqualByComparingTo("0.00");

    UserProfile stored = new UserProfile(
        "Alex Rivera", "alex@example.com", "+1 415 555 0111",
        UserProfileService.hashPassword("SecretPass99"), false);
    when(profiles.findByEmailIgnoreCase("alex@example.com")).thenReturn(Optional.of(stored));

    UserProfileResponse loggedIn = service.login(
        new LoginRequest("alex@example.com", "SecretPass99", false, null, null));
    assertThat(loggedIn.loggedIn()).isTrue();

    assertThatThrownBy(() -> service.login(
        new LoginRequest("alex@example.com", "WrongPass", false, null, null)))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("Invalid email or password");

    assertThatThrownBy(() -> service.login(
        new LoginRequest("unregistered@example.com", "AnyPass123", false, null, null)))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("Invalid email or password");

    assertThatThrownBy(() -> service.login(
        new LoginRequest("alex@example.com", "", false, null, null)))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("Email address and password are required");
  }

  @Test
  void loginOrConnectFromPayPalOAuthAuthenticatesVerifiedPayerIdentity() {
    UserProfileService service = new UserProfileService(profiles, mandates, reservations, mandateService, audit);

    when(profiles.findFirstByLoggedInTrueOrderByUpdatedAtDesc()).thenReturn(Optional.empty());
    when(profiles.findFirstByPaypalPayerIdOrderByUpdatedAtDesc("PAYER-LIVE-77")).thenReturn(Optional.empty());
    when(profiles.findByEmailIgnoreCase("realbuyer@example.com")).thenReturn(Optional.empty());
    when(profiles.findFirstByPaypalEmailIgnoreCaseOrderByUpdatedAtDesc("realbuyer@example.com"))
        .thenReturn(Optional.empty());
    when(profiles.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));
    when(mandateService.getActiveTelemetry()).thenReturn(new ActiveMandateTelemetryResponse(
        UUID.randomUUID(), UUID.randomUUID(), MandateStatus.ACTIVE, "USD",
        new BigDecimal("500.00"), BigDecimal.ZERO, new BigDecimal("500.00"),
        3, 3, true, null, "realbuyer@example.com",
        BigDecimal.ZERO, 0, 0, 0, BigDecimal.ZERO,
        new BigDecimal("500.00"), List.of("bestbuy.com"), null, "Real Buyer", "PAYER-LIVE-77"));

    UserProfileResponse res = service.loginOrConnectFromPayPalOAuth(
        "PAYER-LIVE-77", "realbuyer@example.com", "Real Buyer");

    assertThat(res.loggedIn()).isTrue();
    assertThat(res.paypalConnected()).isTrue();
    assertThat(res.paypalPayerId()).isEqualTo("PAYER-LIVE-77");
    assertThat(res.paypalEmail()).isEqualTo("realbuyer@example.com");
    assertThat(res.fullName()).isEqualTo("Real Buyer");
  }

  @Test
  void resetWalletReleasesActiveAndSettledReservationsWithoutFabricatingBalances() {
    UserProfileService service = new UserProfileService(profiles, mandates, reservations, mandateService, audit);
    SpendReservation settled = new SpendReservation(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("379.98"));
    settled.setStatus(SpendReservation.ReservationStatus.SETTLED);

    when(reservations.findByStatus(SpendReservation.ReservationStatus.ACTIVE)).thenReturn(List.of());
    when(reservations.findByStatus(SpendReservation.ReservationStatus.SETTLED)).thenReturn(List.of(settled));
    when(mandates.findAll()).thenReturn(List.of());
    when(profiles.findFirstByLoggedInTrueOrderByUpdatedAtDesc()).thenReturn(Optional.empty());
    when(mandateService.getActiveTelemetry()).thenReturn(zeroTelemetry());

    UserProfileResponse res = service.resetWallet();

    assertThat(settled.getStatus()).isEqualTo(SpendReservation.ReservationStatus.RELEASED);
    assertThat(res.spentThisWeek()).isEqualByComparingTo("0.00");
    assertThat(res.paypalBalance()).isNull();
    verify(reservations).save(settled);
  }

  @Test
  void logoutClearsUserSessionAndPayPalConnectionState() {
    UserProfileService service = new UserProfileService(profiles, mandates, reservations, mandateService, audit);
    UserProfile active = new UserProfile(
        "Alex Rivera", "alex@example.com", "+1 415 555 0111",
        UserProfileService.hashPassword("SecretPass99"), true);
    active.setPaypalEmail("realbuyer@example.com");
    active.setPaypalPayerId("PAYERID-999");

    when(profiles.findByLoggedInTrue()).thenReturn(List.of(active));
    when(profiles.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));
    when(mandateService.getActiveTelemetry()).thenReturn(zeroTelemetry());

    UserProfileResponse res = service.logout();

    assertThat(active.isLoggedIn()).isFalse();
    assertThat(active.getPaypalEmail()).isNull();
    assertThat(active.getPaypalPayerId()).isNull();
    assertThat(res.loggedIn()).isFalse();
    assertThat(res.paypalConnected()).isFalse();
    verify(mandateService).disconnectPayPal();
    verify(profiles).save(active);
  }
}
