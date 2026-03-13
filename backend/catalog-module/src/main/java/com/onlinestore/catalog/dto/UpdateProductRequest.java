package com.onlinestore.catalog.dto;

import com.onlinestore.catalog.entity.ProductStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateProductRequest(
    @Size(max = 255) String name,
    @Size(max = 5000) String description,
    Long categoryId,
    ProductStatus status,
    Boolean isFeatured,
    @Valid List<ProductAttributeRequest> attributes
) {
}
