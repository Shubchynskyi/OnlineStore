package com.onlinestore.telegrambot.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "telegram.bot")
public class BotProperties {

    @NotBlank
    private String token;

    private String username;

    // Telegram webhook endpoint; empty means long-polling mode (configured in T-002)
    private String webhookUrl;

    private String baseUrl = "https://api.telegram.org";
}
