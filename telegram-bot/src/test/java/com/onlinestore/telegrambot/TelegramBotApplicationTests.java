package com.onlinestore.telegrambot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "telegram.bot.token=test-token",
    "telegram.bot.enabled=false",
    "spring.rabbitmq.dynamic=false",
    "spring.rabbitmq.listener.simple.auto-startup=false",
    "management.health.rabbit.enabled=false"
})
class TelegramBotApplicationTests {

    @Test
    void contextLoads() {
    }
}
