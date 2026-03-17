package com.onlinestore.telegrambot.integration.dto.cart;

import java.math.BigDecimal;
import java.util.List;

public record CartDto(
    BigDecimal totalAmount,
    String totalCurrency,
    List<CartItemDto> items
) {
}
