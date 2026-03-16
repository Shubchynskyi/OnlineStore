package com.onlinestore.telegrambot.transport;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.support.BotInteractionBoundary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.BotSession;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.util.DefaultGetUpdatesGenerator;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.TelegramUrl;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
@Component
public class TelegramLongPollingTransport implements SmartLifecycle, LongPollingSingleThreadUpdateConsumer {

    private final BotProperties botProperties;
    private final BotInteractionBoundary botInteractionBoundary;
    private final TelegramUrl telegramUrl;

    private volatile boolean running;
    private TelegramBotsLongPollingApplication telegramBotsLongPollingApplication;
    private BotSession botSession;

    public TelegramLongPollingTransport(
        BotProperties botProperties,
        BotInteractionBoundary botInteractionBoundary,
        TelegramUrl telegramUrl
    ) {
        this.botProperties = botProperties;
        this.botInteractionBoundary = botInteractionBoundary;
        this.telegramUrl = telegramUrl;
    }

    @Override
    public void start() {
        if (!botProperties.isEnabled() || botProperties.isWebhookEnabled() || running) {
            return;
        }

        try {
            telegramBotsLongPollingApplication = new TelegramBotsLongPollingApplication();
            botSession = telegramBotsLongPollingApplication.registerBot(
                botProperties.getToken(),
                () -> telegramUrl,
                new DefaultGetUpdatesGenerator(),
                this
            );
            running = true;
            log.info("Telegram bot long polling transport started");
        } catch (TelegramApiException exception) {
            throw new IllegalStateException("Failed to start Telegram long polling transport", exception);
        }
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }

        try {
            if (telegramBotsLongPollingApplication != null) {
                telegramBotsLongPollingApplication.close();
            } else if (botSession != null) {
                botSession.close();
            }
            log.info("Telegram bot long polling transport stopped");
        } catch (Exception exception) {
            log.error("Failed to stop Telegram long polling transport cleanly", exception);
        } finally {
            running = false;
            telegramBotsLongPollingApplication = null;
            botSession = null;
        }
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return 0;
    }

    @Override
    public void consume(Update update) {
        botInteractionBoundary.handleLongPollingUpdate(update);
    }
}
