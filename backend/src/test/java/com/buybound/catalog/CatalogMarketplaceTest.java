package com.buybound.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.buybound.catalog.client.Channel3Client;
import com.buybound.catalog.dto.Channel3SearchResponse;
import com.buybound.catalog.dto.MarketplaceProductDto;
import com.buybound.catalog.dto.MarketplaceResponse;
import com.buybound.catalog.service.BuiltInMarketplaceCatalog;
import com.buybound.catalog.service.Channel3SearchService;
import com.buybound.common.exception.ExternalServiceException;
import com.buybound.watch.domain.Watch;
import com.buybound.watch.dto.WatchResponse;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogMarketplaceTest {

  @Test
  void everyCatalogProductHas100PercentUniqueProductImageUrl() {
    Channel3Client client = mock(Channel3Client.class);
    Channel3SearchService service = new Channel3SearchService(client);

    MarketplaceResponse response = service.browseMarketplace(null, null, null, null, "savings", 100);
    List<MarketplaceProductDto> products = response.products();

    Set<String> uniqueImages = new HashSet<>();
    for (MarketplaceProductDto product : products) {
      assertThat(product.imageUrl()).isNotBlank().startsWith("https://");
      uniqueImages.add(product.imageUrl());
    }

    assertThat(products.size()).isEqualTo(BuiltInMarketplaceCatalog.productCount());
    assertThat(uniqueImages).hasSameSizeAs(products);

    // Also verify seeded WatchResponse resolves distinct product images per watch
    Watch w1 = new Watch("Sony WH-1000XM5 Wireless Noise Cancelling Headphones", null, "SONY-WH1000XM5", "Black", "macys.com", "USD", new BigDecimal("260.00"));
    Watch w2 = new Watch("Apple AirPods Pro 2 USB-C", null, "APPLE-APP2-USBC", "White", "bestbuy.com", "USD", new BigDecimal("189.00"));
    Watch w3 = new Watch("Dyson Airwrap Multi-Styler", null, "DYSON-AIRWRAP", "Nickel/Copper", "bestbuy.com", "USD", new BigDecimal("470.00"));
    Set<String> watchImages = Set.of(
        WatchResponse.of(w1, UUID.randomUUID(), 1).imageUrl(),
        WatchResponse.of(w2, UUID.randomUUID(), 1).imageUrl(),
        WatchResponse.of(w3, UUID.randomUUID(), 1).imageUrl());
    assertThat(watchImages).hasSize(3);
  }

  @Test
  void marketplaceExposes44RealWorldProductsAcross8Categories() {
    Channel3Client client = mock(Channel3Client.class);
    Channel3SearchService service = new Channel3SearchService(client);

    MarketplaceResponse response = service.browseMarketplace(null, null, null, null, "savings", 50);

    assertThat(BuiltInMarketplaceCatalog.CATEGORIES).hasSize(8);
    assertThat(response.categories()).containsExactlyElementsOf(BuiltInMarketplaceCatalog.CATEGORIES);
    assertThat(response.totalCount()).isGreaterThanOrEqualTo(44);
    assertThat(response.products()).allSatisfy(product -> {
      assertThat(product.storeCount()).isGreaterThanOrEqualTo(3);
      assertThat(product.bestPrice()).isPositive();
      assertThat(product.baselinePrice()).isGreaterThanOrEqualTo(product.bestPrice());
      assertThat(product.imageUrl()).startsWith("https://");
    });
  }

  @Test
  void arbitraryProductQueryFallsBackToSmartMultiStoreSynthesizerAndDistinctImages() {
    Channel3Client client = mock(Channel3Client.class);
    when(client.search(anyString(), anyInt()))
        .thenThrow(new ExternalServiceException("Channel3 offline in test"));
    Channel3SearchService service = new Channel3SearchService(
        client, null, q -> java.util.Optional.empty());

    MarketplaceResponse market = service.browseMarketplace(
        null, "Logitech MX Master 3S", null, null, "savings", 20);
    Channel3SearchResponse flat = service.searchByQuery("Logitech MX Master 3S", null, 8);
    Channel3SearchResponse alt = service.searchByQuery("Casio G-Shock DW5600", null, 8);

    assertThat(market.products()).isNotEmpty();
    assertThat(market.source()).isEqualTo("smart-catalog-synthesizer");
    assertThat(market.products().getFirst().offers()).hasSize(4);
    assertThat(flat.offers()).hasSize(4);
    assertThat(flat.offers().getFirst().verifiedSavings()).isPositive();
    assertThat(flat.offers().getFirst().imageUrl()).startsWith("https://");
    assertThat(alt.offers().getFirst().imageUrl()).startsWith("https://");
    assertThat(flat.offers().getFirst().imageUrl()).isNotEqualTo(alt.offers().getFirst().imageUrl());

    Channel3SearchService withLiveImageResolver = new Channel3SearchService(
        client,
        null,
        q -> java.util.Optional.of("https://cdn.dummyjson.com/product-images/mens-watches/rolex-submariner-watch/1.webp"));
    Channel3SearchResponse liveResolved = withLiveImageResolver.searchByQuery("Rolex Submariner Vintage", null, 4);
    assertThat(liveResolved.offers().getFirst().imageUrl())
        .isEqualTo("https://cdn.dummyjson.com/product-images/mens-watches/rolex-submariner-watch/1.webp");
  }

  @Test
  void filtersByCategoryAndPriceCeiling() {
    Channel3Client client = mock(Channel3Client.class);
    Channel3SearchService service = new Channel3SearchService(client);

    MarketplaceResponse response = service.browseMarketplace(
        "Audio", null, null, new BigDecimal("200.00"), "price_asc", 50);

    assertThat(response.products()).isNotEmpty();
    assertThat(response.products()).allSatisfy(product -> {
      assertThat(product.category()).isEqualTo("Audio");
      assertThat(product.bestPrice()).isLessThanOrEqualTo(new BigDecimal("200.00"));
    });
  }
}
