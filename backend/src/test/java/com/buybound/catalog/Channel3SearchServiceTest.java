package com.buybound.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.buybound.catalog.client.Channel3Client;
import com.buybound.catalog.client.Channel3Client.OfferApi;
import com.buybound.catalog.client.Channel3Client.PriceApi;
import com.buybound.catalog.client.Channel3Client.PriceHistoryApiResponse;
import com.buybound.catalog.client.Channel3Client.PriceStatsApi;
import com.buybound.catalog.client.Channel3Client.ProductApi;
import com.buybound.catalog.client.Channel3Client.SearchApiResponse;
import com.buybound.catalog.dto.Channel3SearchResponse;
import com.buybound.catalog.service.Channel3SearchService;
import com.buybound.common.exception.ExternalServiceException;
import com.buybound.watch.domain.Watch;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class Channel3SearchServiceTest {

  @Test
  void computesVerifiedSavingsAndCommissionPercentFromCatalog() {
    Channel3Client client = mock(Channel3Client.class);
    Channel3SearchService service = new Channel3SearchService(client);

    PriceApi price = new PriceApi();
    price.price = new BigDecimal("229.99");
    price.compareAtPrice = new BigDecimal("259.99");
    price.currency = "USD";

    OfferApi offer = new OfferApi();
    offer.domain = "www.macys.com";
    offer.url = "https://buy.trychannel3.com/test";
    offer.price = price;
    offer.availability = "InStock";
    offer.condition = "new";
    offer.maxCommissionRate = new BigDecimal("0.056");

    ProductApi product = new ProductApi();
    product.id = "IY1VB5D";
    product.title = "Sony WH-1000XM5";
    product.offers = List.of(offer);

    SearchApiResponse response = new SearchApiResponse();
    response.products = List.of(product);

    PriceStatsApi stats = new PriceStatsApi();
    stats.maxPrice = new BigDecimal("398.00");
    PriceHistoryApiResponse history = new PriceHistoryApiResponse();
    history.statistics = stats;

    when(client.search(anyString(), anyInt())).thenReturn(response);
    when(client.getPriceHistory("IY1VB5D")).thenReturn(history);

    Watch watch = new Watch("Sony WH-1000XM5", null, "SKU-1", "Black",
        "macys.com", "USD", new BigDecimal("260.00"));
    Channel3SearchResponse result = service.searchOffersForWatch(watch);

    assertFalse(result.offers().isEmpty());
    var first = result.offers().get(0);
    assertEquals("macys.com", first.merchantDomain());
    assertEquals(new BigDecimal("229.99"), first.currentPrice());
    assertEquals(new BigDecimal("398.00"), first.baselinePrice());
    assertEquals(new BigDecimal("168.01"), first.verifiedSavings());
    assertEquals(new BigDecimal("5.60"), first.commissionRatePercent());
  }

  @Test
  void channel3ClientFailsClosedWhenApiKeyMissing() {
    Channel3Client client = new Channel3Client("https://api.trychannel3.com/v1", "");
    assertThrows(ExternalServiceException.class, () -> client.search("Sony WH-1000XM5", 5));
  }
}

