package com.onlinestore.search.dto;

import java.math.BigDecimal;

public record ProductSearchRequest(
    String query,
    String category,
    BigDecimal priceMin,
    BigDecimal priceMax
) {
}
