package com.onlinestore.catalog.dto;

import com.onlinestore.catalog.entity.ProductStatus;
import jakarta.validation.constraints.Size;

public record UpdateProductRequest(
    @Size(max = 255) String name,
    @Size(max = 5000) String description,
    Long categoryId,
    ProductStatus status,
    Boolean isFeatured
) {
}
