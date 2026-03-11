package com.onlinestore.shipping.provider;

import java.math.BigDecimal;

public record ShippingRate(
    String providerCode,
    BigDecimal amount,
    String currency,
    int estimatedDays
) {
}
