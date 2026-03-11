package com.onlinestore.catalog.dto;

import com.onlinestore.common.dto.PageResponse;

public record CategoryWithProductsDTO(
    CategoryDTO category,
    PageResponse<ProductDTO> products
) {
}
