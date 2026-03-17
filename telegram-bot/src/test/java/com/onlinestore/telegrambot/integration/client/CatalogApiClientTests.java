package com.onlinestore.telegrambot.integration.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.integration.auth.BackendServiceAccessTokenProvider;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class CatalogApiClientTests {

    private MockRestServiceServer mockRestServiceServer;
    private CatalogApiClient catalogApiClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        mockRestServiceServer = MockRestServiceServer.bindTo(restClientBuilder).build();

        BotProperties botProperties = new BotProperties();
        botProperties.setToken("test-token");
        botProperties.getBackendApi().getRetry().setMaxAttempts(1);
        botProperties.getBackendApi().getRetry().setBackoff(Duration.ZERO);

        BackendServiceAccessTokenProvider serviceAccessTokenProvider = mock(BackendServiceAccessTokenProvider.class);
        when(serviceAccessTokenProvider.isEnabled()).thenReturn(true);
        when(serviceAccessTokenProvider.getAccessToken()).thenReturn("service-token");

        BackendApiClientSupport support = new BackendApiClientSupport(
            botProperties,
            new ObjectMapper(),
            serviceAccessTokenProvider
        );

        catalogApiClient = new CatalogApiClient(restClientBuilder.baseUrl("http://localhost:8080").build(), support);
    }

    @Test
    void getCategoriesAddsOptionalServiceTokenAndParsesResponse() {
        mockRestServiceServer.expect(requestTo("http://localhost:8080/api/v1/public/catalog/categories"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer service-token"))
            .andRespond(withSuccess(
                """
                [
                  {"id":1,"name":"Tea","slug":"tea","description":"Tea products"},
                  {"id":2,"name":"Coffee","slug":"coffee","description":"Coffee products"}
                ]
                """,
                MediaType.APPLICATION_JSON
            ));

        List<?> categories = catalogApiClient.getCategories();

        assertThat(categories).hasSize(2);
        mockRestServiceServer.verify();
    }
}
