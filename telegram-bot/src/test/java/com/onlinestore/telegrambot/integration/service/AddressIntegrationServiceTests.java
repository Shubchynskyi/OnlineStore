package com.onlinestore.telegrambot.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onlinestore.telegrambot.integration.BackendAuthenticationRequiredException;
import com.onlinestore.telegrambot.integration.client.AddressApiClient;
import com.onlinestore.telegrambot.integration.dto.address.AddressDto;
import com.onlinestore.telegrambot.integration.dto.address.CreateAddressRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AddressIntegrationServiceTests {

    @Mock
    private AddressApiClient addressApiClient;

    @Mock
    private CustomerAccessTokenResolver customerAccessTokenResolver;

    private AddressIntegrationService addressIntegrationService;

    @BeforeEach
    void setUp() {
        addressIntegrationService = new AddressIntegrationService(addressApiClient, customerAccessTokenResolver);
    }

    @Test
    void getAddressesUsesResolvedCustomerToken() {
        AddressDto address = new AddressDto(7L, "Home", "US", "New York", "Main Street", "10A", "5", "10001", true);
        when(customerAccessTokenResolver.resolveAccessToken(42L)).thenReturn(Optional.of("customer-token"));
        when(addressApiClient.getAddresses("customer-token")).thenReturn(List.of(address));

        assertThat(addressIntegrationService.getAddresses(42L)).containsExactly(address);

        verify(addressApiClient).getAddresses("customer-token");
    }

    @Test
    void createAddressFailsWhenNoCustomerTokenIsLinked() {
        when(customerAccessTokenResolver.resolveAccessToken(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressIntegrationService.createAddress(
            42L,
            new CreateAddressRequest(null, "US", "New York", "Main Street", null, null, "10001", false)
        ))
            .isInstanceOf(BackendAuthenticationRequiredException.class)
            .hasMessageContaining("not linked");
    }
}
