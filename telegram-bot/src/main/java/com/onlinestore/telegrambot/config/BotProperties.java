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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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

    @Valid
    @NotNull
    private ManagerNotifications managerNotifications = new ManagerNotifications();

    @Valid
    @NotNull
    private Protection protection = new Protection();

    public boolean isWebhookEnabled() {
        return StringUtils.hasText(webhookUrl);
    }

    @AssertTrue(message = "telegram.bot.webhook-path must start with '/'")
    public boolean isWebhookPathValid() {
        return webhookPath != null && webhookPath.startsWith("/");
    }

    @AssertTrue(message = "telegram.bot.protection.webhook-secret-token is required when telegram.bot.webhook-url is configured")
    public boolean isWebhookSecretConfigured() {
        return !isWebhookEnabled()
            || protection != null && StringUtils.hasText(protection.getWebhookSecretToken());
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

    @Getter
    @Setter
    public static class ManagerNotifications {

        private boolean enabled;

        private String chatId;

        @NotNull
        private String chatIds = "";

        private String userId;

        @NotNull
        private String userIds = "";

        private boolean notifyOrderCreated = true;

        private boolean notifyOrderStatusChanged = true;

        private boolean notifyProductLowStock = true;

        public List<Long> resolveChatIds() {
            return parseIdentifiers(chatId, chatIds, false, "manager chat id");
        }

        public List<Long> resolveUserIds() {
            return parseIdentifiers(userId, userIds, true, "manager user id");
        }

        public boolean hasAuthorizedActorsConfigured() {
            return !resolveUserIds().isEmpty();
        }

        @AssertTrue(message = "telegram.bot.manager-notifications requires at least one chat id when enabled")
        public boolean hasConfiguredRecipients() {
            return !enabled || !resolveChatIds().isEmpty();
        }

        private List<Long> parseIdentifiers(String singleValue, String multipleValues, boolean positiveOnly, String label) {
            LinkedHashSet<Long> resolvedIdentifiers = new LinkedHashSet<>();
            if (StringUtils.hasText(singleValue)) {
                resolvedIdentifiers.add(parseIdentifier(singleValue.trim(), positiveOnly, label));
            }
            if (StringUtils.hasText(multipleValues)) {
                for (String candidate : multipleValues.split("[,;\\r\\n]+")) {
                    if (StringUtils.hasText(candidate)) {
                        resolvedIdentifiers.add(parseIdentifier(candidate.trim(), positiveOnly, label));
                    }
                }
            }
            return List.copyOf(resolvedIdentifiers);
        }

        private Long parseIdentifier(String value, boolean positiveOnly, String label) {
            try {
                long parsedValue = Long.parseLong(value);
                if (positiveOnly && parsedValue <= 0) {
                    throw new IllegalStateException("Invalid " + label + ": " + value);
                }
                return parsedValue;
            } catch (NumberFormatException ex) {
                throw new IllegalStateException("Invalid " + label + ": " + value, ex);
            }
        }
    }

    @Getter
    @Setter
    public static class Protection {

        private String webhookSecretToken;

        @NotNull
        private Duration duplicateCartMutationTtl = Duration.ofSeconds(5);

        @NotNull
        private Duration duplicateManagerActionTtl = Duration.ofSeconds(15);

        @Valid
        @NotNull
        private RateLimit rateLimit = new RateLimit();

        public boolean hasWebhookSecretToken() {
            return StringUtils.hasText(webhookSecretToken);
        }

        @AssertTrue(message = "telegram.bot.protection.webhook-secret-token must be at least 16 non-whitespace characters when configured")
        public boolean isWebhookSecretTokenValid() {
            return !StringUtils.hasText(webhookSecretToken)
                || webhookSecretToken.equals(webhookSecretToken.trim()) && webhookSecretToken.length() >= 16;
        }
    }

    @Getter
    @Setter
    public static class RateLimit {

        @Valid
        @NotNull
        private RateLimitWindow userUpdates = rateLimitWindow(20, Duration.ofMinutes(1));

        @Valid
        @NotNull
        private RateLimitWindow aiRequests = rateLimitWindow(4, Duration.ofMinutes(1));

        @Valid
        @NotNull
        private RateLimitWindow managerActions = rateLimitWindow(6, Duration.ofMinutes(1));
    }

    @Getter
    @Setter
    public static class RateLimitWindow {

        @Min(1)
        private int maxEvents;

        @NotNull
        private Duration window;

        @AssertTrue(message = "telegram.bot.protection.rate-limit windows must be positive")
        public boolean isWindowValid() {
            return window != null && !window.isZero() && !window.isNegative();
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

    private static RateLimitWindow rateLimitWindow(int maxEvents, Duration window) {
        RateLimitWindow rateLimitWindow = new RateLimitWindow();
        rateLimitWindow.setMaxEvents(maxEvents);
        rateLimitWindow.setWindow(window);
        return rateLimitWindow;
    }
}
