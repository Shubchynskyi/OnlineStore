package com.onlinestore.telegrambot.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.AssertTrue;
import java.net.URI;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "telegram.bot")
public class BotProperties {

    private boolean enabled = true;

    @NotBlank
    private String token;

    private String username;

    private String webhookUrl;

    @NotBlank
    private String webhookPath = "/telegram/webhook";

    private String baseUrl = "https://api.telegram.org";

    @NotNull
    private Duration sessionTtl = Duration.ofHours(12);

    @Valid
    @NotNull
    private Retry retry = new Retry();

    @Valid
    @NotNull
    private BackendApi backendApi = new BackendApi();

    public boolean isWebhookEnabled() {
        return StringUtils.hasText(webhookUrl);
    }

    @AssertTrue(message = "telegram.bot.webhook-path must start with '/'")
    public boolean isWebhookPathValid() {
        return webhookPath != null && webhookPath.startsWith("/");
    }

    @Getter
    @Setter
    public static class Retry {

        @Positive
        private int maxAttempts = 3;

        @NotNull
        private Duration backoff = Duration.ofMillis(500);
    }

    @Getter
    @Setter
    public static class BackendApi {

        @NotBlank
        private String baseUrl = "http://localhost:8080";

        @Min(1)
        private int catalogPageSize = 6;

        @Min(1)
        private int searchPageSize = 5;

        @Min(1)
        private int recentOrdersPageSize = 3;

        @NotBlank
        private String customerTokenAttributeKey = "backendAccessToken";

        @NotNull
        private Duration connectTimeout = Duration.ofSeconds(5);

        @NotNull
        private Duration readTimeout = Duration.ofSeconds(10);

        @Valid
        @NotNull
        private Retry retry = new Retry();

        @Valid
        @NotNull
        private ServiceAuthentication serviceAuthentication = new ServiceAuthentication();

        @AssertTrue(message = "telegram.bot.backend-api.base-url must be an absolute URL")
        public boolean isBaseUrlValid() {
            return isAbsoluteUrl(baseUrl);
        }
    }

    @Getter
    @Setter
    public static class ServiceAuthentication {

        private boolean enabled;

        private String tokenUrl = "http://localhost:8180/realms/online-store/protocol/openid-connect/token";

        private String clientId = "telegram-bot";

        private String clientSecret;

        private String scope;

        @AssertTrue(message = "telegram.bot.backend-api.service-auth.token-url must be an absolute URL when enabled")
        public boolean isTokenUrlValid() {
            return !enabled || isAbsoluteUrl(tokenUrl);
        }

        @AssertTrue(message = "telegram.bot.backend-api.service-auth.client-id is required when enabled")
        public boolean isClientIdValid() {
            return !enabled || StringUtils.hasText(clientId);
        }

        @AssertTrue(message = "telegram.bot.backend-api.service-auth.client-secret is required when enabled")
        public boolean isClientSecretValid() {
            return !enabled || StringUtils.hasText(clientSecret);
        }
    }

    private static boolean isAbsoluteUrl(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return false;
        }
        try {
            URI uri = URI.create(candidate);
            return StringUtils.hasText(uri.getScheme()) && StringUtils.hasText(uri.getHost());
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
