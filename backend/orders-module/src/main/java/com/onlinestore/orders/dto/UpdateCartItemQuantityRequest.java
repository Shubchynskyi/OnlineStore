package com.onlinestore.orders.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateCartItemQuantityRequest(
    @NotNull @Min(1) Integer quantity
) {
}
