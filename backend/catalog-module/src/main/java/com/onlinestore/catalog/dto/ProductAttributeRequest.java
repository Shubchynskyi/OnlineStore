package com.onlinestore.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record ProductAttributeRequest(
    @NotBlank @Size(max = 100) String name,
    @NotNull Map<String, Object> value
) {
}
