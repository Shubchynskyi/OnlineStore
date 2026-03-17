package com.onlinestore.telegrambot.integration.dto.search;

import java.math.BigDecimal;

public record ProductSearchRequest(
    String query,
    String category,
    BigDecimal priceMin,
    BigDecimal priceMax
) {
}
