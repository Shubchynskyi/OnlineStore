package com.onlinestore.telegrambot.integration.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.integration.BackendApiException;
import com.onlinestore.telegrambot.integration.auth.BackendServiceAccessTokenProvider;
import com.onlinestore.telegrambot.integration.dto.cart.AddCartItemRequest;
import com.onlinestore.telegrambot.integration.dto.cart.CartDto;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class CartApiClientTests {

    private MockRestServiceServer mockRestServiceServer;
    private CartApiClient cartApiClient;

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

        cartApiClient = new CartApiClient(restClientBuilder.baseUrl("http://localhost:8080").build(), support);
    }

    @Test
    void getCartSendsBearerToken() {
        mockRestServiceServer.expect(requestTo("http://localhost:8080/api/v1/cart"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer customer-token"))
            .andRespond(withSuccess(
                """
                {"totalAmount":12.50,"totalCurrency":"USD","items":[]}
                """,
                MediaType.APPLICATION_JSON
            ));

        CartDto cartDto = cartApiClient.getCart("customer-token");

        assertThat(cartDto.totalCurrency()).isEqualTo("USD");
        mockRestServiceServer.verify();
    }

    @Test
    void addItemDoesNotRetryRetryableWriteFailures() {
        mockRestServiceServer.expect(requestTo("http://localhost:8080/api/v1/cart/items"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer customer-token"))
            .andRespond(withServerError());

        assertThatThrownBy(() -> cartApiClient.addItem("customer-token", new AddCartItemRequest(77L, 1)))
            .isInstanceOf(BackendApiException.class)
            .hasMessage("The store service is temporarily unavailable. Please try again later.");

        mockRestServiceServer.verify();
    }
}
