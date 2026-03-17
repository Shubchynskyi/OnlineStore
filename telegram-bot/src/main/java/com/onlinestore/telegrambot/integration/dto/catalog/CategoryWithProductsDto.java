package com.onlinestore.telegrambot.integration.dto.catalog;

import com.onlinestore.telegrambot.integration.dto.PageResponse;

public record CategoryWithProductsDto(
    CategoryDto category,
    PageResponse<ProductDto> products
) {
}
