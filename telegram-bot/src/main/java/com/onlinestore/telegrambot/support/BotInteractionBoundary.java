package com.onlinestore.telegrambot.support;

import com.onlinestore.telegrambot.routing.BotUpdateDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class BotInteractionBoundary {

    private final BotUpdateDispatcher botUpdateDispatcher;
    private final TelegramApiExecutor telegramApiExecutor;
    private final BotErrorHandler botErrorHandler;

    public void handleLongPollingUpdate(Update update) {
        BotApiMethod<?> response = resolveResponse(update);
        if (response == null) {
            return;
        }

        try {
            telegramApiExecutor.execute(response);
        } catch (TelegramInteractionException exception) {
            botErrorHandler.logDeliveryFailure(update, exception);
        }
    }

    public BotApiMethod<?> handleWebhookUpdate(Update update) {
        return resolveResponse(update);
    }

    private BotApiMethod<?> resolveResponse(Update update) {
        try {
            return botUpdateDispatcher.dispatch(update);
        } catch (RuntimeException exception) {
            return botErrorHandler.handleProcessingFailure(update, exception);
        }
    }
}
