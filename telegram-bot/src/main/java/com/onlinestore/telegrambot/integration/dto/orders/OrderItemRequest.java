package com.onlinestore.telegrambot.integration.dto.orders;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderItemRequest(
    @NotNull Long productVariantId,
    @NotNull @Min(1) Integer quantity
) {
}
