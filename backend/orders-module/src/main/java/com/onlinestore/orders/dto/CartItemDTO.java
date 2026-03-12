package com.onlinestore.orders.dto;

import java.math.BigDecimal;

public record CartItemDTO(
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
