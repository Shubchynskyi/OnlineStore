package com.onlinestore.telegrambot.integration.dto.orders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderDto(
    Long id,
    Long userId,
    String status,
    BigDecimal totalAmount,
    String totalCurrency,
    List<OrderItemDto> items,
    Instant createdAt
) {
}
