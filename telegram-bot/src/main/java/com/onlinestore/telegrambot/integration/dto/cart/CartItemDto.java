package com.onlinestore.telegrambot.integration.dto.cart;

import java.math.BigDecimal;

public record CartItemDto(
    Long id,
    Long productVariantId,
    String productName,
    String variantName,
    String sku,
    Integer quantity,
    BigDecimal unitPriceAmount,
    String unitPriceCurrency,
    BigDecimal totalAmount
) {
}
