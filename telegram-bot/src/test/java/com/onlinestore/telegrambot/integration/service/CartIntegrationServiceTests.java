package com.onlinestore.telegrambot.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onlinestore.telegrambot.integration.BackendAuthenticationRequiredException;
import com.onlinestore.telegrambot.integration.client.CartApiClient;
import com.onlinestore.telegrambot.integration.dto.cart.CartDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CartIntegrationServiceTests {

    @Mock
    private CartApiClient cartApiClient;

    @Mock
    private CustomerAccessTokenResolver customerAccessTokenResolver;

    private CartIntegrationService cartIntegrationService;

    @BeforeEach
    void setUp() {
        cartIntegrationService = new CartIntegrationService(cartApiClient, customerAccessTokenResolver);
    }

    @Test
    void getCartUsesResolvedCustomerToken() {
        CartDto cartDto = new CartDto(new BigDecimal("9.99"), "USD", List.of());
        when(customerAccessTokenResolver.resolveAccessToken(42L)).thenReturn(Optional.of("customer-token"));
        when(cartApiClient.getCart("customer-token")).thenReturn(cartDto);

        assertThat(cartIntegrationService.getCart(42L)).isSameAs(cartDto);

        verify(cartApiClient).getCart("customer-token");
    }

    @Test
    void getCartFailsWhenNoCustomerTokenIsLinked() {
        when(customerAccessTokenResolver.resolveAccessToken(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartIntegrationService.getCart(42L))
            .isInstanceOf(BackendAuthenticationRequiredException.class)
            .hasMessageContaining("not linked");
    }
}
