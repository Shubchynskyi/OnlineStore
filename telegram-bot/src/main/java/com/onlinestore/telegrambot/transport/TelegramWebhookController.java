package com.onlinestore.telegrambot.transport;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.support.BotInteractionBoundary;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;

@RestController
@RequiredArgsConstructor
public class TelegramWebhookController {

    private final BotProperties botProperties;
    private final BotInteractionBoundary botInteractionBoundary;

    @PostMapping("${telegram.bot.webhook-path:/telegram/webhook}")
    public ResponseEntity<BotApiMethod<?>> receiveUpdate(@RequestBody Update update) {
        if (!botProperties.isEnabled() || !botProperties.isWebhookEnabled()) {
            return ResponseEntity.notFound().build();
        }

        BotApiMethod<?> response = botInteractionBoundary.handleWebhookUpdate(update);
        if (response == null) {
            return ResponseEntity.ok().build();
        }

        return ResponseEntity.ok(response);
    }
}
