package com.onlinestore.telegrambot.integration.dto.catalog;

import java.math.BigDecimal;

public record ProductFilter(
    Long categoryId,
    String query,
    BigDecimal priceMin,
    BigDecimal priceMax
) {
}
