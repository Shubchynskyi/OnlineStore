package com.onlinestore.telegrambot.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.integration.BackendAuthenticationRequiredException;
import com.onlinestore.telegrambot.integration.client.OrdersApiClient;
import com.onlinestore.telegrambot.integration.dto.orders.OrderDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrdersIntegrationServiceTests {

    @Mock
    private OrdersApiClient ordersApiClient;

    @Mock
    private CustomerAccessTokenResolver customerAccessTokenResolver;

    private OrdersIntegrationService ordersIntegrationService;

    @BeforeEach
    void setUp() {
        BotProperties botProperties = new BotProperties();
        botProperties.setToken("test-token");
        botProperties.getBackendApi().setRecentOrdersPageSize(3);
        ordersIntegrationService = new OrdersIntegrationService(
            ordersApiClient,
            customerAccessTokenResolver,
            botProperties
        );
    }

    @Test
    void lookupOrderParsesNumericReferenceAndDelegates() {
        OrderDto orderDto = new OrderDto(
            15L,
            42L,
            "PENDING",
            new BigDecimal("19.99"),
            "USD",
            List.of(),
            Instant.parse("2026-03-16T12:00:00Z")
        );
        when(customerAccessTokenResolver.resolveAccessToken(42L)).thenReturn(Optional.of("customer-token"));
        when(ordersApiClient.getOrder("customer-token", 15L)).thenReturn(orderDto);

        assertThat(ordersIntegrationService.lookupOrder(42L, "15")).isSameAs(orderDto);

        verify(ordersApiClient).getOrder("customer-token", 15L);
    }

    @Test
    void lookupOrderFailsWhenNoCustomerTokenIsLinked() {
        when(customerAccessTokenResolver.resolveAccessToken(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ordersIntegrationService.getOrder(42L, 15L))
            .isInstanceOf(BackendAuthenticationRequiredException.class)
            .hasMessageContaining("not linked");
    }
}
