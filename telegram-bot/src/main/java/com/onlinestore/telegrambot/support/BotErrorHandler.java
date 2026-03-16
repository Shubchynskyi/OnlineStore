package com.onlinestore.telegrambot.support;

import com.onlinestore.telegrambot.routing.BotUpdateContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;

@Slf4j
@Component
public class BotErrorHandler {

    private final TelegramMessageFactory telegramMessageFactory;

    public BotErrorHandler(TelegramMessageFactory telegramMessageFactory) {
        this.telegramMessageFactory = telegramMessageFactory;
    }

    public BotApiMethod<?> handleProcessingFailure(Update update, RuntimeException exception) {
        BotUpdateContext updateContext = BotUpdateContext.from(update).orElse(null);

        log.error(
            "Telegram update processing failed. updateId={}, userId={}, chatId={}",
            update != null ? update.getUpdateId() : null,
            updateContext != null ? updateContext.getUserId() : null,
            updateContext != null ? updateContext.getChatId() : null,
            exception
        );

        if (updateContext == null) {
            return null;
        }

        if (updateContext.callbackQueryId().isPresent()) {
            return telegramMessageFactory.callbackProcessingFailure(updateContext.callbackQueryId().orElse(null));
        }

        return telegramMessageFactory.processingFailure(updateContext.getChatId());
    }

    public void logDeliveryFailure(Update update, TelegramInteractionException exception) {
        BotUpdateContext updateContext = BotUpdateContext.from(update).orElse(null);

        log.error(
            "Telegram response delivery failed. updateId={}, userId={}, chatId={}",
            update != null ? update.getUpdateId() : null,
            updateContext != null ? updateContext.getUserId() : null,
            updateContext != null ? updateContext.getChatId() : null,
            exception
        );
    }
}
