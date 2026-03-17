package com.onlinestore.telegrambot.integration.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.integration.BackendApiException;
import com.onlinestore.telegrambot.integration.auth.BackendServiceAccessTokenProvider;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

class BackendApiClientSupportTests {

    private BackendApiClientSupport backendApiClientSupport;

    @BeforeEach
    void setUp() {
        BotProperties botProperties = new BotProperties();
        botProperties.setToken("test-token");
        botProperties.getBackendApi().getRetry().setMaxAttempts(2);
        botProperties.getBackendApi().getRetry().setBackoff(Duration.ZERO);

        BackendServiceAccessTokenProvider serviceAccessTokenProvider = mock(BackendServiceAccessTokenProvider.class);
        when(serviceAccessTokenProvider.isEnabled()).thenReturn(false);

        backendApiClientSupport = new BackendApiClientSupport(
            botProperties,
            new ObjectMapper(),
            serviceAccessTokenProvider
        );
    }

    @Test
    void retriesRetryableBackendResponsesAndEventuallySucceeds() {
        @SuppressWarnings("unchecked")
        Supplier<String> requestSupplier = mock(Supplier.class);
        when(requestSupplier.get())
            .thenThrow(new RestClientResponseException(
                "Too Many Requests",
                429,
                "Too Many Requests",
                HttpHeaders.EMPTY,
                """
                {"status":429,"error":"RATE_LIMITED","message":"slow down","path":"/api"}
                """.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
            ))
            .thenReturn("ok");

        String result = backendApiClientSupport.execute("search.searchProducts", requestSupplier);

        assertThat(result).isEqualTo("ok");
        verify(requestSupplier, times(2)).get();
    }

    @Test
    void convertsBackendErrorPayloadIntoBackendApiException() {
        @SuppressWarnings("unchecked")
        Supplier<String> requestSupplier = mock(Supplier.class);
        when(requestSupplier.get()).thenThrow(new RestClientResponseException(
            "Unprocessable Entity",
            422,
            "Unprocessable Entity",
            HttpHeaders.EMPTY,
            """
            {"status":422,"error":"OUT_OF_STOCK","message":"Variant is out of stock","path":"/api/v1/cart/items"}
            """.getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8
        ));

        assertThatThrownBy(() -> backendApiClientSupport.execute("cart.addItem", requestSupplier))
            .isInstanceOf(BackendApiException.class)
            .satisfies(throwable -> {
                BackendApiException exception = (BackendApiException) throwable;
                assertThat(exception.getOperation()).isEqualTo("cart.addItem");
                assertThat(exception.getStatusCode()).isEqualTo(422);
                assertThat(exception.getErrorCode()).isEqualTo("OUT_OF_STOCK");
                assertThat(exception.getMessage()).isEqualTo("The requested item is out of stock right now.");
                assertThat(exception.getMessage()).doesNotContain("/api/v1/cart/items");
            });
    }

    @Test
    void sanitizesConnectivityFailures() {
        @SuppressWarnings("unchecked")
        Supplier<String> requestSupplier = mock(Supplier.class);
        when(requestSupplier.get()).thenThrow(new ResourceAccessException(
            "I/O error on GET request for \"http://internal.local/api/v1/public/search/products\": connection refused"
        ));

        assertThatThrownBy(() -> backendApiClientSupport.execute("search.searchProducts", requestSupplier))
            .isInstanceOf(BackendApiException.class)
            .satisfies(throwable -> {
                BackendApiException exception = (BackendApiException) throwable;
                assertThat(exception.getMessage()).isEqualTo("The store service is temporarily unavailable. Please try again later.");
                assertThat(exception.getMessage()).doesNotContain("internal.local");
            });

        verify(requestSupplier, times(2)).get();
    }
}
