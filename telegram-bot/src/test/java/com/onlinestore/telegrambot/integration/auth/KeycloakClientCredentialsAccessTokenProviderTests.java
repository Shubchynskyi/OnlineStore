package com.onlinestore.telegrambot.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.onlinestore.telegrambot.config.BotProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class KeycloakClientCredentialsAccessTokenProviderTests {

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockRestServiceServer;
    private BotProperties botProperties;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        mockRestServiceServer = MockRestServiceServer.bindTo(restClientBuilder).build();

        botProperties = new BotProperties();
        botProperties.setToken("test-token");
        botProperties.getBackendApi().getServiceAuthentication().setEnabled(true);
        botProperties.getBackendApi().getServiceAuthentication().setTokenUrl("http://auth.local/token");
        botProperties.getBackendApi().getServiceAuthentication().setClientId("telegram-bot");
        botProperties.getBackendApi().getServiceAuthentication().setClientSecret("secret");
    }

    @Test
    void cachesFetchedTokensUntilExpiry() {
        mockRestServiceServer.expect(requestTo("http://auth.local/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(
                """
                {"access_token":"header.payload.signature","expires_in":300,"token_type":"Bearer"}
                """,
                MediaType.APPLICATION_JSON
            ));

        KeycloakClientCredentialsAccessTokenProvider provider = new KeycloakClientCredentialsAccessTokenProvider(
            restClientBuilder.build(),
            botProperties,
            Clock.fixed(Instant.parse("2026-03-16T12:00:00Z"), ZoneOffset.UTC),
            new ObjectMapper()
        );

        assertThat(provider.getAccessToken()).isEqualTo("header.payload.signature");
        assertThat(provider.getAccessToken()).isEqualTo("header.payload.signature");

        mockRestServiceServer.verify();
    }

    @Test
    void throwsWhenServiceAuthenticationIsDisabled() {
        botProperties.getBackendApi().getServiceAuthentication().setEnabled(false);

        KeycloakClientCredentialsAccessTokenProvider provider = new KeycloakClientCredentialsAccessTokenProvider(
            restClientBuilder.build(),
            botProperties,
            Clock.systemUTC(),
            new ObjectMapper()
        );

        assertThat(provider.isEnabled()).isFalse();
        assertThatThrownBy(provider::getAccessToken)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("disabled");
    }

    @Test
    void rejectsNonJwtAccessTokens() {
        mockRestServiceServer.expect(requestTo("http://auth.local/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(
                """
                {"access_token":"service-token","expires_in":300,"token_type":"Bearer"}
                """,
                MediaType.APPLICATION_JSON
            ));

        KeycloakClientCredentialsAccessTokenProvider provider = new KeycloakClientCredentialsAccessTokenProvider(
            restClientBuilder.build(),
            botProperties,
            Clock.systemUTC(),
            new ObjectMapper()
        );

        assertThatThrownBy(provider::getAccessToken)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("invalid JWT access token");

        mockRestServiceServer.verify();
    }

    @Test
    void rejectsNonPositiveTokenExpiry() {
        mockRestServiceServer.expect(requestTo("http://auth.local/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(
                """
                {"access_token":"header.payload.signature","expires_in":0,"token_type":"Bearer"}
                """,
                MediaType.APPLICATION_JSON
            ));

        KeycloakClientCredentialsAccessTokenProvider provider = new KeycloakClientCredentialsAccessTokenProvider(
            restClientBuilder.build(),
            botProperties,
            Clock.systemUTC(),
            new ObjectMapper()
        );

        assertThatThrownBy(provider::getAccessToken)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("non-positive expiry");

        mockRestServiceServer.verify();
    }
}
