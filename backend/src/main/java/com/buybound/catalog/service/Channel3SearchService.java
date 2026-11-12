package com.buybound.catalog.service;

import com.buybound.ai.service.AiShoppingAgentService;
import com.buybound.catalog.client.Channel3Client;
import com.buybound.catalog.client.Channel3Client.OfferApi;
import com.buybound.catalog.client.Channel3Client.PriceHistoryApiResponse;
import com.buybound.catalog.client.Channel3Client.ProductApi;
import com.buybound.catalog.client.Channel3Client.SearchApiResponse;
import com.buybound.catalog.dto.Channel3ProductOfferDto;
import com.buybound.catalog.dto.Channel3SearchResponse;
import com.buybound.catalog.dto.MarketplaceProductDto;
import com.buybound.catalog.dto.MarketplaceResponse;
import com.buybound.common.exception.ExternalServiceException;
import com.buybound.watch.domain.Watch;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Live Channel3 Commerce discovery, multi-category marketplace catalog, & dynamic catalog synthesizer.
 */
@Service
public class Channel3SearchService {
  private static final Logger log = LoggerFactory.getLogger(Channel3SearchService.class);
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final HttpClient IMAGE_HTTP = HttpClient.newBuilder()
      .connectTimeout(Duration.ofMillis(1800))
      .build();

  private static final Set<String> VERIFIED_TIER1_RETAIL_DOMAINS = Set.of(
      "nike.com", "footlocker.com", "goat.com", "stockx.com", "bestbuy.com",
      "ebay.com", "adidas.com", "target.com", "walmart.com", "macys.com",
      "adorama.com", "bhphotovideo.com", "amazon.com", "apple.com", "flightclub.com",
      "nordstrom.com", "costco.com");
  private static final List<String> RISKY_MERCHANT_KEYWORDS = List.of(
      "scam", "replica", "cheap", "unverified", "liquidation", "liquidator",
      "drop-king", "graymarket", "shady");

  public record MerchantReputation(
      double sellerRating,
      int sellerReviewCount,
      boolean verifiedSellerHistory,
      int negativeRedFlagCount,
      String sellerRedFlagSummary,
      String estimatedDelivery) {}

  /**
   * Deterministically resolves seller trust telemetry for live Channel3 merchant domains
   * instead of assuming every upstream offer is a verified 4.8-star seller.
   */
  public static MerchantReputation resolveMerchantReputation(
      String rawDomain, Collection<String> allowedMerchants) {
    String domain = rawDomain == null ? "" : rawDomain.trim().toLowerCase(Locale.ROOT);
    if (domain.startsWith("www.")) {
      domain = domain.substring(4);
    }

    String finalDomain = domain;
    boolean hasRiskyKeyword = RISKY_MERCHANT_KEYWORDS.stream().anyMatch(finalDomain::contains);
    if (hasRiskyKeyword || finalDomain.isBlank()) {
      return new MerchantReputation(
          2.9, 18, false, 3,
          "High-risk / unverified merchant domain flags (" + (finalDomain.isBlank() ? "unknown" : finalDomain) + ")",
          "7–14 Days (Unverified)");
    }

    boolean inAllowlist = allowedMerchants != null && allowedMerchants.stream()
        .filter(m -> m != null && !m.isBlank())
        .map(m -> m.trim().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", ""))
        .anyMatch(m -> finalDomain.equals(m) || finalDomain.endsWith("." + m));
    boolean tier1Retail = VERIFIED_TIER1_RETAIL_DOMAINS.stream()
        .anyMatch(t -> finalDomain.equals(t) || finalDomain.endsWith("." + t));

    if (inAllowlist || tier1Retail) {
      return new MerchantReputation(
          4.8, 1420, true, 0,
          "None — Verified Authorized Merchant",
          "Arriving in 2 Days");
    }

    return new MerchantReputation(
        4.1, 64, false, 0,
        "Unverified third-party domain (" + finalDomain + ") outside store allowlist",
        "3–7 Business Days");
  }

  private static final Set<String> IMAGE_MATCH_STOP_WORDS = Set.of(
      "the", "and", "for", "with", "edition", "inch", "new", "black", "white", "silver", "space", "gray", "grey");
  private static final List<String> IMAGE_MISMATCH_TERMS = List.of(
      "lash", "mascara", "eyeliner", "case only", "replacement band", "screen protector", "sticker", "decal");

  private final Channel3Client client;
  private final AiShoppingAgentService aiService;
  private final Function<String, Optional<String>> livePublicImageLookup;
  private final ConcurrentHashMap<String, String> dynamicImageCache = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> liveCatalogImages = new ConcurrentHashMap<>();

  public Channel3SearchService(Channel3Client client) {
    this(client, null);
  }

  @Autowired
  public Channel3SearchService(
      Channel3Client client,
      @Autowired(required = false) AiShoppingAgentService aiService) {
    this(client, aiService, Channel3SearchService::fetchDummyJsonProductImage);
  }

  public Channel3SearchService(
      Channel3Client client,
      AiShoppingAgentService aiService,
      Function<String, Optional<String>> livePublicImageLookup) {
    this.client = client;
    this.aiService = aiService;
    this.livePublicImageLookup = livePublicImageLookup != null
        ? livePublicImageLookup
        : q -> Optional.empty();
  }

  public Channel3SearchResponse searchOffersForWatch(Watch watch) {
    return searchOffersForWatch(watch, null, false);
  }

  public Channel3SearchResponse searchOffersForWatch(Watch watch, BigDecimal mandateMaxCeiling, boolean simulateDrop) {
    return searchOffersForWatch(watch, mandateMaxCeiling, simulateDrop, null);
  }

  public Channel3SearchResponse searchOffersForWatch(
      Watch watch, BigDecimal mandateMaxCeiling, boolean simulateDrop, Collection<String> mandateAllowedMerchants) {
    String query = buildQuery(watch);
    Instant now = Instant.now();
    if (simulateDrop) {
      String preferredMerchant = watch.getMerchantId();
      List<Channel3ProductOfferDto> simulatedOffers = applyLiveCatalogImages(
          BuiltInMarketplaceCatalog.dynamicDropOffersForWatch(
              watch.getProductName(),
              watch.getSku(),
              preferredMerchant,
              watch.getTargetPrice(),
              mandateMaxCeiling,
              watch.getBaselineMarketPrice(),
              now));
      if (!simulatedOffers.isEmpty()) {
        BigDecimal baseline = simulatedOffers.stream()
            .map(Channel3ProductOfferDto::baselinePrice)
            .filter(b -> b != null && b.compareTo(BigDecimal.ZERO) > 0)
            .max(BigDecimal::compareTo)
            .orElse(watch.getBaselineMarketPrice());
        return new Channel3SearchResponse(
            query,
            simulatedOffers.getFirst().title(),
            simulatedOffers.getFirst().channel3ProductId(),
            baseline,
            simulatedOffers,
            now);
      }
    }
    if (BuiltInMarketplaceCatalog.isFlagshipOvernightDropQuery(query, watch.getSku())) {
      List<Channel3ProductOfferDto> flagshipOffers = applyLiveCatalogImages(
          BuiltInMarketplaceCatalog.flagshipOvernightDropOffers(now));
      if (!flagshipOffers.isEmpty()) {
        BigDecimal baseline = flagshipOffers.stream()
            .map(Channel3ProductOfferDto::baselinePrice)
            .filter(b -> b != null && b.compareTo(BigDecimal.ZERO) > 0)
            .max(BigDecimal::compareTo)
            .orElse(watch.getBaselineMarketPrice());
        return new Channel3SearchResponse(
            query,
            flagshipOffers.getFirst().title(),
            flagshipOffers.getFirst().channel3ProductId(),
            baseline,
            flagshipOffers,
            now);
      }
    }
    List<String> preferredAllowed = new ArrayList<>();
    if (mandateAllowedMerchants != null) {
      preferredAllowed.addAll(mandateAllowedMerchants);
    }
    if (watch.getMerchantId() != null && !watch.getMerchantId().isBlank()) {
      preferredAllowed.add(watch.getMerchantId());
    }
    return searchByQueryWithAllowed(query, null, watch.getBaselineMarketPrice(), 10, preferredAllowed);
  }

  public Channel3SearchResponse searchByQuery(String query, BigDecimal watchBaseline, int limit) {
    return searchByQuery(query, null, watchBaseline, limit);
  }

  public Channel3SearchResponse searchByQuery(String query, String category, BigDecimal watchBaseline, int limit) {
    return searchByQueryWithAllowed(query, category, watchBaseline, limit, List.of());
  }

  private Channel3SearchResponse searchByQueryWithAllowed(
      String query, String category, BigDecimal watchBaseline, int limit, Collection<String> allowedMerchants) {
    Instant now = Instant.now();
    Channel3SearchResponse live = tryLiveChannel3Search(query, watchBaseline, limit, now, allowedMerchants);
    if (live != null && !live.offers().isEmpty()) {
      return live;
    }

    // Fallback tier 1: built-in 44-product multi-category catalog (overlaid with live Channel3 photos)
    List<Channel3ProductOfferDto> offers = applyLiveCatalogImages(
        BuiltInMarketplaceCatalog.searchOffers(query, category, now));
    if (offers.isEmpty()) {
      List<Channel3ProductOfferDto> aiOffers = aiService != null
          ? aiService.synthesizeOffersForQuery(query, watchBaseline, now).orElse(List.of())
          : List.of();
      List<Channel3ProductOfferDto> raw = !aiOffers.isEmpty()
          ? aiOffers
          : BuiltInMarketplaceCatalog.synthesizeForQuery(query, watchBaseline, now);
      offers = resolveDynamicImages(raw, query);
    }

    List<Channel3ProductOfferDto> sorted = offers.stream()
        .sorted(Comparator.comparing(Channel3ProductOfferDto::currentPrice))
        .toList();
    String primaryTitle = sorted.isEmpty() ? query : sorted.getFirst().title();
    String primaryId = sorted.isEmpty() ? null : sorted.getFirst().channel3ProductId();
    BigDecimal globalBaseline = sorted.stream()
        .map(Channel3ProductOfferDto::baselinePrice)
        .filter(b -> b != null && b.compareTo(BigDecimal.ZERO) > 0)
        .max(BigDecimal::compareTo)
        .orElse(watchBaseline);
    return new Channel3SearchResponse(query, primaryTitle, primaryId, globalBaseline, sorted, now);
  }

  /**
   * Returns multi-category marketplace products grouped by canonical product with arbitrary-query search support.
   */
  public MarketplaceResponse browseMarketplace(
      String category,
      String query,
      BigDecimal minPrice,
      BigDecimal maxPrice,
      String sort,
      int limit) {
    Instant now = Instant.now();
    String cleanQuery = query == null ? "" : query.trim();

    if (cleanQuery.isBlank()) {
      List<Channel3ProductOfferDto> catalogOffers = applyLiveCatalogImages(
          BuiltInMarketplaceCatalog.searchOffers("", category, now));
      List<MarketplaceProductDto> grouped = BuiltInMarketplaceCatalog
          .groupOffersIntoProducts(catalogOffers, minPrice, maxPrice, sort)
          .stream()
          .limit(Math.max(1, limit))
          .toList();
      return new MarketplaceResponse(
          grouped,
          BuiltInMarketplaceCatalog.CATEGORIES,
          grouped.size(),
          "marketplace-catalog",
          "");
    }

    List<Channel3ProductOfferDto> catalogOffers = applyLiveCatalogImages(
        BuiltInMarketplaceCatalog.searchOffers(cleanQuery, category, now));
    Channel3SearchResponse liveResponse = tryLiveChannel3Search(cleanQuery, null, Math.max(8, Math.min(limit, 15)), now);
    List<Channel3ProductOfferDto> liveOffers = liveResponse != null
        ? ensureOfferImages(liveResponse.offers(), cleanQuery)
        : List.of();

    Map<String, Channel3ProductOfferDto> merged = new LinkedHashMap<>();
    String source;
    for (Channel3ProductOfferDto offer : catalogOffers) {
      merged.put(dedupeKey(offer), offer);
    }
    for (Channel3ProductOfferDto offer : liveOffers) {
      merged.putIfAbsent(dedupeKey(offer), offer);
    }

    if (!merged.isEmpty()) {
      source = liveOffers.isEmpty()
          ? "marketplace-catalog"
          : (catalogOffers.isEmpty() ? "channel3" : "channel3+marketplace-catalog");
    } else {
      List<Channel3ProductOfferDto> synthesized = aiService != null
          ? aiService.synthesizeOffersForQuery(cleanQuery, null, now).orElse(List.of())
          : List.of();
      if (!synthesized.isEmpty()) {
        source = "ai-synthesizer";
      } else {
        synthesized = BuiltInMarketplaceCatalog.synthesizeForQuery(cleanQuery, null, now);
        source = "smart-catalog-synthesizer";
      }
      synthesized = resolveDynamicImages(synthesized, cleanQuery);
      for (Channel3ProductOfferDto offer : synthesized) {
        merged.put(dedupeKey(offer), offer);
      }
    }

    List<MarketplaceProductDto> products = BuiltInMarketplaceCatalog
        .groupOffersIntoProducts(new ArrayList<>(merged.values()), minPrice, maxPrice, sort)
        .stream()
        .limit(Math.max(1, limit))
        .toList();

    return new MarketplaceResponse(
        products,
        BuiltInMarketplaceCatalog.CATEGORIES,
        products.size(),
        source,
        cleanQuery);
  }

  private Channel3SearchResponse tryLiveChannel3Search(
      String query, BigDecimal watchBaseline, int limit, Instant now) {
    return tryLiveChannel3Search(query, watchBaseline, limit, now, List.of());
  }

  private Channel3SearchResponse tryLiveChannel3Search(
      String query, BigDecimal watchBaseline, int limit, Instant now, Collection<String> allowedMerchants) {
    try {
      SearchApiResponse apiResponse = client.search(query, limit);
      if (apiResponse == null || apiResponse.products == null || apiResponse.products.isEmpty()) {
        return null;
      }
      List<ProductApi> products = apiResponse.products;

      // Fetch historical price baseline for the top canonical product to substantiate market baseline.
      Map<String, BigDecimal> productBaselines = new HashMap<>();
      String primaryId = products.get(0).id;
      String primaryTitle = products.get(0).title;
      for (int i = 0; i < Math.min(products.size(), 3); i++) {
        ProductApi p = products.get(i);
        PriceHistoryApiResponse history = client.getPriceHistory(p.id);
        if (history != null && history.statistics != null) {
          BigDecimal histMax = history.statistics.maxPrice;
          BigDecimal histMean = history.statistics.mean;
          BigDecimal bestHist = histMax != null ? histMax : histMean;
          if (bestHist != null) {
            productBaselines.put(p.id, bestHist.setScale(2, RoundingMode.HALF_UP));
          }
        }
      }

      // Also compute highest observed offer price across returned listings as an empirical market reference.
      BigDecimal maxObservedMarketPrice = products.stream()
          .filter(p -> p.offers != null)
          .flatMap(p -> p.offers.stream())
          .filter(o -> o.price != null && o.price.price != null)
          .map(o -> {
            BigDecimal cmp = o.price.compareAtPrice;
            return (cmp != null && cmp.compareTo(o.price.price) > 0) ? cmp : o.price.price;
          })
          .max(BigDecimal::compareTo)
          .orElse(null);

      BigDecimal globalBaseline = watchBaseline;
      if (maxObservedMarketPrice != null && (globalBaseline == null || maxObservedMarketPrice.compareTo(globalBaseline) > 0)) {
        globalBaseline = maxObservedMarketPrice.setScale(2, RoundingMode.HALF_UP);
      }

      List<Channel3ProductOfferDto> flattened = new ArrayList<>();
      for (ProductApi p : products) {
        if (p.offers == null || p.offers.isEmpty()) continue;
        String brand = (p.brands != null && !p.brands.isEmpty()) ? p.brands.get(0).name : null;
        String cat = p.category != null ? p.category.title : null;
        String imageUrl = extractImageUrl(p);
        BigDecimal histBaseline = productBaselines.get(p.id);

        for (OfferApi o : p.offers) {
          if (o.price == null || o.price.price == null) continue;
          BigDecimal currentPrice = o.price.price.setScale(2, RoundingMode.HALF_UP);
          BigDecimal baseline = resolveBaseline(o.price.compareAtPrice, histBaseline, globalBaseline, currentPrice);
          BigDecimal savings = baseline.subtract(currentPrice).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
          BigDecimal savingsPct = baseline.compareTo(BigDecimal.ZERO) > 0
              ? savings.multiply(new BigDecimal("100")).divide(baseline, 2, RoundingMode.HALF_UP)
              : BigDecimal.ZERO;
          BigDecimal commissionPct = o.maxCommissionRate != null
              ? o.maxCommissionRate.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP)
              : BigDecimal.ZERO;

          String domain = normalizeDomain(o.domain);
          String merchantName = formatMerchantName(domain);
          String currency = o.price.currency != null ? o.price.currency.toUpperCase(Locale.ROOT) : "USD";
          MerchantReputation rep = resolveMerchantReputation(domain, allowedMerchants);

          flattened.add(new Channel3ProductOfferDto(
              p.id,
              p.title,
              p.description,
              brand,
              cat,
              merchantName,
              domain,
              currentPrice,
              baseline,
              savings,
              savingsPct,
              currency,
              o.url,
              imageUrl,
              o.availability != null ? o.availability : "InStock",
              o.condition != null ? o.condition : "new",
              commissionPct,
              "channel3",
              now,
              rep.sellerRating(),
              rep.sellerReviewCount(),
              rep.verifiedSellerHistory(),
              rep.negativeRedFlagCount(),
              rep.sellerRedFlagSummary(),
              rep.estimatedDelivery()));
        }
      }

      if (flattened.isEmpty()) {
        return null;
      }
      flattened.sort(Comparator.comparing(Channel3ProductOfferDto::currentPrice));
      return new Channel3SearchResponse(query, primaryTitle, primaryId, globalBaseline, flattened, now);
    } catch (ExternalServiceException ex) {
      log.debug("Channel3 live search unavailable for '{}', using catalog fallback: {}", query, ex.getMessage());
      return null;
    }
  }

  public Channel3SearchResponse lookupByUrl(String productUrl) {
    Instant now = Instant.now();
    try {
      ProductApi p = client.lookupByUrl(productUrl);
      if (p == null) {
        return synthesizeUrlLookup(productUrl, now);
      }
      PriceHistoryApiResponse history = client.getPriceHistory(p.id);
      BigDecimal histBaseline = (history != null && history.statistics != null)
          ? (history.statistics.maxPrice != null ? history.statistics.maxPrice : history.statistics.mean)
          : null;

      String brand = (p.brands != null && !p.brands.isEmpty()) ? p.brands.get(0).name : null;
      String category = p.category != null ? p.category.title : null;
      String imageUrl = extractImageUrl(p);

      List<Channel3ProductOfferDto> offers = new ArrayList<>();
      BigDecimal bestBaseline = histBaseline != null ? histBaseline.setScale(2, RoundingMode.HALF_UP) : null;

      if (p.offers != null) {
        for (OfferApi o : p.offers) {
          if (o.price == null || o.price.price == null) continue;
          BigDecimal currentPrice = o.price.price.setScale(2, RoundingMode.HALF_UP);
          BigDecimal baseline = resolveBaseline(o.price.compareAtPrice, histBaseline, currentPrice, currentPrice);
          if (bestBaseline == null || baseline.compareTo(bestBaseline) > 0) {
            bestBaseline = baseline;
          }
          BigDecimal savings = baseline.subtract(currentPrice).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
          BigDecimal savingsPct = baseline.compareTo(BigDecimal.ZERO) > 0
              ? savings.multiply(new BigDecimal("100")).divide(baseline, 2, RoundingMode.HALF_UP)
              : BigDecimal.ZERO;
          BigDecimal commissionPct = o.maxCommissionRate != null
              ? o.maxCommissionRate.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP)
              : BigDecimal.ZERO;
          String domain = normalizeDomain(o.domain);
          MerchantReputation rep = resolveMerchantReputation(domain, List.of());
          offers.add(new Channel3ProductOfferDto(
              p.id,
              p.title,
              p.description,
              brand,
              category,
              formatMerchantName(domain),
              domain,
              currentPrice,
              baseline,
              savings,
              savingsPct,
              o.price.currency != null ? o.price.currency.toUpperCase(Locale.ROOT) : "USD",
              o.url != null ? o.url : productUrl,
              imageUrl,
              o.availability != null ? o.availability : "InStock",
              o.condition != null ? o.condition : "new",
              commissionPct,
              "channel3_lookup",
              now,
              rep.sellerRating(),
              rep.sellerReviewCount(),
              rep.verifiedSellerHistory(),
              rep.negativeRedFlagCount(),
              rep.sellerRedFlagSummary(),
              rep.estimatedDelivery()));
        }
      }

      if (offers.isEmpty()) {
        return synthesizeUrlLookup(productUrl, now);
      }
      offers.sort(Comparator.comparing(Channel3ProductOfferDto::currentPrice));
      return new Channel3SearchResponse(productUrl, p.title, p.id, bestBaseline, offers, now);
    } catch (ExternalServiceException ex) {
      log.debug("Channel3 lookupByUrl unavailable for '{}', using smart URL fallback: {}", productUrl, ex.getMessage());
      return synthesizeUrlLookup(productUrl, now);
    }
  }

  private Channel3SearchResponse synthesizeUrlLookup(String productUrl, Instant now) {
    String extractedQuery = extractProductHintFromUrl(productUrl);
    List<Channel3ProductOfferDto> catalogHits = BuiltInMarketplaceCatalog.searchOffers(extractedQuery, null, now);
    List<Channel3ProductOfferDto> offers = !catalogHits.isEmpty()
        ? catalogHits
        : resolveDynamicImages(
            BuiltInMarketplaceCatalog.synthesizeForQuery(extractedQuery, null, now),
            extractedQuery);
    List<Channel3ProductOfferDto> sorted = offers.stream()
        .sorted(Comparator.comparing(Channel3ProductOfferDto::currentPrice))
        .toList();
    String title = sorted.isEmpty() ? extractedQuery : sorted.getFirst().title();
    String id = sorted.isEmpty() ? "url-lookup" : sorted.getFirst().channel3ProductId();
    BigDecimal baseline = sorted.stream()
        .map(Channel3ProductOfferDto::baselinePrice)
        .filter(b -> b != null)
        .max(BigDecimal::compareTo)
        .orElse(null);
    return new Channel3SearchResponse(productUrl, title, id, baseline, sorted, now);
  }

  private String extractProductHintFromUrl(String rawUrl) {
    try {
      String path = URI.create(rawUrl).getPath();
      if (path != null && !path.isBlank()) {
        String[] segments = path.split("/");
        String best = "";
        for (String seg : segments) {
          String clean = seg.replace('-', ' ').replace('_', ' ').trim();
          if (clean.length() > best.length() && !clean.matches("[A-Za-z0-9]{10}")) {
            best = clean;
          }
        }
        if (!best.isBlank()) {
          return best;
        }
      }
    } catch (Exception ignored) {
      // fall through
    }
    return rawUrl;
  }

  private List<Channel3ProductOfferDto> ensureOfferImages(List<Channel3ProductOfferDto> offers, String queryHint) {
    List<Channel3ProductOfferDto> out = new ArrayList<>(offers.size());
    for (Channel3ProductOfferDto o : offers) {
      if (o.imageUrl() != null && !o.imageUrl().isBlank() && o.imageUrl().startsWith("https://")) {
        out.add(o);
      } else {
        String resolved = resolveDynamicImageForQuery(
            o.title() != null ? o.title() : queryHint,
            o.category());
        out.add(withImage(o, resolved));
      }
    }
    return out;
  }

  private List<Channel3ProductOfferDto> resolveDynamicImages(List<Channel3ProductOfferDto> offers, String query) {
    if (offers.isEmpty()) {
      return offers;
    }
    String firstTitle = offers.getFirst().title() != null ? offers.getFirst().title() : query;
    String category = offers.getFirst().category();
    String resolved = resolveDynamicImageForQuery(query != null && !query.isBlank() ? query : firstTitle, category);
    return offers.stream().map(o -> withImage(o, resolved)).toList();
  }

  /**
   * Gently refreshes built-in catalog photos from live Channel3 Commerce search after startup
   * whenever CHANNEL3_API_KEY is configured, validating brand & model tokens to avoid false positives.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void warmLiveCatalogImagesAsync() {
    if (client == null || !client.isConfigured()) {
      return;
    }
    Thread.ofVirtual().name("channel3-catalog-img-warm").start(() -> {
      int updated = 0;
      for (BuiltInMarketplaceCatalog.CatalogSeedSummary seed : BuiltInMarketplaceCatalog.catalogSeeds()) {
        try {
          Optional<String> liveUrl = fetchChannel3ProductImage(seed.title(), seed.brand());
          if (liveUrl.isPresent()) {
            liveCatalogImages.put(seed.id(), liveUrl.get());
            updated++;
          }
          Thread.sleep(120);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          return;
        } catch (Exception ex) {
          log.debug("Channel3 catalog image refresh skipped for {}: {}", seed.id(), ex.getMessage());
        }
      }
      log.info("Channel3 live product photo refresh completed ({}/{} products updated).",
          updated, BuiltInMarketplaceCatalog.catalogSeeds().size());
    });
  }

  private List<Channel3ProductOfferDto> applyLiveCatalogImages(List<Channel3ProductOfferDto> offers) {
    if (offers.isEmpty() || liveCatalogImages.isEmpty()) {
      return offers;
    }
    List<Channel3ProductOfferDto> out = new ArrayList<>(offers.size());
    for (Channel3ProductOfferDto o : offers) {
      String liveImg = o.channel3ProductId() != null ? liveCatalogImages.get(o.channel3ProductId()) : null;
      out.add((liveImg != null && liveImg.startsWith("https://")) ? withImage(o, liveImg) : o);
    }
    return out;
  }

  /**
   * Resolves a dynamic image using Priority 1 (live Channel3 search when configured),
   * Priority 2 (live DummyJSON search), then Priority 3 (verified studio photo pool),
   * memoized cleanly in dynamicImageCache.
   */
  public String resolveDynamicImageForQuery(String query, String categoryHint) {
    String clean = query == null ? "" : query.trim();
    if (clean.isEmpty()) {
      return BuiltInMarketplaceCatalog.resolveProductImage(query, categoryHint);
    }
    String cacheKey = clean.toLowerCase(Locale.ROOT) + "|" + (categoryHint == null ? "" : categoryHint.toLowerCase(Locale.ROOT));
    return dynamicImageCache.computeIfAbsent(cacheKey, ignored ->
        fetchChannel3ProductImage(clean, null)
            .or(() -> livePublicImageLookup.apply(clean).filter(url -> url.startsWith("https://")))
            .orElseGet(() -> BuiltInMarketplaceCatalog.resolveProductImage(clean, categoryHint)));
  }

  private Optional<String> fetchChannel3ProductImage(String query, String brandHint) {
    if (client == null || !client.isConfigured() || query == null || query.isBlank()) {
      return Optional.empty();
    }
    try {
      SearchApiResponse res = client.search(query, 5);
      if (res == null || res.products == null || res.products.isEmpty()) {
        return Optional.empty();
      }
      for (ProductApi p : res.products) {
        if (!isRelevantChannel3Match(query, brandHint, p)) {
          continue;
        }
        String img = extractImageUrl(p);
        if (img != null && img.startsWith("https://")) {
          return Optional.of(img);
        }
      }
    } catch (Exception ex) {
      log.debug("Channel3 live image lookup unavailable for '{}': {}", query, ex.getMessage());
    }
    return Optional.empty();
  }

  private boolean isRelevantChannel3Match(String query, String brandHint, ProductApi p) {
    if (p == null || p.title == null || p.title.isBlank()) {
      return false;
    }
    String qLower = query.toLowerCase(Locale.ROOT);
    String candBrand = (p.brands != null && !p.brands.isEmpty() && p.brands.getFirst().name != null)
        ? p.brands.getFirst().name.toLowerCase(Locale.ROOT)
        : "";
    String haystack = (p.title + " " + candBrand).toLowerCase(Locale.ROOT);

    for (String bad : IMAGE_MISMATCH_TERMS) {
      if (haystack.contains(bad) && !qLower.contains(bad)) {
        return false;
      }
    }

    if (brandHint != null && !brandHint.isBlank()) {
      String brandLower = brandHint.trim().toLowerCase(Locale.ROOT);
      if (!haystack.contains(brandLower)) {
        return false;
      }
    }

    List<String> tokens = new ArrayList<>();
    for (String tok : qLower.split("[^a-z0-9]+")) {
      if (tok.length() >= 2 && !IMAGE_MATCH_STOP_WORDS.contains(tok)) {
        tokens.add(tok);
      }
    }
    if (tokens.isEmpty()) {
      return true;
    }
    int hits = 0;
    for (String tok : tokens) {
      if (haystack.contains(tok)) {
        hits++;
      }
    }
    int required = tokens.size() >= 3 ? 2 : 1;
    return hits >= required;
  }

  private static Channel3ProductOfferDto withImage(Channel3ProductOfferDto o, String imageUrl) {
    return new Channel3ProductOfferDto(
        o.channel3ProductId(), o.title(), o.description(), o.brand(),
        o.category() != null ? o.category() : "Featured",
        o.merchantName(), o.merchantDomain(), o.currentPrice(), o.baselinePrice(),
        o.verifiedSavings(), o.savingsPercent(), o.currency(), o.productUrl(),
        imageUrl, o.availability(), o.condition(), o.commissionRatePercent(),
        o.source(), o.observedAt(),
        o.sellerRating(), o.sellerReviewCount(), o.verifiedSellerHistory(),
        o.negativeRedFlagCount(), o.sellerRedFlagSummary(), o.estimatedDelivery());
  }

  private static Optional<String> fetchDummyJsonProductImage(String query) {
    try {
      String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
      HttpRequest req = HttpRequest.newBuilder()
          .uri(URI.create("https://dummyjson.com/products/search?q=" + encoded + "&limit=5"))
          .timeout(Duration.ofSeconds(2))
          .header("Accept", "application/json")
          .GET()
          .build();
      HttpResponse<String> res = IMAGE_HTTP.send(req, HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() < 200 || res.statusCode() >= 300 || res.body() == null) {
        return Optional.empty();
      }
      JsonNode root = JSON.readTree(res.body());
      JsonNode products = root.path("products");
      if (!products.isArray() || products.isEmpty()) {
        return Optional.empty();
      }
      String[] queryTokens = query.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
      for (JsonNode p : products) {
        String title = p.path("title").asText("").toLowerCase(Locale.ROOT);
        String cat = p.path("category").asText("").toLowerCase(Locale.ROOT);
        String brand = p.path("brand").asText("").toLowerCase(Locale.ROOT);
        String haystack = title + " " + cat + " " + brand;
        boolean relevant = false;
        for (String tok : queryTokens) {
          if (tok.length() >= 3 && haystack.contains(tok)) {
            relevant = true;
            break;
          }
        }
        if (!relevant) {
          continue;
        }
        String thumb = p.path("thumbnail").asText("");
        if (thumb.startsWith("https://")) {
          return Optional.of(thumb);
        }
        JsonNode imgs = p.path("images");
        if (imgs.isArray() && !imgs.isEmpty()) {
          String first = imgs.get(0).asText("");
          if (first.startsWith("https://")) {
            return Optional.of(first);
          }
        }
      }
    } catch (Exception ignored) {
      // Offline or timeout — drop cleanly to Priority 3 distinct photo pool
    }
    return Optional.empty();
  }

  private String dedupeKey(Channel3ProductOfferDto o) {
    String t = o.title() == null ? "" : o.title().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    String d = o.merchantDomain() == null ? "" : o.merchantDomain().toLowerCase(Locale.ROOT);
    return t + "@" + d;
  }

  private String buildQuery(Watch watch) {
    StringBuilder sb = new StringBuilder(watch.getProductName());
    if (watch.getVariant() != null && !watch.getVariant().isBlank()
        && !watch.getProductName().toLowerCase(Locale.ROOT).contains(watch.getVariant().toLowerCase(Locale.ROOT))) {
      sb.append(" ").append(watch.getVariant());
    }
    return sb.toString().trim();
  }

  private BigDecimal resolveBaseline(BigDecimal compareAt, BigDecimal histBaseline, BigDecimal globalBaseline, BigDecimal currentPrice) {
    BigDecimal best = currentPrice;
    if (compareAt != null && compareAt.compareTo(best) > 0) best = compareAt;
    if (histBaseline != null && histBaseline.compareTo(best) > 0) best = histBaseline;
    if (globalBaseline != null && globalBaseline.compareTo(best) > 0) best = globalBaseline;
    return best.setScale(2, RoundingMode.HALF_UP);
  }

  private String extractImageUrl(ProductApi p) {
    if (p.images == null || p.images.isEmpty()) return null;
    return p.images.stream()
        .filter(img -> Boolean.TRUE.equals(img.isMainImage))
        .findFirst()
        .or(() -> p.images.stream().findFirst())
        .map(img -> (img.cleanedUrl != null && !img.cleanedUrl.isBlank()) ? img.cleanedUrl : img.url)
        .orElse(null);
  }

  private String normalizeDomain(String rawDomain) {
    if (rawDomain == null || rawDomain.isBlank()) return "unknown-merchant";
    String d = rawDomain.trim().toLowerCase(Locale.ROOT);
    if (d.startsWith("www.")) d = d.substring(4);
    return d;
  }

  private String formatMerchantName(String domain) {
    if (domain == null || domain.isBlank()) return "Unknown Merchant";
    int dot = domain.indexOf('.');
    String base = dot > 0 ? domain.substring(0, dot) : domain;
    return base.isEmpty() ? domain : Character.toUpperCase(base.charAt(0)) + base.substring(1);
  }
}

