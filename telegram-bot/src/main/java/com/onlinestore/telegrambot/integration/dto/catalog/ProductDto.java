package com.onlinestore.telegrambot.integration.dto.catalog;

import java.util.List;

public record ProductDto(
    Long id,
    String name,
    String slug,
    String description,
    Long categoryId,
    String categoryName,
    String categorySlug,
    String status,
    boolean isFeatured,
    List<VariantDto> variants,
    List<ImageDto> images,
    List<ProductAttributeDto> attributes
) {
}
