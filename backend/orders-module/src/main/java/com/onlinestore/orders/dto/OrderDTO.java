package com.onlinestore.orders.dto;

import com.onlinestore.orders.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderDTO(
    Long id,
    Long userId,
    OrderStatus status,
    BigDecimal totalAmount,
    String totalCurrency,
    List<OrderItemDTO> items,
    Instant createdAt
) {
}
