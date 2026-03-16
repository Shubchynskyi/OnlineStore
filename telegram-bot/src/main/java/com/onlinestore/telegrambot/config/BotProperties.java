package com.onlinestore.telegrambot.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.AssertTrue;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.util.StringUtils;

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
}
