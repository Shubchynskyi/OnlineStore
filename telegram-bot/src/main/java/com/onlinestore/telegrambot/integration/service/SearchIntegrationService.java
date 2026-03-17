package com.onlinestore.telegrambot.integration.service;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.integration.client.SearchApiClient;
import com.onlinestore.telegrambot.integration.dto.PageResponse;
import com.onlinestore.telegrambot.integration.dto.search.ProductSearchRequest;
import com.onlinestore.telegrambot.integration.dto.search.ProductSearchResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchIntegrationService {

    private final SearchApiClient searchApiClient;
    private final BotProperties botProperties;

    public PageResponse<ProductSearchResult> searchProducts(String query) {
        return searchApiClient.search(
            new ProductSearchRequest(query, null, null, null),
            0,
            botProperties.getBackendApi().getSearchPageSize()
        );
    }

    public List<String> suggest(String query) {
        return searchApiClient.suggest(query);
    }
}
