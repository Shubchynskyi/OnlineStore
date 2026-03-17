package com.onlinestore.telegrambot.integration.client;

import com.onlinestore.telegrambot.integration.dto.PageResponse;
import com.onlinestore.telegrambot.integration.dto.catalog.CategoryDto;
import com.onlinestore.telegrambot.integration.dto.catalog.CategoryWithProductsDto;
import com.onlinestore.telegrambot.integration.dto.catalog.ProductDto;
import com.onlinestore.telegrambot.integration.dto.catalog.ProductFilter;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class CatalogApiClient {

    private static final ParameterizedTypeReference<List<CategoryDto>> CATEGORY_LIST_TYPE =
        new ParameterizedTypeReference<>() {
        };

    private static final ParameterizedTypeReference<PageResponse<ProductDto>> PRODUCT_PAGE_TYPE =
        new ParameterizedTypeReference<>() {
        };

    private final RestClient backendApiRestClient;
    private final BackendApiClientSupport backendApiClientSupport;

    public List<CategoryDto> getCategories() {
        return backendApiClientSupport.execute("catalog.getCategories", () -> backendApiRestClient.get()
            .uri("/api/v1/public/catalog/categories")
            .headers(backendApiClientSupport::applyOptionalServiceAuthentication)
            .retrieve()
            .body(CATEGORY_LIST_TYPE));
    }

    public CategoryWithProductsDto getCategoryBySlug(String slug, int page, int size) {
        return backendApiClientSupport.execute("catalog.getCategoryBySlug", () -> backendApiRestClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/api/v1/public/catalog/categories/{slug}")
                .queryParam("page", page)
                .queryParam("size", size)
                .build(slug))
            .headers(backendApiClientSupport::applyOptionalServiceAuthentication)
            .retrieve()
            .body(CategoryWithProductsDto.class));
    }

    public PageResponse<ProductDto> getProducts(ProductFilter filter, int page, int size) {
        return backendApiClientSupport.execute("catalog.getProducts", () -> backendApiRestClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/api/v1/public/catalog/products")
                .queryParamIfPresent("categoryId", optionalValue(filter.categoryId()))
                .queryParamIfPresent("query", optionalText(filter.query()))
                .queryParamIfPresent("priceMin", optionalValue(filter.priceMin()))
                .queryParamIfPresent("priceMax", optionalValue(filter.priceMax()))
                .queryParam("page", page)
                .queryParam("size", size)
                .build())
            .headers(backendApiClientSupport::applyOptionalServiceAuthentication)
            .retrieve()
            .body(PRODUCT_PAGE_TYPE));
    }

    public ProductDto getProductBySlug(String slug) {
        return backendApiClientSupport.execute("catalog.getProductBySlug", () -> backendApiRestClient.get()
            .uri("/api/v1/public/catalog/products/{slug}", slug)
            .headers(backendApiClientSupport::applyOptionalServiceAuthentication)
            .retrieve()
            .body(ProductDto.class));
    }

    private Optional<Object> optionalValue(Object value) {
        return value == null ? Optional.empty() : Optional.of(value);
    }

    private Optional<String> optionalText(String value) {
        return StringUtils.hasText(value) ? Optional.of(value) : Optional.empty();
    }
}
