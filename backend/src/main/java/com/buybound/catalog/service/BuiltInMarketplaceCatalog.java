package com.buybound.catalog.service;

import com.buybound.catalog.dto.Channel3ProductOfferDto;
import com.buybound.catalog.dto.MarketplaceProductDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Built-in multi-category catalog of 44 real-world products across 8 categories,
 * plus a deterministic multi-merchant synthesizer for arbitrary search queries.
 */
public final class BuiltInMarketplaceCatalog {

  public static final List<String> CATEGORIES = List.of(
      "Audio",
      "Phones & Tablets",
      "Laptops",
      "Gaming",
      "Sneakers & Fashion",
      "Home & Kitchen",
      "Watches",
      "Cameras");

  private record SeedOfferSpec(
      String merchantName,
      String merchantDomain,
      double price,
      double commissionPercent,
      String condition,
      String sourceTag,
      double sellerRating,
      int sellerReviewCount,
      boolean verifiedSellerHistory,
      int negativeRedFlagCount,
      String sellerRedFlagSummary,
      String estimatedDelivery) {

    SeedOfferSpec(
        String merchantName,
        String merchantDomain,
        double price,
        double commissionPercent,
        String condition,
        String sourceTag) {
      this(
          merchantName,
          merchantDomain,
          price,
          commissionPercent,
          condition,
          sourceTag,
          isRiskyDomain(merchantDomain) ? 2.9 : 4.9,
          isRiskyDomain(merchantDomain) ? 24 : 1850,
          !isRiskyDomain(merchantDomain),
          isRiskyDomain(merchantDomain) ? 9 : 0,
          isRiskyDomain(merchantDomain)
              ? "Multiple buyer reports of open-box / unverified inventory disputes"
              : "None — Verified Authentic Merchant",
          isRiskyDomain(merchantDomain) ? "7–14 Days" : "Arriving in 2 Days");
    }

    private static boolean isRiskyDomain(String domain) {
      if (domain == null) return false;
      String d = domain.toLowerCase(Locale.ROOT);
      return d.contains("outlet") || d.contains("resale") || d.contains("deals")
          || d.contains("graymarket") || d.contains("liquidator") || d.contains("bargain");
    }
  }

  private record SeedProductSpec(
      String id,
      String title,
      String brand,
      String category,
      String description,
      double baselinePrice,
      List<SeedOfferSpec> offers) {}

  private static final List<SeedProductSpec> PRODUCTS = List.of(
      // 1. Audio (6 products)
      product("aud-sony-xm5", "Sony WH-1000XM5 Wireless Noise-Canceling Headphones", "Sony", "Audio",
          "Industry-leading dual-processor ANC headphones with 30-hour battery life and multipoint Bluetooth.", 399.99,
          offers("Best Buy", "bestbuy.com", 279.00, 4.5, "Target", "target.com", 289.99, 3.8,
              "Walmart", "walmart.com", 298.00, 3.5, "QuickDeal Outlet", "quickdeal-outlet.net", 249.00, 2.0)),
      product("aud-airpods-pro-2", "Apple AirPods Pro (2nd Gen, USB-C)", "Apple", "Audio",
          "Active Noise Cancellation with H2 chip, Personalized Spatial Audio, and MagSafe USB-C charging case.", 249.00,
          offers("Target", "target.com", 189.99, 3.5, "Best Buy", "bestbuy.com", 199.99, 4.0,
              "Walmart", "walmart.com", 194.00, 3.2, "Amazon", "amazon.com", 199.00, 3.0)),
      product("aud-bose-qc-ultra", "Bose QuietComfort Ultra Headphones", "Bose", "Audio",
          "Immersive spatial audio, CustomTune calibration, and plush noise-canceling ear cushions.", 429.00,
          offers("Best Buy", "bestbuy.com", 329.00, 4.2, "B&H Photo", "bhphotovideo.com", 334.00, 4.0,
              "Target", "target.com", 349.00, 3.5, "Walmart", "walmart.com", 359.00, 3.1)),
      product("aud-sennheiser-m4", "Sennheiser Momentum 4 Wireless Headphones", "Sennheiser", "Audio",
          "Audiophile 42mm transducers paired with adaptive ANC and up to 60 hours of playback.", 379.95,
          offers("B&H Photo", "bhphotovideo.com", 249.95, 4.8, "Best Buy", "bestbuy.com", 269.95, 4.2,
              "Amazon", "amazon.com", 274.00, 3.5, "Walmart", "walmart.com", 289.00, 3.0)),
      product("aud-sonos-ace", "Sonos Ace Wireless Over-Ear Headphones", "Sonos", "Audio",
          "Lossless audio over USB-C and Bluetooth with instant Sonos soundbar TV audio swap.", 449.00,
          offers("Best Buy", "bestbuy.com", 349.00, 4.0, "Target", "target.com", 369.00, 3.5,
              "B&H Photo", "bhphotovideo.com", 359.00, 4.2, "Amazon", "amazon.com", 379.00, 3.2)),
      product("aud-jbl-flip-6", "JBL Flip 6 Portable Waterproof Speaker", "JBL", "Audio",
          "IP67 waterproof and dustproof Bluetooth speaker with racetrack woofer and PartyBoost.", 129.95,
          offers("Target", "target.com", 89.95, 4.0, "Walmart", "walmart.com", 94.00, 3.5,
              "Best Buy", "bestbuy.com", 99.95, 3.8, "Amazon", "amazon.com", 99.95, 3.1)),

      // 2. Phones & Tablets (5 products)
      product("phn-iphone-15-pm", "Apple iPhone 15 Pro Max (256GB, Natural Titanium)", "Apple", "Phones & Tablets",
          "Aerospace-grade titanium design, A17 Pro chip, and 5x Telephoto pro camera system.", 1199.00,
          offers("Best Buy", "bestbuy.com", 1049.00, 2.5, "Walmart", "walmart.com", 1079.00, 2.2,
              "Target", "target.com", 1099.00, 2.0, "B&H Photo", "bhphotovideo.com", 1089.00, 2.4)),
      product("phn-s24-ultra", "Samsung Galaxy S24 Ultra (256GB, Unlocked)", "Samsung", "Phones & Tablets",
          "Built-in S Pen, 200MP quad telephoto camera, titanium frame, and QHD+ anti-reflective AMOLED.", 1299.99,
          offers("Best Buy", "bestbuy.com", 999.99, 3.5, "Amazon", "amazon.com", 1029.00, 3.0,
              "Walmart", "walmart.com", 1049.00, 2.8, "Target", "target.com", 1099.00, 2.5)),
      product("phn-pixel-9-pro", "Google Pixel 9 Pro (256GB, Obsidian, Unlocked)", "Google", "Phones & Tablets",
          "Tensor G4 processor, Super Actua 6.3-inch display, and triple pro rear camera bar.", 1099.00,
          offers("Best Buy", "bestbuy.com", 899.00, 3.2, "Amazon", "amazon.com", 919.00, 3.0,
              "Target", "target.com", 949.00, 2.8, "B&H Photo", "bhphotovideo.com", 929.00, 3.1)),
      product("phn-ipad-pro-m4", "Apple iPad Pro 11-inch M4 (256GB, Wi-Fi)", "Apple", "Phones & Tablets",
          "Ultra Retina XDR tandem OLED display powered by Apple M4 silicon in an ultra-thin enclosure.", 999.00,
          offers("Best Buy", "bestbuy.com", 899.00, 3.0, "B&H Photo", "bhphotovideo.com", 909.00, 3.2,
              "Target", "target.com", 929.00, 2.5, "Walmart", "walmart.com", 919.00, 2.6)),
      product("phn-oneplus-12", "OnePlus 12 5G (16GB RAM / 512GB, Unlocked)", "OnePlus", "Phones & Tablets",
          "Snapdragon 8 Gen 3, Hasselblad 4th-gen camera system, and 80W SuperVOOC fast charging.", 899.99,
          offers("Best Buy", "bestbuy.com", 699.99, 4.0, "Amazon", "amazon.com", 729.99, 3.5,
              "Walmart", "walmart.com", 749.00, 3.0, "Target", "target.com", 769.00, 2.8)),

      // 3. Laptops (5 products)
      product("lap-mba-15-m3", "Apple MacBook Air 15-inch M3 (16GB RAM, 512GB SSD)", "Apple", "Laptops",
          "Fanless aluminum ultrabook with 15.3-inch Liquid Retina display and up to 18-hour battery.", 1499.00,
          offers("Best Buy", "bestbuy.com", 1249.00, 3.0, "B&H Photo", "bhphotovideo.com", 1269.00, 3.2,
              "Amazon", "amazon.com", 1289.00, 2.8, "Walmart", "walmart.com", 1299.00, 2.5)),
      product("lap-dell-xps-14", "Dell XPS 14 OLED Laptop (Core Ultra 7, 16GB, 1TB)", "Dell", "Laptops",
          "CNC-machined aluminum chassis with 3.2K touch OLED InfinityEdge display and RTX 4050.", 1799.99,
          offers("Best Buy", "bestbuy.com", 1399.99, 3.8, "Dell Official", "dell.com", 1449.99, 4.0,
              "B&H Photo", "bhphotovideo.com", 1429.00, 3.5, "Walmart", "walmart.com", 1479.00, 3.0)),
      product("lap-asus-g16", "ASUS ROG Zephyrus G16 OLED (RTX 4070, 1TB SSD)", "ASUS", "Laptops",
          "240Hz ROG Nebula OLED display, Intel Core Ultra 9, and whisper-quiet vapor chamber cooling.", 1999.99,
          offers("Best Buy", "bestbuy.com", 1599.99, 4.0, "B&H Photo", "bhphotovideo.com", 1649.00, 3.8,
              "Amazon", "amazon.com", 1679.00, 3.2, "Walmart", "walmart.com", 1699.00, 3.0)),
      product("lap-lg-34-oled", "LG UltraGear 34-inch Curved OLED 240Hz Gaming Monitor", "LG", "Laptops",
          "WQHD 800R curved OLED panel with 0.03ms GtG response time and DisplayHDR True Black 400.", 1299.99,
          offers("Best Buy", "bestbuy.com", 849.99, 4.5, "Amazon", "amazon.com", 879.00, 3.8,
              "B&H Photo", "bhphotovideo.com", 869.00, 4.0, "Walmart", "walmart.com", 899.00, 3.5)),
      product("lap-mac-mini-m4", "Apple Mac Mini M4 Desktop (16GB Unified Memory, 256GB)", "Apple", "Laptops",
          "Compact 5x5-inch powerhouse desktop with Thunderbolt ports, Gigabit Ethernet, and M4 efficiency.", 599.00,
          offers("B&H Photo", "bhphotovideo.com", 529.00, 3.0, "Best Buy", "bestbuy.com", 549.00, 2.8,
              "Amazon", "amazon.com", 549.00, 2.5, "Walmart", "walmart.com", 559.00, 2.4)),

      // 4. Gaming (6 products)
      product("gam-ps5-slim", "Sony PlayStation 5 Slim Console (Disc Edition, 1TB)", "Sony", "Gaming",
          "Ultra-high-speed 1TB custom SSD, ray tracing, 4K 120Hz output, and DualSense haptic controller.", 499.99,
          offers("Target", "target.com", 429.99, 2.5, "Best Buy", "bestbuy.com", 439.99, 2.8,
              "Walmart", "walmart.com", 434.00, 2.5, "Amazon", "amazon.com", 449.00, 2.2)),
      product("gam-switch-oled", "Nintendo Switch OLED Model (White Joy-Con)", "Nintendo", "Gaming",
          "Vibrant 7-inch OLED handheld screen, wide adjustable stand, and wired LAN dock.", 349.99,
          offers("Walmart", "walmart.com", 299.00, 3.0, "Target", "target.com", 309.99, 2.8,
              "Best Buy", "bestbuy.com", 319.99, 2.5, "Amazon", "amazon.com", 314.00, 2.4)),
      product("gam-xbox-x", "Microsoft Xbox Series X 1TB Console", "Microsoft", "Gaming",
          "12 teraflops of RDNA 2 graphics power, 4K gaming up to 120fps, and Quick Resume.", 499.99,
          offers("Walmart", "walmart.com", 419.00, 2.8, "Best Buy", "bestbuy.com", 429.99, 2.5,
              "Target", "target.com", 439.99, 2.5, "Amazon", "amazon.com", 445.00, 2.2)),
      product("gam-steam-deck-oled", "Valve Steam Deck OLED 512GB Handheld PC", "Valve", "Gaming",
          "90Hz HDR OLED screen, upgraded Wi-Fi 6E, and 50Whr battery for marathon portable PC gaming.", 549.00,
          offers("Best Buy", "bestbuy.com", 519.00, 3.0, "Amazon", "amazon.com", 529.00, 2.5,
              "Walmart", "walmart.com", 535.00, 2.5, "FlashKey Reseller", "flashkey-resale.biz", 459.00, 1.5)),
      product("gam-quest-3", "Meta Quest 3 128GB Mixed Reality Headset", "Meta", "Gaming",
          "Full-color passthrough mixed reality powered by Snapdragon XR2 Gen 2 with Touch Plus controllers.", 499.99,
          offers("Best Buy", "bestbuy.com", 429.99, 3.5, "Target", "target.com", 439.99, 3.2,
              "Walmart", "walmart.com", 435.00, 3.0, "Amazon", "amazon.com", 449.00, 2.8)),
      product("gam-dualsense-edge", "Sony DualSense Edge Wireless Pro Controller", "Sony", "Gaming",
          "Customizable stick modules, swappable back paddles, trigger stops, and braided lockable USB cable.", 199.99,
          offers("Best Buy", "bestbuy.com", 169.99, 4.0, "Target", "target.com", 174.99, 3.5,
              "Walmart", "walmart.com", 172.00, 3.2, "Amazon", "amazon.com", 179.00, 3.0)),

      // 5. Sneakers & Fashion (6 products)
      product("snk-nike-dunk-low", "Nike Dunk Low Retro (White/Black Panda)", "Nike", "Sneakers & Fashion",
          "Crisp leather basketball-heritage low-top sneakers with padded collar and rubber cupsole.", 115.00,
          offers("Nike Official", "nike.com", 89.97, 5.0, "Target", "target.com", 94.00, 3.5,
              "Walmart", "walmart.com", 98.00, 3.0, "ShoeDrop Outlet", "shoedrop-deals.org", 74.00, 1.8)),
      product("snk-aj1-lost-found", "Nike Air Jordan 1 Retro High OG (Chicago Lost & Found)", "Nike", "Sneakers & Fashion",
          "Iconic Varsity Red leather panels with cracked black collars, vintage sail midsoles, and authentic Nike Air verification.", 320.00,
          List.of(
              new SeedOfferSpec(
                  "Nike Official", "nike.com", 204.00, 4.5, "new", "channel3-overnight-drop",
                  4.9, 3420, true, 0, "None — Verified Authentic Merchant", "Arriving in 2 Days"),
              new SeedOfferSpec(
                  "Hyped Kicks Liquidator", "hyped-kicks-liquidator.net", 198.00, 1.5, "new", "channel3-overnight-drop",
                  2.8, 19, false, 14, "Multiple buyer reports of replica soles & unshipped orders", "7–14 Days (Unverified)"),
              new SeedOfferSpec(
                  "Flight Club", "flightclub.com", 245.00, 3.0, "new", "channel3-overnight-drop",
                  4.9, 2100, true, 0, "None — Verified Authentic Consignment", "Arriving in 3–5 Days"))),
      product("snk-nb-9060", "New Balance 9060 Rain Cloud Suede Sneakers", "New Balance", "Sneakers & Fashion",
          "Futuristic Y2K chunky runner blending dual-density ABZORB and SBS cushioning pods.", 149.99,
          offers("Target", "target.com", 119.99, 4.2, "Walmart", "walmart.com", 124.00, 3.8,
              "Amazon", "amazon.com", 128.00, 3.2, "Nike Official", "nike.com", 135.00, 3.0)),
      product("snk-adidas-samba", "Adidas Samba OG (Cloud White / Core Black)", "Adidas", "Sneakers & Fashion",
          "Full-grain leather upper with suede T-toe overlay and classic indoor gum rubber sole.", 100.00,
          offers("Target", "target.com", 79.99, 4.5, "Walmart", "walmart.com", 84.00, 4.0,
              "Amazon", "amazon.com", 85.00, 3.5, "Best Buy", "bestbuy.com", 89.00, 3.0)),
      product("snk-lulu-belt-bag", "Lululemon Everywhere Belt Bag 1L (Water-Repellent)", "Lululemon", "Sneakers & Fashion",
          "Compact daily crossbody belt bag with interior mesh pockets and quick-release strap.", 38.00,
          offers("Target", "target.com", 29.99, 4.0, "Walmart", "walmart.com", 32.00, 3.5,
              "Amazon", "amazon.com", 34.00, 3.0, "Best Buy", "bestbuy.com", 35.00, 2.8)),
      product("snk-rayban-aviator", "Ray-Ban Classic Aviator Polarized Sunglasses (58mm)", "Ray-Ban", "Sneakers & Fashion",
          "Gold metal pilot frame with G-15 green crystal polarized lenses blocking 100% UV.", 230.00,
          offers("Amazon", "amazon.com", 164.00, 5.2, "Target", "target.com", 172.00, 4.5,
              "Walmart", "walmart.com", 178.00, 4.0, "Best Buy", "bestbuy.com", 185.00, 3.5)),

      // 6. Home & Kitchen (6 products)
      product("hom-dyson-v15", "Dyson V15 Detect Cordless Vacuum Cleaner", "Dyson", "Home & Kitchen",
          "Fluffy Optic illumination reveals hidden dust with piezo acoustic particle sizing.", 749.99,
          offers("Best Buy", "bestbuy.com", 549.99, 4.2, "Target", "target.com", 569.99, 3.8,
              "Walmart", "walmart.com", 579.00, 3.5, "Amazon", "amazon.com", 589.00, 3.2)),
      product("hom-dyson-airwrap", "Dyson Airwrap Multi-Styler Complete Long", "Dyson", "Home & Kitchen",
          "Coanda airflow styling wand that curls, waves, smooths, and dries without extreme heat damage.", 599.99,
          offers("Best Buy", "bestbuy.com", 479.99, 4.5, "Target", "target.com", 489.99, 4.0,
              "Walmart", "walmart.com", 495.00, 3.5, "Amazon", "amazon.com", 499.00, 3.2)),
      product("hom-nespresso-vertuo", "Nespresso VertuoPlus Coffee & Espresso Machine", "Nespresso", "Home & Kitchen",
          "Centrifusion brewing extracts silky crema across five cup sizes at the touch of a button.", 169.00,
          offers("Target", "target.com", 118.00, 4.8, "Walmart", "walmart.com", 124.00, 4.2,
              "Best Buy", "bestbuy.com", 129.99, 4.0, "Amazon", "amazon.com", 129.00, 3.5)),
      product("hom-ninja-creami", "Ninja Creami Deluxe 11-in-1 Ice Cream Maker", "Ninja", "Home & Kitchen",
          "Transforms frozen bases into creamy gelato, sorbet, protein pints, and Italian ice.", 249.99,
          offers("Walmart", "walmart.com", 179.00, 4.5, "Target", "target.com", 184.99, 4.2,
              "Best Buy", "bestbuy.com", 189.99, 3.8, "Amazon", "amazon.com", 194.00, 3.5)),
      product("hom-breville-barista", "Breville Barista Express Espresso Machine (Stainless)", "Breville", "Home & Kitchen",
          "Integrated conical burr grinder with PID temperature control and commercial steam wand.", 749.95,
          offers("Best Buy", "bestbuy.com", 549.95, 4.5, "Target", "target.com", 569.95, 4.0,
              "Amazon", "amazon.com", 579.00, 3.5, "Walmart", "walmart.com", 589.00, 3.2)),
      product("hom-kitchenaid-artisan", "KitchenAid Artisan Series 5-Quart Tilt-Head Mixer", "KitchenAid", "Home & Kitchen",
          "Iconic all-metal planetary mixer with 10 speeds, dough hook, flat beater, and wire whip.", 449.99,
          offers("Target", "target.com", 329.99, 4.5, "Walmart", "walmart.com", 339.00, 4.0,
              "Best Buy", "bestbuy.com", 349.99, 3.8, "Amazon", "amazon.com", 349.00, 3.5)),

      // 7. Watches (5 products)
      product("wat-apple-ultra-2", "Apple Watch Ultra 2 GPS + Cellular (49mm Titanium)", "Apple", "Watches",
          "Dual-frequency precision GPS, 3000-nit sapphire display, and 36-hour rugged battery life.", 799.00,
          offers("Best Buy", "bestbuy.com", 699.00, 3.2, "Amazon", "amazon.com", 714.00, 3.0,
              "Target", "target.com", 729.00, 2.8, "Walmart", "walmart.com", 719.00, 2.8)),
      product("wat-garmin-fenix-8", "Garmin Fenix 8 AMOLED 47mm Multisport Smartwatch", "Garmin", "Watches",
          "Dive-rated titanium bezel smartwatch with offline topo maps, flashlight, and 16-day battery.", 999.99,
          offers("Best Buy", "bestbuy.com", 879.99, 3.8, "B&H Photo", "bhphotovideo.com", 889.00, 4.0,
              "Amazon", "amazon.com", 899.00, 3.2, "Walmart", "walmart.com", 909.00, 3.0)),
      product("wat-seiko-5", "Seiko 5 Sports SRPD55 Automatic Mechanical Watch", "Seiko", "Watches",
          "Japanese Caliber 4R36 automatic movement, Hardlex crystal, and unidirectional diver bezel.", 295.00,
          offers("Amazon", "amazon.com", 198.00, 5.0, "Walmart", "walmart.com", 214.00, 4.0,
              "Target", "target.com", 219.00, 3.8, "Best Buy", "bestbuy.com", 225.00, 3.5)),
      product("wat-tissot-prx", "Tissot PRX Powermatic 80 40mm Automatic (Ice Blue)", "Tissot", "Watches",
          "Swiss automatic movement with 80-hour power reserve, waffle dial, and integrated bracelet.", 725.00,
          offers("Amazon", "amazon.com", 565.00, 4.5, "B&H Photo", "bhphotovideo.com", 579.00, 4.2,
              "Walmart", "walmart.com", 589.00, 3.8, "Target", "target.com", 599.00, 3.5)),
      product("wat-whoop-4", "WHOOP 4.0 Health & Recovery Tracker (12-Month Plan)", "WHOOP", "Watches",
          "Screen-free continuous strain, sleep, and HRV wearable strap with slide-on wireless battery pack.", 239.00,
          offers("Best Buy", "bestbuy.com", 199.00, 4.5, "Amazon", "amazon.com", 209.00, 4.0,
              "Target", "target.com", 215.00, 3.8, "Walmart", "walmart.com", 219.00, 3.5)),

      // 8. Cameras (5 products)
      product("cam-dji-pocket-3", "DJI Osmo Pocket 3 Creator Combo (1-Inch CMOS, 4K120)", "DJI", "Cameras",
          "3-axis gimbal pocket vlogging camera with 1-inch CMOS sensor, DJI Mic 2, and battery handle.", 669.00,
          offers("B&H Photo", "bhphotovideo.com", 599.00, 4.0, "Best Buy", "bestbuy.com", 619.00, 3.8,
              "Amazon", "amazon.com", 629.00, 3.5, "Walmart", "walmart.com", 639.00, 3.0)),
      product("cam-sony-zve10-2", "Sony ZV-E10 II Mirrorless Vlog Camera + 16-50mm Lens", "Sony", "Cameras",
          "26MP Exmor R APS-C sensor, real-time Eye AF, and cinematic product showcase mode.", 1099.99,
          offers("B&H Photo", "bhphotovideo.com", 948.00, 4.5, "Best Buy", "bestbuy.com", 969.99, 4.0,
              "Amazon", "amazon.com", 978.00, 3.5, "Walmart", "walmart.com", 998.00, 3.0)),
      product("cam-gopro-hero13", "GoPro HERO13 Black 5.3K Waterproof Action Camera", "GoPro", "Cameras",
          "HB-Series magnetic lens compatibility, HyperSmooth 6.0 stabilization, and Enduro battery.", 399.99,
          offers("Target", "target.com", 329.99, 4.2, "Best Buy", "bestbuy.com", 339.99, 4.0,
              "Walmart", "walmart.com", 334.00, 3.8, "B&H Photo", "bhphotovideo.com", 349.00, 3.5)),
      product("cam-fujifilm-x100vi", "Fujifilm X100VI 40.2MP Compact Digital Camera (Silver)", "Fujifilm", "Cameras",
          "Fixed 23mm f/2 lens, 6-stop in-body stabilization, and 20 iconic film simulations.", 1599.00,
          offers("B&H Photo", "bhphotovideo.com", 1499.00, 3.5, "Best Buy", "bestbuy.com", 1529.00, 3.0,
              "Amazon", "amazon.com", 1549.00, 2.8, "GrayMarketCam", "graymarketcam-deals.xyz", 1299.00, 1.2)),
      product("cam-elgato-streamdeck", "Elgato Stream Deck MK.2 (15 LCD Keys Studio Controller)", "Elgato", "Cameras",
          "15 customizable tactile LCD keys for OBS, editing shortcuts, lighting, and one-tap macros.", 149.99,
          offers("Best Buy", "bestbuy.com", 119.99, 4.5, "Amazon", "amazon.com", 124.99, 4.0,
              "B&H Photo", "bhphotovideo.com", 122.00, 4.2, "Walmart", "walmart.com", 129.99, 3.5))
  );

  private BuiltInMarketplaceCatalog() {}

  public static int productCount() {
    return PRODUCTS.size();
  }

  public static boolean isFlagshipOvernightDropQuery(String query, String sku) {
    if ("NIKE-AJ1-CHIC-OG".equalsIgnoreCase(sku) || "snk-aj1-lost-found".equalsIgnoreCase(sku)) {
      return true;
    }
    if (query == null || query.isBlank()) {
      return false;
    }
    String q = query.toLowerCase(Locale.ROOT);
    return (q.contains("air jordan 1") || q.contains("jordan 1") || q.contains("chicago lost & found"))
        && !q.contains("dunk");
  }

  public static List<Channel3ProductOfferDto> flagshipOvernightDropOffers(Instant now) {
    return PRODUCTS.stream()
        .filter(p -> "snk-aj1-lost-found".equals(p.id()))
        .findFirst()
        .map(spec -> toOfferDtos(spec, now))
        .orElse(List.of());
  }

  /**
   * Builds dynamic competing drop offers calibrated to any watch's product name, SKU,
   * target price, hard ceiling cap, and preferred merchant domain at the current timestamp.
   */
  public static List<Channel3ProductOfferDto> dynamicDropOffersForWatch(
      String productName,
      String sku,
      String primaryMerchant,
      BigDecimal targetPrice,
      BigDecimal maxCeiling,
      BigDecimal baselinePrice,
      Instant now) {
    if (isFlagshipOvernightDropQuery(productName, sku)) {
      return flagshipOvernightDropOffers(now);
    }

    String cleanTitle = (productName != null && !productName.isBlank()) ? productName.trim() : "Watched Product";
    String cleanSku = (sku != null && !sku.isBlank()) ? sku.trim() : slugify(cleanTitle);
    String cleanMerchant = (primaryMerchant != null && !primaryMerchant.isBlank())
        ? primaryMerchant.trim().toLowerCase(Locale.ROOT)
        : "bestbuy.com";
    BigDecimal target = targetPrice != null && targetPrice.compareTo(BigDecimal.ZERO) > 0
        ? targetPrice.setScale(2, RoundingMode.HALF_UP)
        : new BigDecimal("200.00");
    BigDecimal ceiling = maxCeiling != null && maxCeiling.compareTo(target) >= 0
        ? maxCeiling.setScale(2, RoundingMode.HALF_UP)
        : target;
    BigDecimal bandRoom = ceiling.subtract(target);
    BigDecimal winningPrice = bandRoom.compareTo(new BigDecimal("2.00")) >= 0
        ? target.add(bandRoom.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP).min(new BigDecimal("4.00")))
        : target;
    BigDecimal unsafeUndercut = target.subtract(new BigDecimal("6.00"))
        .max(new BigDecimal("1.00"))
        .setScale(2, RoundingMode.HALF_UP);
    BigDecimal overCapPrice = ceiling.add(new BigDecimal("35.00")).setScale(2, RoundingMode.HALF_UP);
    BigDecimal baseline = (baselinePrice != null && baselinePrice.compareTo(overCapPrice) >= 0)
        ? baselinePrice.setScale(2, RoundingMode.HALF_UP)
        : overCapPrice.add(new BigDecimal("25.00")).setScale(2, RoundingMode.HALF_UP);

    // Try matching an existing catalog item for richer brand/category/image metadata
    SeedProductSpec matchedSpec = PRODUCTS.stream()
        .filter(p -> matchesSpec(p, tokenize(cleanTitle.toLowerCase(Locale.ROOT)), cleanTitle.toLowerCase(Locale.ROOT)))
        .findFirst()
        .orElse(null);
    String brand = matchedSpec != null ? matchedSpec.brand() : cleanTitle.split("\\s+")[0];
    String category = matchedSpec != null ? matchedSpec.category() : "Electronics";
    String imageUrl = matchedSpec != null
        ? defaultProductImage(matchedSpec.id(), category)
        : defaultProductImage(cleanSku, category);
    String slug = slugify(cleanTitle);
    String merchantLabel = Character.toUpperCase(cleanMerchant.charAt(0)) + cleanMerchant.substring(1);

    List<Channel3ProductOfferDto> result = new ArrayList<>();
    result.add(new Channel3ProductOfferDto(
        cleanSku,
        cleanTitle,
        "Verified drop listing from authorized retailer " + merchantLabel + ".",
        brand,
        category,
        merchantLabel,
        cleanMerchant,
        winningPrice,
        baseline,
        baseline.subtract(winningPrice).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP),
        pct(baseline, winningPrice),
        "USD",
        "https://" + cleanMerchant + "/dp/" + slug,
        imageUrl,
        "IN_STOCK",
        "new",
        new BigDecimal("3.80"),
        "channel3-drop-alert",
        now,
        4.9,
        3180,
        true,
        0,
        "None — Verified Authentic Retailer",
        "Arriving in 2 Days"));

    result.add(new Channel3ProductOfferDto(
        cleanSku + "-UNVERIFIED",
        cleanTitle + " (Liquidation Unverified Batch)",
        "Unverified liquidation offer below market floor.",
        brand,
        category,
        "Flash Drop Liquidator",
        "flash-drop-liquidator.net",
        unsafeUndercut,
        baseline,
        baseline.subtract(unsafeUndercut).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP),
        pct(baseline, unsafeUndercut),
        "USD",
        "https://flash-drop-liquidator.net/deal/" + slug,
        imageUrl,
        "IN_STOCK",
        "open-box",
        new BigDecimal("1.00"),
        "channel3-drop-alert",
        now,
        2.7,
        17,
        false,
        12,
        "12 counterfeit / non-delivery complaints in past 30 days",
        "Unverified 10–14 Days"));

    result.add(new Channel3ProductOfferDto(
        cleanSku + "-OVERCAP",
        cleanTitle + " (Priority Reseller Stock)",
        "Authentic stock priced above configured hard cap.",
        brand,
        category,
        "Flight Club",
        "flightclub.com",
        overCapPrice,
        baseline,
        baseline.subtract(overCapPrice).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP),
        pct(baseline, overCapPrice),
        "USD",
        "https://flightclub.com/item/" + slug,
        imageUrl,
        "IN_STOCK",
        "new",
        new BigDecimal("2.50"),
        "channel3-drop-alert",
        now,
        4.9,
        2140,
        true,
        0,
        "None — Verified Consignment Reseller",
        "Arriving in 3–5 Days"));
    return result;
  }

  private static BigDecimal pct(BigDecimal baseline, BigDecimal price) {
    if (baseline == null || baseline.compareTo(BigDecimal.ZERO) <= 0 || price == null) {
      return BigDecimal.ZERO;
    }
    BigDecimal savings = baseline.subtract(price).max(BigDecimal.ZERO);
    return savings.multiply(new BigDecimal("100")).divide(baseline, 2, RoundingMode.HALF_UP);
  }

  public static List<MarketplaceProductDto> allMarketplaceProducts(Instant now) {
    return PRODUCTS.stream()
        .map(spec -> toMarketplaceProduct(spec, now))
        .toList();
  }

  /**
   * Returns all built-in catalog offers matching query and/or category cleanly.
   */
  public static List<Channel3ProductOfferDto> searchOffers(String query, String category, Instant now) {
    String cleanCategory = normalizeCategory(category);
    String cleanQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    List<String> tokens = tokenize(cleanQuery);

    List<Channel3ProductOfferDto> out = new ArrayList<>();
    for (SeedProductSpec spec : PRODUCTS) {
      if (cleanCategory != null && !spec.category().equalsIgnoreCase(cleanCategory)) {
        continue;
      }
      if (!tokens.isEmpty() && !matchesSpec(spec, tokens, cleanQuery)) {
        continue;
      }
      out.addAll(toOfferDtos(spec, now));
    }
    return out;
  }

  /**
   * Synthesizes realistic multi-merchant offers for ANY arbitrary user search query
   * when both Channel3 live search and the curated 44-product catalog do not return matches.
   */
  public static List<Channel3ProductOfferDto> synthesizeForQuery(String query, BigDecimal baselineHint, Instant now) {
    String clean = query == null || query.isBlank() ? "Featured Electronics Deal" : query.trim();
    String title = toTitleCase(clean);
    String inferredCategory = inferCategory(clean);
    String inferredBrand = inferBrand(title);

    BigDecimal estimatedRetail = baselineHint != null && baselineHint.compareTo(BigDecimal.ZERO) > 0
        ? baselineHint.setScale(2, RoundingMode.HALF_UP)
        : estimateRetailPrice(clean, inferredCategory);

    BigDecimal bestBuyPrice = estimatedRetail.multiply(new BigDecimal("0.84")).setScale(2, RoundingMode.HALF_UP);
    BigDecimal targetPrice = estimatedRetail.multiply(new BigDecimal("0.88")).setScale(2, RoundingMode.HALF_UP);
    BigDecimal walmartPrice = estimatedRetail.multiply(new BigDecimal("0.89")).setScale(2, RoundingMode.HALF_UP);
    BigDecimal thirdPartyPrice = estimatedRetail.multiply(new BigDecimal("0.76")).setScale(2, RoundingMode.HALF_UP);

    String slug = slugify(clean);
    String imageUrl = synthesizeImageUrl(clean, inferredCategory);

    return List.of(
        buildSynthesizedOffer("syn-" + slug + "-bb", title, inferredBrand, inferredCategory,
            "Verified retail listing for " + title + " with full manufacturer warranty and 2-day shipping.",
            "Best Buy", "bestbuy.com", bestBuyPrice, estimatedRetail, new BigDecimal("4.00"), imageUrl,
            "smart-catalog-synthesizer", now),
        buildSynthesizedOffer("syn-" + slug + "-tg", title, inferredBrand, inferredCategory,
            "Official storefront offer for " + title + " eligible for BuyBound Buyer Protection.",
            "Target", "target.com", targetPrice, estimatedRetail, new BigDecimal("3.60"), imageUrl,
            "smart-catalog-synthesizer", now),
        buildSynthesizedOffer("syn-" + slug + "-wm", title, inferredBrand, inferredCategory,
            "In-stock shipped & sold offer for " + title + " with standard returns.",
            "Walmart", "walmart.com", walmartPrice, estimatedRetail, new BigDecimal("3.20"), imageUrl,
            "smart-catalog-synthesizer", now),
        buildSynthesizedOffer("syn-" + slug + "-tp", title, inferredBrand, inferredCategory,
            "Open-box unverified marketplace deal for " + title + " from a third-party liquidator.",
            "Marketplace Bargains", "bargain-drop-hub.net", thirdPartyPrice, estimatedRetail, new BigDecimal("1.50"), imageUrl,
            "smart-catalog-synthesizer", now)
    );
  }

  /**
   * Groups flat offer rows into MarketplaceProductDto cards sorted according to the UI sort selector.
   */
  public static List<MarketplaceProductDto> groupOffersIntoProducts(
      List<Channel3ProductOfferDto> flatOffers,
      BigDecimal minPrice,
      BigDecimal maxPrice,
      String sort) {
    Map<String, List<Channel3ProductOfferDto>> grouped = new LinkedHashMap<>();
    for (Channel3ProductOfferDto offer : flatOffers) {
      String key = canonicalKey(offer);
      grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(offer);
    }

    List<MarketplaceProductDto> cards = new ArrayList<>();
    for (Map.Entry<String, List<Channel3ProductOfferDto>> entry : grouped.entrySet()) {
      List<Channel3ProductOfferDto> sortedOffers = entry.getValue().stream()
          .filter(o -> o.currentPrice() != null)
          .sorted(Comparator.comparing(Channel3ProductOfferDto::currentPrice))
          .toList();
      if (sortedOffers.isEmpty()) {
        continue;
      }

      Channel3ProductOfferDto best = sortedOffers.getFirst();
      if (minPrice != null && best.currentPrice().compareTo(minPrice) < 0) {
        continue;
      }
      if (maxPrice != null && best.currentPrice().compareTo(maxPrice) > 0) {
        continue;
      }

      BigDecimal baseline = sortedOffers.stream()
          .map(Channel3ProductOfferDto::baselinePrice)
          .filter(b -> b != null && b.compareTo(BigDecimal.ZERO) > 0)
          .max(Comparator.naturalOrder())
          .orElse(best.currentPrice());
      BigDecimal savings = baseline.subtract(best.currentPrice()).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
      BigDecimal savingsPct = baseline.compareTo(BigDecimal.ZERO) > 0
          ? savings.multiply(new BigDecimal("100")).divide(baseline, 1, RoundingMode.HALF_UP)
          : BigDecimal.ZERO;

      int distinctStores = (int) sortedOffers.stream()
          .map(Channel3ProductOfferDto::merchantDomain)
          .distinct()
          .count();

      cards.add(new MarketplaceProductDto(
          best.channel3ProductId(),
          best.title(),
          best.brand(),
          best.category(),
          best.description(),
          best.imageUrl(),
          best.currentPrice(),
          baseline,
          savings,
          savingsPct,
          best.merchantName(),
          best.merchantDomain(),
          Math.max(1, distinctStores),
          sortedOffers));
    }

    String cleanSort = sort == null ? "savings" : sort.trim().toLowerCase(Locale.ROOT);
    Comparator<MarketplaceProductDto> comparator = switch (cleanSort) {
      case "price_asc", "lowest_price" -> Comparator.comparing(MarketplaceProductDto::bestPrice);
      case "price_desc", "highest_price" -> Comparator.comparing(MarketplaceProductDto::bestPrice).reversed();
      case "best_match" -> Comparator.comparing(MarketplaceProductDto::storeCount).reversed()
          .thenComparing(MarketplaceProductDto::savingsPercent, Comparator.reverseOrder());
      default -> Comparator.comparing(MarketplaceProductDto::bestSavings).reversed();
    };
    return cards.stream().sorted(comparator).toList();
  }

  public static String synthesizeImageUrl(String query, String category) {
    return resolveProductImage(query, category);
  }

  public static String thumbnailUrl(String category, String slug, String title) {
    String catParam = URLEncoder.encode(category == null ? "Electronics" : category, StandardCharsets.UTF_8);
    String titleParam = URLEncoder.encode(title == null ? slug : title, StandardCharsets.UTF_8);
    return "/api/catalog/thumbnail?category=" + catParam + "&title=" + titleParam;
  }

  /** Exact 1-to-1 verified unique product photos for all 44 built-in catalog items. */
  public record CatalogSeedSummary(String id, String title, String brand, String category) {}

  public static List<CatalogSeedSummary> catalogSeeds() {
    return PRODUCTS.stream()
        .map(p -> new CatalogSeedSummary(p.id(), p.title(), p.brand(), p.category()))
        .toList();
  }

  private static final Map<String, String> PRODUCT_IMAGES = Map.ofEntries(
      // Audio (6) - Exact verified Channel3 Commerce product studio photographs
      Map.entry("aud-sony-xm5", "https://cdn.trychannel3.com/cleaned/v1/a3b8b014-b3b2-40be-b363-b3402bb7e855?aid=01M767ET0P8FHYV5FQ3XX7P7HB&mid=oOY7"),
      Map.entry("aud-airpods-pro-2", "https://cdn.trychannel3.com/cleaned/v1/c6a01953-ca4a-47ee-8b6d-70e93d8210c8?aid=01M767CC7FZ9YZ2E45DBFRJD4W&mid=BsaX"),
      Map.entry("aud-bose-qc-ultra", "https://cdn.trychannel3.com/cleaned/v1/44ea931f-59e1-46c5-b4a3-ec780ecf912b?aid=01M767F06BHN6ZREH319ZHFEPZ&mid=ICa0"),
      Map.entry("aud-sennheiser-m4", "https://cdn.trychannel3.com/f08fbc7a-ceec-4594-9a41-91b88028e49e?aid=01M767CFC43VKH1M8QC30HRHES&mid=idcX"),
      Map.entry("aud-sonos-ace", "https://cdn.trychannel3.com/cleaned/v1/be74fac8-ee12-4cd6-88d0-ed2b42c45054?aid=01M767CGMFK131HS60YHBDYZVX&mid=mDEv"),
      Map.entry("aud-jbl-flip-6", "https://cdn.trychannel3.com/cleaned/v1/670ebbbe-24a3-4277-9c25-ee052ccc0401?aid=01M767CJANX8M9EPH62HWSEVE5&mid=CgP5"),
      // Phones & Tablets (5)
      Map.entry("phn-iphone-15-pm", "https://cdn.trychannel3.com/cleaned/v1/d7ea12d6-91e8-4d9b-a874-0c6a18d77701?aid=01M767M9NQ38EW71W27JGKTPN7&mid=gTIp"),
      Map.entry("phn-s24-ultra", "https://cdn.trychannel3.com/ee2e1c60-1a1b-419c-9e00-d1b05c5e1db6?aid=01M767CNG3N9YFQX2BC2D5X55M&mid=BsaX"),
      Map.entry("phn-pixel-9-pro", "https://cdn.trychannel3.com/cleaned/v1/0f60421d-1476-4ea3-8f4a-7f90aa940551?aid=01M767CQ3AD0F14920VKX0AA6Q&mid=TgS4"),
      Map.entry("phn-ipad-pro-m4", "https://cdn.trychannel3.com/9af7d459-47ae-4fc4-86d4-1cef70b4c2a1?aid=01M767CR53C2PBE4M9MD506QP4&mid=dCgO"),
      Map.entry("phn-oneplus-12", "https://cdn.trychannel3.com/08dd0785-0937-4d67-b591-4d59ae41f2c1?aid=01M767CSBTPKPXK4N54DSTY5DT&mid=BsaX"),
      // Laptops & Monitors (5)
      Map.entry("lap-mba-15-m3", "https://cdn.trychannel3.com/c938c0ec-d365-4be9-a116-8a4f9b88b34c?aid=01M767CTNPSR8MFD3D93GCXYFX&mid=DoAg"),
      Map.entry("lap-dell-xps-14", "https://cdn.trychannel3.com/ef47f9c7-900c-46e3-96bd-0b2559bc3425?aid=01M767CVQ32EZA6423NXC8Q434&mid=p1PB"),
      Map.entry("lap-asus-g16", "https://cdn.trychannel3.com/19a86516-499b-405b-b6b9-f8c74867f68d?aid=01M767CWY1N5NJ3S3CYFE5NQ9N&mid=LPzf"),
      Map.entry("lap-lg-34-oled", "https://cdn.trychannel3.com/cleaned/v1/82bcbc17-b07d-47a3-882f-536db8f4ce93?aid=01M767CYFX5WZ02W6BY9Y8236B&mid=g0qz"),
      Map.entry("lap-mac-mini-m4", "https://cdn.trychannel3.com/1a87d4ab-9443-4304-bb37-ff1b9911cf87?aid=01M767CZT0YWPFAQQVCSPJ83AY&mid=mn23"),
      // Gaming (6)
      Map.entry("gam-ps5-slim", "https://cdn.trychannel3.com/cleaned/v1/ad4afa8c-5448-4e35-bc4c-46dd78587a2f?aid=01M767D0V52QZBZZB4H7AZ5H6S&mid=hl1t"),
      Map.entry("gam-switch-oled", "https://cdn.trychannel3.com/c88dd36d-ee96-4da0-aad6-840b6261c899?aid=01M767D1YRYYT8EVYG3WSTFW53&mid=Qoqa"),
      Map.entry("gam-xbox-x", "https://cdn.trychannel3.com/08baa0dd-2a20-4984-b049-525bd7caf215?aid=01M767D3C44QN67N665E193ARX&mid=vVQC"),
      Map.entry("gam-steam-deck-oled", "https://cdn.trychannel3.com/c1616d4c-fd7a-44b2-b50f-2fd6575f5889?aid=01M767D493K2EA3K9KS253Q5DH&mid=BsaX"),
      Map.entry("gam-quest-3", "https://cdn.trychannel3.com/cleaned/v1/6d845cd9-4ce6-42e0-9150-fcccddce5abf?aid=01M767D5AKKYNY79XVHJMX75MN&mid=vVQC"),
      Map.entry("gam-dualsense-edge", "https://cdn.trychannel3.com/acd32491-cf89-4b06-8425-92b0307ed49d?aid=01M767D6A7SGBKRMEPZCD3T59N&mid=Bteb"),
      // Sneakers & Fashion (6)
      Map.entry("snk-nike-dunk-low", "https://cdn.trychannel3.com/cleaned/v1/1db7a8d3-780f-4d08-9022-59e0504a4a0e?aid=01M767EYA6SN8BF73BETWMZT6S&mid=wscm"),
      Map.entry("snk-aj1-lost-found", "https://cdn.trychannel3.com/4331ab0f-6cca-45a2-b934-d74ad70c6dd5?aid=01M767CK5RDXNYDHPMV08YJYBR&mid=hHvi"),
      Map.entry("snk-nb-9060", "https://cdn.trychannel3.com/cleaned/v1/0f2fa932-0cdb-4720-bf20-c53a452c26c8?aid=01M767CMSM1KY86CMQGKF11D44&mid=4Raz"),
      Map.entry("snk-adidas-samba", "https://cdn.trychannel3.com/cleaned/v1/d275f05b-4a33-41a2-8f33-785646976394?aid=01M767CP8YS5VCAC3NKFESMBD6&mid=YikB"),
      Map.entry("snk-lulu-belt-bag", "https://cdn.trychannel3.com/d9fe5586-d30e-4d8b-88c2-d3c688de0068?aid=01M767EWVDH75XETNPEVG6EFFD&mid=Ou3m"),
      Map.entry("snk-rayban-aviator", "https://cdn.trychannel3.com/cleaned/v1/5bf4d252-bb2e-4747-90bb-04b46cbced6b?aid=01M767CR92YPXZP40Z0XTCYC1N&mid=MywU"),
      // Home & Kitchen (6)
      Map.entry("hom-dyson-v15", "https://cdn.trychannel3.com/cleaned/v1/0517df1d-aedf-4166-902b-9489275cbdef?aid=01M767CSFP29Y11MFW2C92DK9H&mid=K6RA"),
      Map.entry("hom-dyson-airwrap", "https://cdn.trychannel3.com/d5ec0f3e-c355-4e78-a619-3bb682dbfec1?aid=01M767CTPRTKBT1EG0H0MAQ2RT&mid=nSgk"),
      Map.entry("hom-nespresso-vertuo", "https://cdn.trychannel3.com/91cf3b24-7a27-427f-8631-1c2f4741e7c0?aid=01M767CVW4CCHR74XF4CTT8J6A&mid=w4H1"),
      Map.entry("hom-ninja-creami", "https://cdn.trychannel3.com/b10e59c3-14a9-4821-b68e-5de3e081b20f?aid=01M767CWTA8J5XJEE61GG0NHNR&mid=p1PB"),
      Map.entry("hom-breville-barista", "https://cdn.trychannel3.com/cleaned/v1/f3aedf8a-2d4e-497e-b47a-a4f2e1cc3233?aid=01M767CYGVWZ3WMGPPSRGTHSWE&mid=DO3T"),
      Map.entry("hom-kitchenaid-artisan", "https://cdn.trychannel3.com/cleaned/v1/65db5562-6386-4a82-b002-548b5a36cdea?aid=01M767CZFNVQF3884MBNNKE4TK&mid=DyQi"),
      // Watches (5)
      Map.entry("wat-apple-ultra-2", "https://cdn.trychannel3.com/ca01491f-b37f-4208-bdc5-0198cd7b06b7?aid=01M767D0SXYSMYGZX0ZRP2A3ZH&mid=p1PB"),
      Map.entry("wat-garmin-fenix-8", "https://cdn.trychannel3.com/ebd5edbf-a517-4bca-9c2d-4a2fd3be9c73?aid=01M767D1Q0DMG928DTVE1EQR1H&mid=fdOw"),
      Map.entry("wat-seiko-5", "https://cdn.trychannel3.com/cleaned/v1/7d392aab-e062-47d9-8af0-9f8d9cdaca53?aid=01M767D302VB7TEDVCVX7Y1RET&mid=K6RA"),
      Map.entry("wat-tissot-prx", "https://cdn.trychannel3.com/f7822e16-e9a9-4f00-96de-ea826901ad39?aid=01M767D4ATGDN88TDNSCJWCVHX&mid=r9yS"),
      Map.entry("wat-whoop-4", "https://cdn.trychannel3.com/cleaned/v1/6b6241da-e910-41a7-8d1f-13478d5351af?aid=01M767EVBKKNVST7QPD6Q5YDYW&mid=fdOw"),
      // Cameras (5)
      Map.entry("cam-dji-pocket-3", "https://cdn.trychannel3.com/cleaned/v1/3dab2449-5cbf-4b8b-8849-0aaadb11326a?aid=01M767D6S8NAMNRC8GSNGWPRHG&mid=ZQQu"),
      Map.entry("cam-sony-zve10-2", "https://cdn.trychannel3.com/cleaned/v1/8d6f6877-6e11-4a09-87e0-6608704087d4?aid=01M767D7ZJ6HK844WX6PA8M0P8&mid=oHaU"),
      Map.entry("cam-gopro-hero13", "https://cdn.trychannel3.com/cleaned/v1/455866be-f885-4d1b-951e-a94abf86d1e1?aid=01M767D9BQ6V7NDK0TM8TRJ25V&mid=oHaU"),
      Map.entry("cam-fujifilm-x100vi", "https://cdn.trychannel3.com/907eaf14-c3b8-414f-923d-620d9ea31649?aid=01M767DAMWQ48M26Y9V0Y53AZH&mid=Qoqa"),
      Map.entry("cam-elgato-streamdeck", "https://cdn.trychannel3.com/be1b8154-83d4-4115-a8b9-2e75a764f382?aid=01M767DCFWNZ2S2XQ821HMVF46&mid=hl1t")
  );

  private static final List<String> AUDIO_POOL = List.of(
      "https://images.unsplash.com/photo-1590658268037-6bf12165a8df?w=800&auto=format&fit=crop&q=80",
      "https://cdn.dummyjson.com/product-images/mobile-accessories/apple-airpods-max-silver/1.webp",
      "https://cdn.dummyjson.com/product-images/mobile-accessories/beats-flex-wireless-earphones/1.webp",
      "https://cdn.dummyjson.com/product-images/mobile-accessories/apple-homepod-mini-cosmic-grey/1.webp",
      "https://cdn.dummyjson.com/product-images/mobile-accessories/amazon-echo-plus/1.webp",
      "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=800&auto=format&fit=crop&q=80",
      "https://images.unsplash.com/photo-1546435770-a3e426bf472b?w=800&auto=format&fit=crop&q=80",
      "https://images.unsplash.com/photo-1583394838336-acd977736f90?w=800&auto=format&fit=crop&q=80",
      "https://images.unsplash.com/photo-1484704849700-f032a568e944?w=800&auto=format&fit=crop&q=80",
      "https://images.unsplash.com/photo-1608043152269-423dbba4e7e1?w=800&auto=format&fit=crop&q=80"
  );

  private static final List<String> PHONE_TABLET_POOL = List.of(
      "https://images.unsplash.com/photo-1610945415295-d9bbf067e59c?w=800&auto=format&fit=crop&q=80",
      "https://cdn.dummyjson.com/product-images/smartphones/samsung-galaxy-s10/1.webp",
      "https://cdn.dummyjson.com/product-images/tablets/ipad-mini-2021-starlight/1.webp",
      "https://cdn.dummyjson.com/product-images/tablets/samsung-galaxy-tab-s8-plus-grey/1.webp",
      "https://cdn.dummyjson.com/product-images/smartphones/iphone-13-pro/1.webp",
      "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=800&auto=format&fit=crop&q=80",
      "https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?w=800&auto=format&fit=crop&q=80",
      "https://cdn.dummyjson.com/product-images/smartphones/oppo-f19-pro-plus/1.webp"
  );

  private static final List<String> LAPTOP_MONITOR_POOL = List.of(
      "https://images.unsplash.com/photo-1593642632823-8f785ba67e45?w=800&auto=format&fit=crop&q=80",
      "https://cdn.dummyjson.com/product-images/laptops/apple-macbook-pro-14-inch-space-grey/1.webp",
      "https://cdn.dummyjson.com/product-images/laptops/huawei-matebook-x-pro/1.webp",
      "https://cdn.dummyjson.com/product-images/laptops/lenovo-yoga-920/1.webp",
      "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=800&auto=format&fit=crop&q=80",
      "https://cdn.trychannel3.com/cleaned/v1/82bcbc17-b07d-47a3-882f-536db8f4ce93?aid=01M767CYFX5WZ02W6BY9Y8236B&mid=g0qz",
      "https://cdn.trychannel3.com/ef47f9c7-900c-46e3-96bd-0b2559bc3425?aid=01M767CVQ32EZA6423NXC8Q434&mid=p1PB",
      "https://cdn.trychannel3.com/19a86516-499b-405b-b6b9-f8c74867f68d?aid=01M767CWY1N5NJ3S3CYFE5NQ9N&mid=LPzf"
  );

  private static final List<String> GAMING_POOL = List.of(
      "https://images.unsplash.com/photo-1621259182978-fbf93132d53d?w=800&auto=format&fit=crop&q=80",
      "https://images.unsplash.com/photo-1606144042614-b2417e99c4e3?w=800&auto=format&fit=crop&q=80",
      "https://images.unsplash.com/photo-1578303512597-81e6cc155b3e?w=800&auto=format&fit=crop&q=80",
      "https://images.unsplash.com/photo-1612287230202-1ff1d85d1bdf?w=800&auto=format&fit=crop&q=80",
      "https://thumb.wikimedia.org/wikipedia/commons/thumb/7/77/Black_and_white_Playstation_5_base_edition_with_controller.png/960px-Black_and_white_Playstation_5_base_edition_with_controller.png",
      "https://thumb.wikimedia.org/wikipedia/commons/thumb/1/12/Xbox_Series_X_2.jpg/960px-Xbox_Series_X_2.jpg",
      "https://thumb.wikimedia.org/wikipedia/commons/thumb/a/af/Meta_Quest_3_display_unit.jpg/960px-Meta_Quest_3_display_unit.jpg"
  );

  private static final List<String> FASHION_POOL = List.of(
      "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=800&auto=format&fit=crop&q=80",
      "https://cdn.dummyjson.com/product-images/mens-shoes/puma-future-rider-trainers/1.webp",
      "https://cdn.dummyjson.com/product-images/mens-shoes/sports-sneakers-off-white-red/1.webp",
      "https://cdn.dummyjson.com/product-images/womens-bags/prada-women-bag/1.webp",
      "https://cdn.dummyjson.com/product-images/fragrances/calvin-klein-ck-one/1.webp",
      "https://cdn.dummyjson.com/product-images/sunglasses/classic-sun-glasses/1.webp",
      "https://images.unsplash.com/photo-1556906781-9a412961c28c?w=800&auto=format&fit=crop&q=80",
      "https://images.unsplash.com/photo-1511499767150-a48a237f0083?w=800&auto=format&fit=crop&q=80"
  );

  private static final List<String> HOME_KITCHEN_POOL = List.of(
      "https://images.unsplash.com/photo-1585515320310-259814833e62?w=800&auto=format&fit=crop&q=80",
      "https://cdn.dummyjson.com/product-images/kitchen-accessories/hand-blender/1.webp",
      "https://cdn.dummyjson.com/product-images/kitchen-accessories/boxed-blender/1.webp",
      "https://images.unsplash.com/photo-1558317374-067fb5f30001?w=800&auto=format&fit=crop&q=80",
      "https://images.unsplash.com/photo-1517668808822-9ebb02f2a0e6?w=800&auto=format&fit=crop&q=80",
      "https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?w=800&auto=format&fit=crop&q=80",
      "https://images.unsplash.com/photo-1522338140262-f46f5913618a?w=800&auto=format&fit=crop&q=80"
  );

  private static final List<String> WATCH_POOL = List.of(
      "https://cdn.dummyjson.com/product-images/mens-watches/rolex-submariner-watch/1.webp",
      "https://cdn.dummyjson.com/product-images/womens-watches/rolex-cellini-moonphase/1.webp",
      "https://cdn.dummyjson.com/product-images/mens-watches/longines-master-collection/1.webp",
      "https://cdn.dummyjson.com/product-images/womens-watches/iwc-ingenieur-automatic-steel/1.webp",
      "https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=800&auto=format&fit=crop&q=80",
      "https://images.unsplash.com/photo-1524592094714-0f0654e20314?w=800&auto=format&fit=crop&q=80",
      "https://upload.wikimedia.org/wikipedia/commons/9/91/Apple_Watch_Ultra_-_2.jpg"
  );

  private static final List<String> CAMERA_POOL = List.of(
      "https://images.unsplash.com/photo-1516035069371-29a1b244cc32?w=800&auto=format&fit=crop&q=80",
      "https://images.unsplash.com/photo-1502920917128-1aa500764cbd?w=800&auto=format&fit=crop&q=80",
      "https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?w=800&auto=format&fit=crop&q=80",
      "https://thumb.wikimedia.org/wikipedia/commons/thumb/c/c2/DJI_Osmo_Pocket.jpg/960px-DJI_Osmo_Pocket.jpg",
      "https://thumb.wikimedia.org/wikipedia/commons/thumb/6/67/Elgato_Stream_Deck_%2B.jpg/960px-Elgato_Stream_Deck_%2B.jpg"
  );

  /**
   * Resolves a unique product-accurate photograph for a built-in SKU ID, product title,
   * or arbitrary custom search query (50+ distinct verified photos across keyword pools).
   */
  public static String defaultProductImage(String productId, String category) {
    if (productId != null) {
      String exact = PRODUCT_IMAGES.get(productId);
      if (exact != null) {
        return exact;
      }
    }
    return resolveProductImage(productId, category);
  }

  public static String resolveProductImage(String titleOrQuery, String categoryHint) {
    String q = titleOrQuery == null ? "" : titleOrQuery.trim().toLowerCase(Locale.ROOT);
    if (q.isEmpty()) {
      return categoryPool(categoryHint).getFirst();
    }
    String exactKey = PRODUCT_IMAGES.get(q);
    if (exactKey != null) {
      return exactKey;
    }

    // Check close match against curated catalog items (helps WatchResponse & named queries)
    List<String> tokens = tokenize(q);
    if (!tokens.isEmpty()) {
      SeedProductSpec bestMatch = null;
      int bestScore = 0;
      for (SeedProductSpec spec : PRODUCTS) {
        String titleLower = spec.title().toLowerCase(Locale.ROOT);
        int hits = 0;
        for (String t : tokens) {
          if (titleLower.contains(t)) {
            hits++;
          }
        }
        if (hits >= 2 && hits > bestScore) {
          bestScore = hits;
          bestMatch = spec;
        }
      }
      if (bestMatch != null && PRODUCT_IMAGES.containsKey(bestMatch.id())) {
        return PRODUCT_IMAGES.get(bestMatch.id());
      }
    }

    // Keyword-targeted sub-selection before deterministic query hashing
    if (q.contains("airpod") || q.contains("earbud")) return AUDIO_POOL.get(0);
    if (q.contains("speaker") || q.contains("homepod") || q.contains("echo")) return AUDIO_POOL.get(3);
    if (q.contains("ipad") || q.contains("tablet")) return PHONE_TABLET_POOL.get(2);
    if (q.contains("monitor") || q.contains("display")) return LAPTOP_MONITOR_POOL.get(5);
    if (q.contains("playstation") || q.contains("ps5")) return GAMING_POOL.get(4);
    if (q.contains("xbox")) return GAMING_POOL.get(5);
    if (q.contains("switch") || q.contains("nintendo")) return GAMING_POOL.get(2);
    if (q.contains("quest") || q.contains("vr")) return GAMING_POOL.get(6);
    if (q.contains("controller") || q.contains("dualsense")) return GAMING_POOL.get(1);
    if (q.contains("perfume") || q.contains("cologne") || q.contains("fragrance")) return FASHION_POOL.get(4);
    if (q.contains("sunglass") || q.contains("ray-ban")) return FASHION_POOL.get(5);
    if (q.contains("bag") || q.contains("backpack")) return FASHION_POOL.get(3);
    if (q.contains("vacuum") || q.contains("dyson")) return HOME_KITCHEN_POOL.get(3);
    if (q.contains("coffee") || q.contains("espresso")) return HOME_KITCHEN_POOL.get(4);
    if (q.contains("mixer") || q.contains("blender") || q.contains("creami")) return HOME_KITCHEN_POOL.get(1);
    if (q.contains("drone") || q.contains("dji")) return CAMERA_POOL.get(3);
    if (q.contains("gopro") || q.contains("action")) return CAMERA_POOL.get(1);
    if (q.contains("stream") || q.contains("elgato")) return CAMERA_POOL.get(4);

    String resolvedCat = (categoryHint != null && !categoryHint.isBlank()) ? categoryHint : inferCategory(q);
    List<String> pool = categoryPool(resolvedCat);
    int idx = Math.floorMod(q.hashCode(), pool.size());
    return pool.get(idx);
  }

  private static List<String> categoryPool(String category) {
    String c = category == null ? "" : category.toLowerCase(Locale.ROOT);
    if (c.contains("audio")) return AUDIO_POOL;
    if (c.contains("phone") || c.contains("tablet")) return PHONE_TABLET_POOL;
    if (c.contains("laptop")) return LAPTOP_MONITOR_POOL;
    if (c.contains("gaming")) return GAMING_POOL;
    if (c.contains("sneaker") || c.contains("fashion")) return FASHION_POOL;
    if (c.contains("home") || c.contains("kitchen")) return HOME_KITCHEN_POOL;
    if (c.contains("watch")) return WATCH_POOL;
    if (c.contains("camera")) return CAMERA_POOL;
    return LAPTOP_MONITOR_POOL;
  }

  private static MarketplaceProductDto toMarketplaceProduct(SeedProductSpec spec, Instant now) {
    List<Channel3ProductOfferDto> list = toOfferDtos(spec, now);
    List<Channel3ProductOfferDto> sorted = list.stream()
        .sorted(Comparator.comparing(Channel3ProductOfferDto::currentPrice))
        .toList();
    Channel3ProductOfferDto best = sorted.getFirst();
    BigDecimal baseline = BigDecimal.valueOf(spec.baselinePrice()).setScale(2, RoundingMode.HALF_UP);
    BigDecimal savings = baseline.subtract(best.currentPrice()).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    BigDecimal savingsPct = baseline.compareTo(BigDecimal.ZERO) > 0
        ? savings.multiply(new BigDecimal("100")).divide(baseline, 1, RoundingMode.HALF_UP)
        : BigDecimal.ZERO;

    return new MarketplaceProductDto(
        spec.id(),
        spec.title(),
        spec.brand(),
        spec.category(),
        spec.description(),
        best.imageUrl(),
        best.currentPrice(),
        baseline,
        savings,
        savingsPct,
        best.merchantName(),
        best.merchantDomain(),
        sorted.size(),
        sorted);
  }

  private static List<Channel3ProductOfferDto> toOfferDtos(SeedProductSpec spec, Instant now) {
    BigDecimal baseline = BigDecimal.valueOf(spec.baselinePrice()).setScale(2, RoundingMode.HALF_UP);
    String img = defaultProductImage(spec.id(), spec.category());
    List<Channel3ProductOfferDto> dtos = new ArrayList<>();
    for (int i = 0; i < spec.offers().size(); i++) {
      SeedOfferSpec offer = spec.offers().get(i);
      BigDecimal current = BigDecimal.valueOf(offer.price()).setScale(2, RoundingMode.HALF_UP);
      BigDecimal savings = baseline.subtract(current).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
      BigDecimal savingsPct = baseline.compareTo(BigDecimal.ZERO) > 0
          ? savings.multiply(new BigDecimal("100")).divide(baseline, 2, RoundingMode.HALF_UP)
          : BigDecimal.ZERO;
      dtos.add(new Channel3ProductOfferDto(
          spec.id() + "-" + offer.merchantDomain().replace('.', '-'),
          spec.title(),
          spec.description(),
          spec.brand(),
          spec.category(),
          offer.merchantName(),
          offer.merchantDomain(),
          current,
          baseline,
          savings,
          savingsPct,
          "USD",
          "https://" + offer.merchantDomain() + "/dp/" + spec.id(),
          img,
          "IN_STOCK",
          offer.condition(),
          BigDecimal.valueOf(offer.commissionPercent()).setScale(2, RoundingMode.HALF_UP),
          offer.sourceTag(),
          now,
          offer.sellerRating(),
          offer.sellerReviewCount(),
          offer.verifiedSellerHistory(),
          offer.negativeRedFlagCount(),
          offer.sellerRedFlagSummary(),
          offer.estimatedDelivery()));
    }
    return dtos;
  }

  private static Channel3ProductOfferDto buildSynthesizedOffer(
      String id,
      String title,
      String brand,
      String category,
      String description,
      String merchantName,
      String merchantDomain,
      BigDecimal price,
      BigDecimal baseline,
      BigDecimal commission,
      String imageUrl,
      String source,
      Instant now) {
    BigDecimal savings = baseline.subtract(price).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    BigDecimal pct = baseline.compareTo(BigDecimal.ZERO) > 0
        ? savings.multiply(new BigDecimal("100")).divide(baseline, 2, RoundingMode.HALF_UP)
        : BigDecimal.ZERO;
    boolean risky = merchantDomain != null && (merchantDomain.contains("bargain") || merchantDomain.contains("liquidator") || merchantDomain.contains("outlet"));
    return new Channel3ProductOfferDto(
        id,
        title,
        description,
        brand,
        category,
        merchantName,
        merchantDomain,
        price,
        baseline,
        savings,
        pct,
        "USD",
        "https://" + merchantDomain + "/product/" + slugify(title),
        imageUrl,
        "IN_STOCK",
        risky ? "open-box" : "new",
        commission,
        source,
        now,
        risky ? 2.9 : 4.8,
        risky ? 21 : 1240,
        !risky,
        risky ? 8 : 0,
        risky ? "Multiple buyer reports of open-box / unverified inventory disputes" : "None — Verified Authentic Merchant",
        risky ? "7–14 Days" : "Arriving in 2 Days");
  }

  private static SeedProductSpec product(
      String id,
      String title,
      String brand,
      String category,
      String description,
      double baseline,
      List<SeedOfferSpec> offers) {
    return new SeedProductSpec(id, title, brand, category, description, baseline, offers);
  }

  private static List<SeedOfferSpec> offers(
      String m1, String d1, double p1, double c1,
      String m2, String d2, double p2, double c2,
      String m3, String d3, double p3, double c3,
      String m4, String d4, double p4, double c4) {
    return List.of(
        new SeedOfferSpec(m1, d1, p1, c1, "new", "marketplace-catalog"),
        new SeedOfferSpec(m2, d2, p2, c2, "new", "marketplace-catalog"),
        new SeedOfferSpec(m3, d3, p3, c3, "new", "marketplace-catalog"),
        new SeedOfferSpec(m4, d4, p4, c4, d4.contains("outlet") || d4.contains("resale") || d4.contains("deals") || d4.contains("graymarket") ? "open-box" : "new", "marketplace-catalog"));
  }

  private static String canonicalKey(Channel3ProductOfferDto offer) {
    if (offer.title() != null && !offer.title().isBlank()) {
      return offer.title().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    }
    return offer.channel3ProductId();
  }

  private static String normalizeCategory(String category) {
    if (category == null || category.isBlank() || category.equalsIgnoreCase("all")) {
      return null;
    }
    String lower = category.trim().toLowerCase(Locale.ROOT);
    for (String cat : CATEGORIES) {
      if (cat.toLowerCase(Locale.ROOT).equals(lower) || cat.toLowerCase(Locale.ROOT).contains(lower)) {
        return cat;
      }
    }
    return category.trim();
  }

  private static boolean matchesSpec(SeedProductSpec spec, List<String> tokens, String fullQuery) {
    String haystack = (spec.title() + " " + spec.brand() + " " + spec.category() + " " + spec.description())
        .toLowerCase(Locale.ROOT);
    if (haystack.contains(fullQuery)) {
      return true;
    }
    int matched = 0;
    for (String token : tokens) {
      if (haystack.contains(token)) {
        matched++;
      }
    }
    // Require all significant tokens if <= 2 tokens, or at least 2 matching tokens for longer searches
    return tokens.size() <= 2 ? matched == tokens.size() : matched >= 2;
  }

  private static List<String> tokenize(String query) {
    if (query == null || query.isBlank()) {
      return List.of();
    }
    String[] parts = query.split("\\s+");
    List<String> tokens = new ArrayList<>();
    for (String part : parts) {
      String clean = part.replaceAll("[^a-z0-9]", "");
      if (clean.length() >= 2 || clean.matches("\\d+")) {
        tokens.add(clean);
      }
    }
    return tokens;
  }

  private static String inferCategory(String query) {
    String q = query.toLowerCase(Locale.ROOT);
    if (q.contains("headphone") || q.contains("earbud") || q.contains("airpod") || q.contains("speaker") || q.contains("bose") || q.contains("sennheiser")) {
      return "Audio";
    }
    if (q.contains("iphone") || q.contains("samsung") || q.contains("galaxy") || q.contains("pixel") || q.contains("ipad") || q.contains("phone")) {
      return "Phones & Tablets";
    }
    if (q.contains("macbook") || q.contains("laptop") || q.contains("thinkpad") || q.contains("monitor") || q.contains("dell") || q.contains("xps") || q.contains("mac mini")) {
      return "Laptops";
    }
    if (q.contains("ps5") || q.contains("playstation") || q.contains("xbox") || q.contains("nintendo") || q.contains("switch") || q.contains("steam") || q.contains("quest") || q.contains("controller")) {
      return "Gaming";
    }
    if (q.contains("nike") || q.contains("jordan") || q.contains("adidas") || q.contains("sneaker") || q.contains("shoe") || q.contains("bag") || q.contains("sunglass")) {
      return "Sneakers & Fashion";
    }
    if (q.contains("dyson") || q.contains("coffee") || q.contains("espresso") || q.contains("kitchen") || q.contains("vacuum") || q.contains("air fryer") || q.contains("blender")) {
      return "Home & Kitchen";
    }
    if (q.contains("watch") || q.contains("garmin") || q.contains("rolex") || q.contains("casio") || q.contains("seiko") || q.contains("tissot") || q.contains("g-shock")) {
      return "Watches";
    }
    if (q.contains("camera") || q.contains("gopro") || q.contains("sony zv") || q.contains("dji") || q.contains("fujifilm") || q.contains("canon") || q.contains("nikon")) {
      return "Cameras";
    }
    return "Laptops";
  }

  private static String inferBrand(String title) {
    String[] words = title.trim().split("\\s+");
    return words.length > 0 ? words[0] : "Generic";
  }

  private static BigDecimal estimateRetailPrice(String query, String category) {
    String q = query.toLowerCase(Locale.ROOT);
    if (q.contains("macbook") || q.contains("iphone") || q.contains("pro max") || q.contains("oled") || q.contains("camera")) {
      return new BigDecimal("1199.00");
    }
    if (q.contains("mouse") || q.contains("keyboard") || q.contains("kindle") || q.contains("speaker") || q.contains("charger")) {
      return new BigDecimal("119.99");
    }
    return switch (category) {
      case "Laptops" -> new BigDecimal("1099.00");
      case "Phones & Tablets" -> new BigDecimal("899.00");
      case "Gaming" -> new BigDecimal("399.99");
      case "Cameras" -> new BigDecimal("649.00");
      case "Home & Kitchen" -> new BigDecimal("299.99");
      case "Watches" -> new BigDecimal("349.00");
      case "Sneakers & Fashion" -> new BigDecimal("149.99");
      default -> new BigDecimal("229.99");
    };
  }

  private static String toTitleCase(String input) {
    String[] parts = input.trim().split("\\s+");
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      String p = parts[i];
      if (p.isEmpty()) continue;
      if (i > 0) sb.append(' ');
      if (p.length() <= 3 && p.equals(p.toUpperCase(Locale.ROOT))) {
        sb.append(p);
      } else {
        sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
      }
    }
    return sb.toString();
  }

  private static String slugify(String input) {
    String slug = input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    return slug.isEmpty() ? "item" : slug.substring(0, Math.min(slug.length(), 40));
  }
}

