package com.onlinestore.orders.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartDTO(
    BigDecimal totalAmount,
    String totalCurrency,
    List<CartItemDTO> items
) {
}
