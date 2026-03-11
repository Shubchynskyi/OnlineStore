package com.onlinestore.common.port.orders;

import java.math.BigDecimal;

public record OrderAccessView(
    Long id,
    Long userId,
    BigDecimal totalAmount,
    String totalCurrency
) {
}
