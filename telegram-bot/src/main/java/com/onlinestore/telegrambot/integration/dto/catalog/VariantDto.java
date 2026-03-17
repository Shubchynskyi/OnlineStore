package com.onlinestore.telegrambot.integration.dto.catalog;

import java.math.BigDecimal;
import java.util.Map;

public record VariantDto(
    Long id,
    String sku,
    String name,
    BigDecimal price,
    String currency,
    BigDecimal compareAtPrice,
    Integer stock,
    Map<String, Object> attributes,
    boolean active
) {
}
