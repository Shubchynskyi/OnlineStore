package com.onlinestore.catalog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Map;

public record CreateVariantRequest(
    @NotBlank @Size(max = 100) String sku,
    String name,
    @NotNull @Positive BigDecimal price,
    @Size(min = 3, max = 3) String currency,
    @NotNull @Min(0) Integer stock,
    Map<String, Object> attributes
) {
}
