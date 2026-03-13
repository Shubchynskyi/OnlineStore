package com.onlinestore.catalog.dto;

import com.onlinestore.catalog.entity.ProductStatus;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public record ProductDTO(
    Long id,
    String name,
    String slug,
    String description,
    Long categoryId,
    String categoryName,
    String categorySlug,
    ProductStatus status,
    boolean isFeatured,
    List<VariantDTO> variants,
    List<ImageDTO> images,
    List<ProductAttributeDTO> attributes
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
