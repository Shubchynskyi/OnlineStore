package com.onlinestore.telegrambot.support;

import com.onlinestore.telegrambot.config.BotProperties;
import java.io.Serializable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
@RequiredArgsConstructor
public class TelegramApiExecutor {

    private final TelegramClient telegramClient;
    private final BotProperties botProperties;

    public <T extends Serializable> T execute(BotApiMethod<T> botApiMethod) {
        return executeWithRetry(() -> telegramClient.execute(botApiMethod), "Telegram API interaction failed");
    }

    public Boolean execute(SetWebhook setWebhook) {
        return executeWithRetry(() -> telegramClient.execute(setWebhook), "Telegram webhook registration failed");
    }

    private <T extends Serializable> T executeWithRetry(
        TelegramCall<T> telegramCall,
        String failureMessage
    ) {
        TelegramApiException lastFailure = null;
        int maxAttempts = botProperties.getRetry().getMaxAttempts();
        long baseBackoffMillis = botProperties.getRetry().getBackoff().toMillis();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return telegramCall.execute();
            } catch (TelegramApiRequestException exception) {
                if (!isRetryable(exception) || attempt == maxAttempts) {
                    throw new TelegramInteractionException("Telegram API request failed", exception);
                }
                lastFailure = exception;
            } catch (TelegramApiException exception) {
                if (attempt == maxAttempts) {
                    throw new TelegramInteractionException(failureMessage, exception);
                }
                lastFailure = exception;
            }

            pause(baseBackoffMillis * attempt, lastFailure);
        }

        throw new TelegramInteractionException(failureMessage, lastFailure);
    }

    private boolean isRetryable(TelegramApiRequestException exception) {
        Integer errorCode = exception.getErrorCode();
        return errorCode != null && (errorCode == 429 || errorCode >= 500);
    }

    private void pause(long backoffMillis, TelegramApiException lastFailure) {
        if (backoffMillis <= 0) {
            return;
        }

        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new TelegramInteractionException("Telegram retry backoff was interrupted", lastFailure);
        }
    }

    @FunctionalInterface
    private interface TelegramCall<T extends Serializable> {
        T execute() throws TelegramApiException;
    }
}
