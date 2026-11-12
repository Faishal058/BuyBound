package com.buybound.catalog.client;

import com.buybound.common.exception.ExternalServiceException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real HTTP client for the Channel3 Commerce API (https://api.trychannel3.com/v1).
 * Never synthesizes fake catalog data; fails cleanly with ExternalServiceException
 * if CHANNEL3_API_KEY is missing or the upstream API returns an error.
 */
@Component
public class Channel3Client {
  private static final Logger log = LoggerFactory.getLogger(Channel3Client.class);

  private final RestClient restClient;
  private final RestClient rootClient;
  private final String apiKey;

  public Channel3Client(
      @Value("${app.channel3.base-url:https://api.trychannel3.com/v1}") String baseUrl,
      @Value("${app.channel3.api-key:}") String apiKey) {
    this.apiKey = apiKey;
    String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    String hostRoot = normalizedBase.endsWith("/v1")
        ? normalizedBase.substring(0, normalizedBase.length() - 3)
        : normalizedBase;
    this.restClient = RestClient.builder().baseUrl(normalizedBase).build();
    this.rootClient = RestClient.builder().baseUrl(hostRoot).build();
    log.info("Channel3 live client initialized (baseUrl={}, configured={})",
        normalizedBase, apiKey != null && !apiKey.isBlank());
  }

  public SearchApiResponse search(String query, int limit) {
    requireKey();
    try {
      Map<String, Object> body = Map.of(
          "query", query,
          "limit", Math.min(Math.max(limit, 1), 30),
          "filters", Map.of(
              "conditions", List.of("new"),
              "availability", List.of("InStock")
          )
      );
      SearchApiResponse response = restClient.post()
          .uri("/search")
          .header("x-api-key", apiKey)
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .onStatus(HttpStatusCode::isError, (req, res) -> {
            throw new ExternalServiceException("Channel3 /v1/search error: HTTP " + res.getStatusCode());
          })
          .body(SearchApiResponse.class);
      if (response == null) {
        throw new ExternalServiceException("Channel3 /v1/search returned an empty response");
      }
      return response;
    } catch (ExternalServiceException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ExternalServiceException("Channel3 /v1/search request failed: " + ex.getMessage(), ex);
    }
  }

  public ProductApi lookupByUrl(String productUrl) {
    requireKey();
    try {
      Map<String, Object> body = Map.of("url", productUrl, "realtime_fetch", true);
      LookupApiResponse response = restClient.post()
          .uri("/lookup")
          .header("x-api-key", apiKey)
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .onStatus(HttpStatusCode::isError, (req, res) -> {
            String detail = "";
            try {
              detail = new String(res.getBody().readAllBytes());
            } catch (Exception ignored) {
              // ignore stream read error
            }
            throw new ExternalServiceException("Channel3 /v1/lookup error (" + res.getStatusCode() + "): " + detail);
          })
          .body(LookupApiResponse.class);
      if (response == null || response.product == null) {
        throw new ExternalServiceException("Channel3 /v1/lookup returned no product for URL");
      }
      return response.product;
    } catch (ExternalServiceException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ExternalServiceException("Channel3 /v1/lookup failed: " + ex.getMessage(), ex);
    }
  }

  public PriceHistoryApiResponse getPriceHistory(String canonicalProductId) {
    requireKey();
    if (canonicalProductId == null || canonicalProductId.isBlank()) {
      return null;
    }
    try {
      return rootClient.get()
          .uri("/v0/price-tracking/history/{id}", canonicalProductId)
          .header("x-api-key", apiKey)
          .retrieve()
          .onStatus(HttpStatusCode::isError, (req, res) -> {
            log.debug("Channel3 price history unavailable for {}: {}", canonicalProductId, res.getStatusCode());
          })
          .body(PriceHistoryApiResponse.class);
    } catch (Exception ex) {
      log.debug("Channel3 price history skipped for {}: {}", canonicalProductId, ex.getMessage());
      return null;
    }
  }

  public boolean isConfigured() {
    return apiKey != null && !apiKey.isBlank();
  }

  private void requireKey() {
    if (!isConfigured()) {
      throw new ExternalServiceException("CHANNEL3_API_KEY is not configured.");
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class SearchApiResponse {
    public List<ProductApi> products;
    @JsonProperty("next_page_token")
    public String nextPageToken;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class LookupApiResponse {
    public ProductApi product;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ProductApi {
    public String id;
    public String title;
    public String description;
    public List<BrandApi> brands;
    public List<ImageApi> images;
    public CategoryApi category;
    public List<OfferApi> offers;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class BrandApi {
    public String id;
    public String name;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ImageApi {
    public String url;
    @JsonProperty("cleaned_url")
    public String cleanedUrl;
    @JsonProperty("is_main_image")
    public Boolean isMainImage;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class CategoryApi {
    public String slug;
    public String title;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class OfferApi {
    public String url;
    public String domain;
    public PriceApi price;
    public String availability;
    public String condition;
    @JsonProperty("max_commission_rate")
    public BigDecimal maxCommissionRate;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class PriceApi {
    public BigDecimal price;
    @JsonProperty("compare_at_price")
    public BigDecimal compareAtPrice;
    public String currency;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class PriceHistoryApiResponse {
    @JsonProperty("canonical_product_id")
    public String canonicalProductId;
    @JsonProperty("product_title")
    public String productTitle;
    public PriceStatsApi statistics;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class PriceStatsApi {
    public BigDecimal mean;
    @JsonProperty("min_price")
    public BigDecimal minPrice;
    @JsonProperty("max_price")
    public BigDecimal maxPrice;
    @JsonProperty("current_price")
    public BigDecimal currentPrice;
    public String currency;
  }
}

