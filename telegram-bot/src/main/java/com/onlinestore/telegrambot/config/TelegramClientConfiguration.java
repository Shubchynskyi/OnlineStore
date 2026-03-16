package com.onlinestore.telegrambot.config;

import java.net.URI;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.TelegramUrl;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Configuration
public class TelegramClientConfiguration {

    @Bean
    public OkHttpClient telegramOkHttpClient() {
        return new OkHttpClient.Builder().build();
    }

    @Bean
    public TelegramUrl telegramUrl(BotProperties botProperties) {
        URI uri = URI.create(botProperties.getBaseUrl());
        String scheme = uri.getScheme();
        String host = uri.getHost();

        if (scheme == null || host == null) {
            throw new IllegalArgumentException("telegram.bot.base-url must be an absolute URL");
        }

        int port = uri.getPort();
        if (port < 0) {
            port = "http".equalsIgnoreCase(scheme) ? 80 : 443;
        }

        return TelegramUrl.builder()
            .schema(scheme)
            .host(host)
            .port(port)
            .testServer(false)
            .build();
    }

    @Bean
    public TelegramClient telegramClient(
        OkHttpClient telegramOkHttpClient,
        BotProperties botProperties,
        TelegramUrl telegramUrl
    ) {
        return new OkHttpTelegramClient(telegramOkHttpClient, botProperties.getToken(), telegramUrl);
    }
}
