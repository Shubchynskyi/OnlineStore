package com.onlinestore.telegrambot.integration.client;

import com.onlinestore.telegrambot.integration.dto.address.AddressDto;
import com.onlinestore.telegrambot.integration.dto.address.CreateAddressRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class AddressApiClient {

    private static final ParameterizedTypeReference<List<AddressDto>> ADDRESS_LIST_TYPE =
        new ParameterizedTypeReference<>() {
        };

    private final RestClient backendApiRestClient;
    private final BackendApiClientSupport backendApiClientSupport;

    public List<AddressDto> getAddresses(String accessToken) {
        List<AddressDto> response = backendApiClientSupport.execute("users.getAddresses", () -> backendApiRestClient.get()
            .uri("/api/v1/users/me/addresses")
            .headers(headers -> headers.setBearerAuth(accessToken))
            .retrieve()
            .body(ADDRESS_LIST_TYPE));
        return response == null ? List.of() : response;
    }

    public AddressDto createAddress(String accessToken, CreateAddressRequest request) {
        return backendApiClientSupport.executeWithoutRetry("users.createAddress", () -> backendApiRestClient.post()
            .uri("/api/v1/users/me/addresses")
            .headers(headers -> headers.setBearerAuth(accessToken))
            .body(request)
            .retrieve()
            .body(AddressDto.class));
    }
}
