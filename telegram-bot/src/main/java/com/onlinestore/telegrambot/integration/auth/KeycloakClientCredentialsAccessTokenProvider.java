package com.onlinestore.telegrambot.integration.auth;

import com.onlinestore.telegrambot.config.BotProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class KeycloakClientCredentialsAccessTokenProvider implements BackendServiceAccessTokenProvider {

    private static final Duration TOKEN_EXPIRY_SAFETY_MARGIN = Duration.ofSeconds(30);

    private final RestClient backendAuthRestClient;
    private final BotProperties botProperties;
    private final Clock backendApiClock;
    private final ObjectMapper objectMapper;

    private final Object monitor = new Object();

    private volatile BackendAccessToken cachedToken;

    @Override
    public boolean isEnabled() {
        return botProperties.getBackendApi().getServiceAuthentication().isEnabled();
    }

    @Override
    public String getAccessToken() {
        if (!isEnabled()) {
            throw new IllegalStateException("Backend service authentication is disabled.");
        }

        Instant now = backendApiClock.instant();
        BackendAccessToken currentToken = cachedToken;
        if (currentToken != null && currentToken.isUsableAt(now, TOKEN_EXPIRY_SAFETY_MARGIN)) {
            return currentToken.value();
        }

        synchronized (monitor) {
            BackendAccessToken refreshedToken = cachedToken;
            if (refreshedToken != null && refreshedToken.isUsableAt(now, TOKEN_EXPIRY_SAFETY_MARGIN)) {
                return refreshedToken.value();
            }

            BackendAccessToken fetchedToken = fetchAccessToken();
            cachedToken = fetchedToken;
            return fetchedToken.value();
        }
    }

    private BackendAccessToken fetchAccessToken() {
        BotProperties.ServiceAuthentication serviceAuthentication = botProperties.getBackendApi().getServiceAuthentication();

        LinkedMultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "client_credentials");
        formData.add("client_id", serviceAuthentication.getClientId());
        formData.add("client_secret", serviceAuthentication.getClientSecret());
        if (StringUtils.hasText(serviceAuthentication.getScope())) {
            formData.add("scope", serviceAuthentication.getScope());
        }

        String responseBody;
        try {
            responseBody = backendAuthRestClient.post()
                .uri(serviceAuthentication.getTokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .body(String.class);
        } catch (RestClientException ex) {
            throw new IllegalStateException("Failed to obtain a backend service access token.", ex);
        }

        if (!StringUtils.hasText(responseBody)) {
            throw new IllegalStateException("Backend service token response did not include an access token.");
        }

        JsonNode tokenPayload;
        try {
            tokenPayload = objectMapper.readTree(responseBody);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Backend service token response was not valid JSON.", ex);
        }

        String accessToken = textValue(tokenPayload, "access_token");
        if (!StringUtils.hasText(accessToken)) {
            throw new IllegalStateException("Backend service token response did not include an access token.");
        }
        accessToken = validateJwtAccessToken(accessToken);

        long expiresInSeconds = positiveExpirySeconds(tokenPayload);
        return new BackendAccessToken(
            accessToken,
            backendApiClock.instant().plusSeconds(expiresInSeconds)
        );
    }

    private String validateJwtAccessToken(String accessToken) {
        String normalizedAccessToken = accessToken.trim();
        String[] tokenParts = normalizedAccessToken.split("\\.", -1);
        if (tokenParts.length != 3) {
            throw new IllegalStateException("Backend service token response included an invalid JWT access token.");
        }

        for (String tokenPart : tokenParts) {
            if (!StringUtils.hasText(tokenPart)) {
                throw new IllegalStateException("Backend service token response included an invalid JWT access token.");
            }
        }
        return normalizedAccessToken;
    }

    private long positiveExpirySeconds(JsonNode tokenPayload) {
        long expiresInSeconds = tokenPayload.path("expires_in").asLong(Long.MIN_VALUE);
        if (expiresInSeconds <= 0L) {
            throw new IllegalStateException("Backend service token response included a non-positive expiry.");
        }
        return expiresInSeconds;
    }

    private String textValue(JsonNode jsonNode, String fieldName) {
        JsonNode field = jsonNode.path(fieldName);
        if (field.isMissingNode() || field.isNull()) {
            return null;
        }
        return field.asText();
    }
}
