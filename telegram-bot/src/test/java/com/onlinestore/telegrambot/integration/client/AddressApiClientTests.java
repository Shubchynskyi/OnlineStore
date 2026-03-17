package com.onlinestore.telegrambot.integration.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.integration.BackendApiException;
import com.onlinestore.telegrambot.integration.auth.BackendServiceAccessTokenProvider;
import com.onlinestore.telegrambot.integration.dto.address.AddressDto;
import com.onlinestore.telegrambot.integration.dto.address.CreateAddressRequest;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class AddressApiClientTests {

    private MockRestServiceServer mockRestServiceServer;
    private AddressApiClient addressApiClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        mockRestServiceServer = MockRestServiceServer.bindTo(restClientBuilder).build();

        BotProperties botProperties = new BotProperties();
        botProperties.setToken("test-token");
        botProperties.getBackendApi().getRetry().setMaxAttempts(3);
        botProperties.getBackendApi().getRetry().setBackoff(Duration.ZERO);

        BackendServiceAccessTokenProvider serviceAccessTokenProvider = mock(BackendServiceAccessTokenProvider.class);
        when(serviceAccessTokenProvider.isEnabled()).thenReturn(false);

        BackendApiClientSupport support = new BackendApiClientSupport(
            botProperties,
            new ObjectMapper(),
            serviceAccessTokenProvider
        );

        addressApiClient = new AddressApiClient(restClientBuilder.baseUrl("http://localhost:8080").build(), support);
    }

    @Test
    void getAddressesSendsBearerTokenAndParsesAddressList() {
        mockRestServiceServer.expect(requestTo("http://localhost:8080/api/v1/users/me/addresses"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer customer-token"))
            .andRespond(withSuccess(
                """
                [
                  {
                    "id": 7,
                    "label": "Home",
                    "country": "US",
                    "city": "New York",
                    "street": "Main Street",
                    "building": "10A",
                    "apartment": "5",
                    "postalCode": "10001",
                    "isDefault": true
                  }
                ]
                """,
                MediaType.APPLICATION_JSON
            ));

        List<AddressDto> addresses = addressApiClient.getAddresses("customer-token");

        assertThat(addresses).singleElement().extracting(AddressDto::id).isEqualTo(7L);
        mockRestServiceServer.verify();
    }

    @Test
    void createAddressSendsBearerTokenAndRequestBody() {
        CreateAddressRequest request = new CreateAddressRequest(
            null,
            "US",
            "New York",
            "Main Street",
            "10A",
            "5",
            "10001",
            false
        );

        mockRestServiceServer.expect(requestTo("http://localhost:8080/api/v1/users/me/addresses"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer customer-token"))
            .andExpect(content().json(
                """
                {
                  "label": null,
                  "country": "US",
                  "city": "New York",
                  "street": "Main Street",
                  "building": "10A",
                  "apartment": "5",
                  "postalCode": "10001",
                  "isDefault": false
                }
                """
            ))
            .andRespond(withSuccess(
                """
                {
                  "id": 9,
                  "label": null,
                  "country": "US",
                  "city": "New York",
                  "street": "Main Street",
                  "building": "10A",
                  "apartment": "5",
                  "postalCode": "10001",
                  "isDefault": false
                }
                """,
                MediaType.APPLICATION_JSON
            ));

        AddressDto createdAddress = addressApiClient.createAddress("customer-token", request);

        assertThat(createdAddress.id()).isEqualTo(9L);
        mockRestServiceServer.verify();
    }

    @Test
    void createAddressDoesNotRetryRetryableWriteFailures() {
        mockRestServiceServer.expect(requestTo("http://localhost:8080/api/v1/users/me/addresses"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer customer-token"))
            .andRespond(withServerError());

        assertThatThrownBy(() -> addressApiClient.createAddress(
            "customer-token",
            new CreateAddressRequest(null, "US", "New York", "Main Street", null, null, "10001", false)
        ))
            .isInstanceOf(BackendApiException.class)
            .hasMessage("The store service is temporarily unavailable. Please try again later.");

        mockRestServiceServer.verify();
    }
}
