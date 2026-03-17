package com.onlinestore.telegrambot.integration.dto.orders;

import java.math.BigDecimal;

public record OrderItemDto(
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
