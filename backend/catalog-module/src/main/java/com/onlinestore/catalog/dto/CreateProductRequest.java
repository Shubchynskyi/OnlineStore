package com.onlinestore.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateProductRequest(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 5000) String description,
    Long categoryId,
    boolean isFeatured,
    @Valid @NotEmpty List<CreateVariantRequest> variants,
    @Valid List<ProductAttributeRequest> attributes
) {
}
