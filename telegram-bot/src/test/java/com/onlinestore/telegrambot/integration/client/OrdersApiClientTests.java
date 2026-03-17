package com.onlinestore.telegrambot.integration.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.integration.auth.BackendServiceAccessTokenProvider;
import com.onlinestore.telegrambot.integration.dto.PageResponse;
import com.onlinestore.telegrambot.integration.dto.orders.OrderDto;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class OrdersApiClientTests {

    private MockRestServiceServer mockRestServiceServer;
    private OrdersApiClient ordersApiClient;

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

        ordersApiClient = new OrdersApiClient(restClientBuilder.baseUrl("http://localhost:8080").build(), support);
    }

    @Test
    void getOrdersSendsBearerTokenAndParsesPage() {
        mockRestServiceServer.expect(requestTo("http://localhost:8080/api/v1/orders?page=0&size=3"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer customer-token"))
            .andExpect(queryParam("page", "0"))
            .andExpect(queryParam("size", "3"))
            .andRespond(withSuccess(
                """
                {
                  "content":[{"id":15,"userId":42,"status":"PENDING","totalAmount":19.99,"totalCurrency":"USD","items":[],"createdAt":"2026-03-16T12:00:00Z"}],
                  "page":0,
                  "size":3,
                  "totalElements":1,
                  "totalPages":1,
                  "last":true
                }
                """,
                MediaType.APPLICATION_JSON
            ));

        PageResponse<OrderDto> response = ordersApiClient.getOrders("customer-token", 0, 3);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().id()).isEqualTo(15L);
        mockRestServiceServer.verify();
    }
}
