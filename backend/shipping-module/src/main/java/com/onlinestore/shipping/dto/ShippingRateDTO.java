package com.onlinestore.shipping.dto;

import java.math.BigDecimal;

public record ShippingRateDTO(
    String rateCode,
    String providerCode,
    String serviceName,
    BigDecimal amount,
    String currency,
    int estimatedDays
) {
}
