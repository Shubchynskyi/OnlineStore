package com.onlinestore.telegrambot.integration.client;

import com.onlinestore.telegrambot.integration.dto.PageResponse;
import com.onlinestore.telegrambot.integration.dto.search.ProductSearchRequest;
import com.onlinestore.telegrambot.integration.dto.search.ProductSearchResult;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class SearchApiClient {

    private static final ParameterizedTypeReference<PageResponse<ProductSearchResult>> SEARCH_PAGE_TYPE =
        new ParameterizedTypeReference<>() {
        };

    private static final ParameterizedTypeReference<List<String>> SUGGESTIONS_TYPE =
        new ParameterizedTypeReference<>() {
        };

    private final RestClient backendApiRestClient;
    private final BackendApiClientSupport backendApiClientSupport;

    public PageResponse<ProductSearchResult> search(ProductSearchRequest request, int page, int size) {
        return backendApiClientSupport.execute("search.searchProducts", () -> backendApiRestClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/api/v1/public/search/products")
                .queryParamIfPresent("query", optionalText(request.query()))
                .queryParamIfPresent("category", optionalText(request.category()))
                .queryParamIfPresent("priceMin", optionalValue(request.priceMin()))
                .queryParamIfPresent("priceMax", optionalValue(request.priceMax()))
                .queryParam("page", page)
                .queryParam("size", size)
                .build())
            .headers(backendApiClientSupport::applyOptionalServiceAuthentication)
            .retrieve()
            .body(SEARCH_PAGE_TYPE));
    }

    public List<String> suggest(String query) {
        return backendApiClientSupport.execute("search.suggest", () -> backendApiRestClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/api/v1/public/search/suggest")
                .queryParam("q", query)
                .build())
            .headers(backendApiClientSupport::applyOptionalServiceAuthentication)
            .retrieve()
            .body(SUGGESTIONS_TYPE));
    }

    private Optional<Object> optionalValue(Object value) {
        return value == null ? Optional.empty() : Optional.of(value);
    }

    private Optional<String> optionalText(String value) {
        return StringUtils.hasText(value) ? Optional.of(value) : Optional.empty();
    }
}
