package com.buybound.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.buybound.common.exception.GlobalExceptionHandler;
import com.buybound.common.exception.UnauthorizedException;
import com.buybound.user.controller.UserProfileController;
import com.buybound.user.dto.UserProfileDtos.UserProfileResponse;
import com.buybound.user.service.UserProfileService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserProfileController.class)
@Import(GlobalExceptionHandler.class)
class UserProfileControllerTest {
  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private UserProfileService service;

  private UserProfileResponse sampleProfile(boolean loggedIn, BigDecimal spent) {
    BigDecimal weekly = new BigDecimal("500.00");
    return new UserProfileResponse(
        loggedIn ? UUID.randomUUID() : null,
        loggedIn ? "Alex Rivera" : null,
        loggedIn ? "alex@example.com" : null,
        loggedIn ? "+1 415 555 0100" : null,
        "742 Evergreen Way",
        "San Francisco",
        "CA",
        "94107",
        "United States",
        true,
        true,
        true,
        loggedIn ? "realbuyer@example.com" : null,
        loggedIn ? "PAYER-123" : null,
        loggedIn,
        loggedIn,
        null,
        weekly,
        new BigDecimal("500.00"),
        spent,
        weekly.subtract(spent),
        "USD",
        Instant.now(),
        Instant.now());
  }

  @Test
  void profileAndAuthEndpointsReturnCleanStateWithoutPasswordOrFakeBalances() throws Exception {
    when(service.getProfile()).thenReturn(sampleProfile(false, BigDecimal.ZERO));

    mockMvc.perform(get("/api/user/profile"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.loggedIn").value(false))
        .andExpect(jsonPath("$.paypalConnected").value(false))
        .andExpect(jsonPath("$.passwordHash").doesNotExist());

    when(service.signup(any())).thenReturn(sampleProfile(true, new BigDecimal("0.00")));
    mockMvc.perform(post("/api/user/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "fullName": "Alex Rivera",
                  "email": "alex@example.com",
                  "phone": "+1 415 555 0100",
                  "password": "StrongPassword123"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.loggedIn").value(true))
        .andExpect(jsonPath("$.spentThisWeek").value(0.00))
        .andExpect(jsonPath("$.passwordHash").doesNotExist());

    when(service.login(any())).thenReturn(sampleProfile(true, new BigDecimal("0.00")));
    mockMvc.perform(post("/api/user/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"alex@example.com","password":"StrongPassword123"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("alex@example.com"));

    // Blank password must be rejected with HTTP 401 Unauthorized
    mockMvc.perform(post("/api/user/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"alex@example.com","password":""}
                """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401));

    // Wrong password must return HTTP 401 Unauthorized
    when(service.login(any())).thenThrow(new UnauthorizedException("Invalid email or password."));
    mockMvc.perform(post("/api/user/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"alex@example.com","password":"WrongPassword"}
                """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Invalid email or password."));

    when(service.updateProfile(any())).thenReturn(sampleProfile(true, new BigDecimal("0.00")));
    mockMvc.perform(put("/api/user/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"fullName":"Alex Rivera","shippingCity":"San Francisco"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.shippingCity").value("San Francisco"));

    when(service.resetWallet()).thenReturn(sampleProfile(true, new BigDecimal("0.00")));
    mockMvc.perform(post("/api/user/reset-wallet"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.spentThisWeek").value(0.00));

    when(service.logout()).thenReturn(sampleProfile(false, new BigDecimal("0.00")));
    mockMvc.perform(post("/api/user/logout"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.loggedIn").value(false));
  }
}
