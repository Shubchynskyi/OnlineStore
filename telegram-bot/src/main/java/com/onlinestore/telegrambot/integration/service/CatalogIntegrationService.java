package com.onlinestore.telegrambot.integration.service;

import com.onlinestore.telegrambot.integration.client.CatalogApiClient;
import com.onlinestore.telegrambot.integration.dto.PageResponse;
import com.onlinestore.telegrambot.integration.dto.catalog.CategoryDto;
import com.onlinestore.telegrambot.integration.dto.catalog.CategoryWithProductsDto;
import com.onlinestore.telegrambot.integration.dto.catalog.ProductDto;
import com.onlinestore.telegrambot.integration.dto.catalog.ProductFilter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CatalogIntegrationService {

    private final CatalogApiClient catalogApiClient;

    public List<CategoryDto> getCategories() {
        return catalogApiClient.getCategories();
    }

    public CategoryWithProductsDto getCategoryBySlug(String slug, int page, int size) {
        return catalogApiClient.getCategoryBySlug(slug, page, size);
    }

    public PageResponse<ProductDto> getProducts(ProductFilter productFilter, int page, int size) {
        return catalogApiClient.getProducts(productFilter, page, size);
    }

    public ProductDto getProductBySlug(String slug) {
        return catalogApiClient.getProductBySlug(slug);
    }
}
