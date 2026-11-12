package com.buybound.config;

import com.buybound.user.domain.UserProfile;
import com.buybound.user.repository.UserProfileRepository;
import com.buybound.watch.domain.Mandate;
import com.buybound.watch.domain.Watch;
import com.buybound.watch.repository.MandateRepository;
import com.buybound.watch.repository.WatchRepository;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds catalog product watches and guardrail policies on an empty database.
 * Never seeds mock/hardcoded user accounts, passwords, or pre-connected PayPal wallets.
 */
@Component
public class DataSeeder implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

  private final WatchRepository watches;
  private final MandateRepository mandates;
  private final UserProfileRepository userProfiles;

  public DataSeeder(
      WatchRepository watches,
      MandateRepository mandates,
      UserProfileRepository userProfiles) {
    this.watches = watches;
    this.mandates = mandates;
    this.userProfiles = userProfiles;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    // Ensure app always starts logged out with zero hardcoded/mock demo accounts or stub balances.
    List<String> legacyMockEmails = List.of(
        "sb-86ikf53178328@personal.example.com",
        "sb-07amf53855029@personal.example.com",
        "shopper@buybound.app");
    for (String mockEmail : legacyMockEmails) {
      userProfiles.findByEmailIgnoreCase(mockEmail).ifPresent(userProfiles::delete);
    }
    for (UserProfile p : userProfiles.findByLoggedInTrue()) {
      p.setLoggedIn(false);
      userProfiles.save(p);
    }

    for (Mandate existingMandate : mandates.findAll()) {
      boolean dirty = false;
      boolean hasVerifiedPayPal =
          (existingMandate.getPaypalVaultToken() != null && !existingMandate.getPaypalVaultToken().isBlank())
              || (existingMandate.getPaypalPayerId() != null && !existingMandate.getPaypalPayerId().isBlank());
      if (!hasVerifiedPayPal
          && (existingMandate.getPaypalPayerEmail() != null || existingMandate.getPaypalPayerName() != null)) {
        existingMandate.setPaypalPayerEmail(null);
        existingMandate.setPaypalPayerName(null);
        dirty = true;
      }
      if (!hasVerifiedPayPal && existingMandate.getSandboxBalance() != null) {
        existingMandate.setSandboxBalance(null);
        dirty = true;
      }
      if (dirty) {
        mandates.save(existingMandate);
      }
    }

    boolean hasFlagshipAj1 = watches.findAll().stream()
        .anyMatch(w -> w.getProductName() != null
            && w.getProductName().toLowerCase(java.util.Locale.ROOT).contains("air jordan 1"));
    if (!hasFlagshipAj1) {
      seedWatch(
          "Nike Air Jordan 1 Retro High OG (Chicago Lost & Found)",
          "NIKE-AJ1-CHIC-OG",
          "Chicago Lost & Found",
          "nike.com",
          new BigDecimal("200.00"),
          new BigDecimal("210.00"),
          new BigDecimal("210.00"),
          new BigDecimal("500.00"),
          new BigDecimal("320.00"),
          "Overnight Drop Watch: Nike Air Jordan 1 Retro High OG ($200 target, $210 hard ceiling, verified >= 4.5★ seller only).",
          List.of("nike.com", "flightclub.com", "bestbuy.com", "target.com", "walmart.com", "macys.com")
      );
    }

    if (watches.count() > 1) {
      return;
    }
    log.info("Seeding initial active product watches and guardrail policies...");

    List<String> trustedMerchants = List.of(
        "macys.com", "bestbuy.com", "target.com", "walmart.com", "adorama.com", "bhphotovideo.com", "6ave.com");

    seedWatch(
        "Sony WH-1000XM5 Wireless Noise Cancelling Headphones",
        "SONY-WH1000XM5",
        "Black",
        "macys.com",
        new BigDecimal("260.00"),
        new BigDecimal("280.00"),
        new BigDecimal("280.00"),
        new BigDecimal("500.00"),
        new BigDecimal("398.00"),
        "Buy Sony WH-1000XM5 headphones under $260. Max $280 per item and $500/week from approved electronics stores.",
        trustedMerchants
    );

    seedWatch(
        "Apple AirPods Pro 2 USB-C",
        "APPLE-APP2-USBC",
        "White",
        "bestbuy.com",
        new BigDecimal("189.00"),
        new BigDecimal("210.00"),
        new BigDecimal("210.00"),
        new BigDecimal("500.00"),
        new BigDecimal("249.00"),
        "Watch Apple AirPods Pro 2nd Gen under $189, never exceed $210, approved stores only.",
        trustedMerchants
    );

    seedWatch(
        "Dyson Airwrap Multi-Styler",
        "DYSON-AIRWRAP",
        "Nickel/Copper",
        "bestbuy.com",
        new BigDecimal("470.00"),
        new BigDecimal("500.00"),
        new BigDecimal("500.00"),
        new BigDecimal("500.00"),
        new BigDecimal("599.99"),
        "Snipe Dyson Airwrap Multi-Styler when price drops below $470 (hard cap $500).",
        trustedMerchants
    );
  }

  private void seedWatch(String name, String sku, String variant, String primaryMerchant,
                         BigDecimal target, BigDecimal maxUnit, BigDecimal maxTotal,
                         BigDecimal weeklyCap, BigDecimal baseline, String nlPrompt,
                         List<String> allowedMerchants) {
    Watch watch = new Watch(name, null, sku, variant, primaryMerchant, "USD", target);
    watch.setNaturalLanguagePrompt(nlPrompt);
    watch.setBaselineMarketPrice(baseline);
    watches.save(watch);

    Mandate mandate = new Mandate(
        watch.getId(), 1, "USD", maxUnit, maxTotal, 1,
        allowedMerchants, 3, 3600L,
        Mandate.NewMerchantBehavior.HOLD, null, weeklyCap);
    mandates.save(mandate);
  }
}
