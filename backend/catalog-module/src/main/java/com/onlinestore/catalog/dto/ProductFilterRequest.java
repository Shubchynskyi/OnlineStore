package com.onlinestore.catalog.dto;

import java.math.BigDecimal;

public record ProductFilterRequest(
    Long categoryId,
    String query,
    BigDecimal priceMin,
    BigDecimal priceMax
) {
}
