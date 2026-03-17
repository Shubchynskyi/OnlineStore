package com.onlinestore.telegrambot.integration.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.integration.auth.BackendServiceAccessTokenProvider;
import com.onlinestore.telegrambot.integration.dto.PageResponse;
import com.onlinestore.telegrambot.integration.dto.search.ProductSearchRequest;
import com.onlinestore.telegrambot.integration.dto.search.ProductSearchResult;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class SearchApiClientTests {

    private MockRestServiceServer mockRestServiceServer;
    private SearchApiClient searchApiClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        mockRestServiceServer = MockRestServiceServer.bindTo(restClientBuilder).build();

        BotProperties botProperties = new BotProperties();
        botProperties.setToken("test-token");
        botProperties.getBackendApi().getRetry().setMaxAttempts(1);
        botProperties.getBackendApi().getRetry().setBackoff(Duration.ZERO);

        BackendServiceAccessTokenProvider serviceAccessTokenProvider = mock(BackendServiceAccessTokenProvider.class);
        when(serviceAccessTokenProvider.isEnabled()).thenReturn(false);

        BackendApiClientSupport support = new BackendApiClientSupport(
            botProperties,
            new ObjectMapper(),
            serviceAccessTokenProvider
        );

        searchApiClient = new SearchApiClient(restClientBuilder.baseUrl("http://localhost:8080").build(), support);
    }

    @Test
    void searchBuildsQueryParametersAndParsesPageResponse() {
        mockRestServiceServer.expect(requestTo("http://localhost:8080/api/v1/public/search/products?query=tea&page=0&size=5"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(queryParam("query", "tea"))
            .andExpect(queryParam("page", "0"))
            .andExpect(queryParam("size", "5"))
            .andRespond(withSuccess(
                """
                {
                  "content":[{"id":"1","name":"Green Tea","description":"Loose leaf","category":"Tea","minPrice":4.50,"maxPrice":6.00,"inStock":true,"imageUrls":[],"score":1.0}],
                  "page":0,
                  "size":5,
                  "totalElements":1,
                  "totalPages":1,
                  "last":true
                }
                """,
                MediaType.APPLICATION_JSON
            ));

        PageResponse<ProductSearchResult> response = searchApiClient.search(
            new ProductSearchRequest("tea", null, null, null),
            0,
            5
        );

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().name()).isEqualTo("Green Tea");
        mockRestServiceServer.verify();
    }
}
