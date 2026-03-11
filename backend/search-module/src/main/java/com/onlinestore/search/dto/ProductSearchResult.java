package com.onlinestore.search.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProductSearchResult(
    String id,
    String name,
    String description,
    String category,
    BigDecimal minPrice,
    BigDecimal maxPrice,
    boolean inStock,
    List<String> imageUrls,
    float score
) {
}
