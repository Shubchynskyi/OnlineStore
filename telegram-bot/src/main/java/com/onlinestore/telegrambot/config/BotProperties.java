package com.onlinestore.telegrambot.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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

    @NotNull
    private Duration interactionLockTtl = Duration.ofMinutes(4);

    @Valid
    @NotNull
    private Retry retry = new Retry();

    @Valid
    @NotNull
    private BackendApi backendApi = new BackendApi();

    @Valid
    @NotNull
    private AiAssistant aiAssistant = new AiAssistant();

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

    @Getter
    @Setter
    public static class AiAssistant {

        private boolean enabled;

        private String baseUrl = "https://api.openai.com/v1";

        private String apiKey;

        private String model = "gpt-4o-mini";

        @Positive
        private int maxCompletionTokens = 320;

        @Positive
        private int maxHistoryMessages = 10;

        @Positive
        private int maxRetrievedProducts = 3;

        @Positive
        private int maxProductDescriptionCharacters = 160;

        @Positive
        private int maxUserMessageCharacters = 500;

        @Positive
        private int maxSessionTokens = 4000;

        @DecimalMin("0.0")
        @DecimalMax("2.0")
        private double temperature = 0.2d;

        @NotBlank
        private String fallbackMessage =
            "The AI assistant is temporarily unavailable. Please use /search or /catalog and try again later.";

        @NotBlank
        private String tokenBudgetMessage =
            "This assistant chat reached the session token budget. Clear the conversation and try again.";

        @NotNull
        private Duration connectTimeout = Duration.ofSeconds(5);

        @NotNull
        private Duration readTimeout = Duration.ofSeconds(30);

        @Valid
        @NotNull
        private Retry retry = new Retry();

        @AssertTrue(message = "telegram.bot.ai-assistant.base-url must be an absolute URL when enabled")
        public boolean isBaseUrlValid() {
            return !enabled || isAbsoluteUrl(baseUrl);
        }

        @AssertTrue(message = "telegram.bot.ai-assistant.api-key is required when enabled")
        public boolean isApiKeyValid() {
            return !enabled || StringUtils.hasText(apiKey);
        }

        @AssertTrue(message = "telegram.bot.ai-assistant.model is required when enabled")
        public boolean isModelValid() {
            return !enabled || StringUtils.hasText(model);
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
