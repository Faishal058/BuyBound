package com.buybound.catalog.dto;

import java.util.List;

/**
 * Response payload for marketplace browsing and universal product search.
 */
public record MarketplaceResponse(
    List<MarketplaceProductDto> products,
    List<String> categories,
    int totalCount,
    String source,
    String query) {}

