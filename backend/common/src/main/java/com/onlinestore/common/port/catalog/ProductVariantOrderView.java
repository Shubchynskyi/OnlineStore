package com.onlinestore.common.port.catalog;

import java.math.BigDecimal;

public record ProductVariantOrderView(
    Long id,
    String sku,
    String name,
    String productName,
    BigDecimal priceAmount,
    String priceCurrency,
    Integer stock
) {
}
