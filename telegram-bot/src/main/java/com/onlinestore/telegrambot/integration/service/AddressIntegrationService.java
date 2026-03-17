package com.onlinestore.telegrambot.integration.service;

import com.onlinestore.telegrambot.integration.BackendAuthenticationRequiredException;
import com.onlinestore.telegrambot.integration.client.AddressApiClient;
import com.onlinestore.telegrambot.integration.dto.address.AddressDto;
import com.onlinestore.telegrambot.integration.dto.address.CreateAddressRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AddressIntegrationService {

    private final AddressApiClient addressApiClient;
    private final CustomerAccessTokenResolver customerAccessTokenResolver;

    public List<AddressDto> getAddresses(Long telegramUserId) {
        return addressApiClient.getAddresses(requireAccessToken(telegramUserId));
    }

    public AddressDto createAddress(Long telegramUserId, CreateAddressRequest request) {
        return addressApiClient.createAddress(requireAccessToken(telegramUserId), request);
    }

    private String requireAccessToken(Long telegramUserId) {
        return customerAccessTokenResolver.resolveAccessToken(telegramUserId)
            .orElseThrow(() -> new BackendAuthenticationRequiredException(
                "Checkout is active, but this Telegram user is not linked to a backend customer token yet."
            ));
    }
}
