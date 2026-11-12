package com.buybound.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class UserProfileDtos {
  private UserProfileDtos() {}

  public record UserProfileResponse(
      UUID id,
      String fullName,
      String email,
      String phone,
      String shippingStreet,
      String shippingCity,
      String shippingState,
      String shippingPostalCode,
      String shippingCountry,
      boolean notifyPriceDrops,
      boolean notifyHeldSms,
      boolean notifyReceiptEmails,
      String paypalEmail,
      String paypalPayerId,
      boolean loggedIn,
      boolean paypalConnected,
      BigDecimal paypalBalance,
      BigDecimal weeklyLimit,
      BigDecimal maxItemPrice,
      BigDecimal spentThisWeek,
      BigDecimal remainingWeeklyBudget,
      String currency,
      Instant createdAt,
      Instant updatedAt,
      String sessionToken) {

    public UserProfileResponse(
        UUID id,
        String fullName,
        String email,
        String phone,
        String shippingStreet,
        String shippingCity,
        String shippingState,
        String shippingPostalCode,
        String shippingCountry,
        boolean notifyPriceDrops,
        boolean notifyHeldSms,
        boolean notifyReceiptEmails,
        String paypalEmail,
        String paypalPayerId,
        boolean loggedIn,
        boolean paypalConnected,
        BigDecimal paypalBalance,
        BigDecimal weeklyLimit,
        BigDecimal maxItemPrice,
        BigDecimal spentThisWeek,
        BigDecimal remainingWeeklyBudget,
        String currency,
        Instant createdAt,
        Instant updatedAt) {
      this(id, fullName, email, phone, shippingStreet, shippingCity, shippingState,
          shippingPostalCode, shippingCountry, notifyPriceDrops, notifyHeldSms,
          notifyReceiptEmails, paypalEmail, paypalPayerId, loggedIn, paypalConnected,
          paypalBalance, weeklyLimit, maxItemPrice, spentThisWeek, remainingWeeklyBudget,
          currency, createdAt, updatedAt, null);
    }

    public UserProfileResponse withSessionToken(String token) {
      return new UserProfileResponse(
          id, fullName, email, phone, shippingStreet, shippingCity, shippingState,
          shippingPostalCode, shippingPostalCode != null ? shippingCountry : shippingCountry,
          notifyPriceDrops, notifyHeldSms, notifyReceiptEmails, paypalEmail, paypalPayerId,
          loggedIn, paypalConnected, paypalBalance, weeklyLimit, maxItemPrice,
          spentThisWeek, remainingWeeklyBudget, currency, createdAt, updatedAt, token);
    }
  }

  public record LoginRequest(
      @NotBlank(message = "Email address is required.")
      @Email(message = "Please enter a valid email address.")
      String email,
      @NotBlank(message = "Password is required.")
      String password,
      Boolean viaPayPal,
      String paypalEmail,
      String fullName) {}

  public record SignupRequest(
      @NotBlank(message = "Full name is required.")
      String fullName,
      @NotBlank(message = "Email address is required.")
      @Email(message = "Please enter a valid email address.")
      String email,
      String phone,
      @NotBlank(message = "Password is required.")
      @Size(min = 6, message = "Password must be at least 6 characters.")
      String password,
      String paypalEmail,
      String currency,
      BigDecimal maxWeeklySpend,
      BigDecimal maxUnitPrice) {}

  public record UpdateProfileRequest(
      String fullName,
      String email,
      String phone,
      String shippingStreet,
      String shippingCity,
      String shippingState,
      String shippingPostalCode,
      String shippingCountry,
      Boolean notifyPriceDrops,
      Boolean notifyHeldSms,
      Boolean notifyReceiptEmails,
      String paypalEmail) {}
}
