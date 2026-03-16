package com.onlinestore.telegrambot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "telegram.bot.token=test-token",
    "telegram.bot.enabled=false"
})
class TelegramBotApplicationTests {

    @Test
    void contextLoads() {
    }
}
