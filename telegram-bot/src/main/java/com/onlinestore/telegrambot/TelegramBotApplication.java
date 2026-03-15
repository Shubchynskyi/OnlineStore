package com.onlinestore.telegrambot;

import com.onlinestore.telegrambot.config.BotProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(BotProperties.class)
public class TelegramBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(TelegramBotApplication.class, args);
    }
}
