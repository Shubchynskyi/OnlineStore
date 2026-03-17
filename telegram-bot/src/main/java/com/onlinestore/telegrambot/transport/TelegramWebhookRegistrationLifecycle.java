package com.onlinestore.telegrambot.transport;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.support.TelegramApiExecutor;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramWebhookRegistrationLifecycle implements SmartLifecycle {

    private final BotProperties botProperties;
    private final TelegramApiExecutor telegramApiExecutor;

    private volatile boolean running;

    @Override
    public void start() {
        if (!botProperties.isEnabled() || !botProperties.isWebhookEnabled() || running) {
            return;
        }

        validateWebhookConfiguration();
        SetWebhook.SetWebhookBuilder webhookBuilder = SetWebhook.builder().url(botProperties.getWebhookUrl());
        if (botProperties.getProtection().hasWebhookSecretToken()) {
            webhookBuilder.secretToken(botProperties.getProtection().getWebhookSecretToken());
        }
        telegramApiExecutor.execute(webhookBuilder.build());
        running = true;
        log.info("Telegram bot webhook registered at {}", botProperties.getWebhookUrl());
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }

        telegramApiExecutor.execute(new DeleteWebhook());
        running = false;
        log.info("Telegram bot webhook removed");
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

    private void validateWebhookConfiguration() {
        if (!StringUtils.hasText(botProperties.getWebhookUrl())) {
            throw new IllegalStateException("telegram.bot.webhook-url must be set for webhook mode");
        }

        URI webhookUri = URI.create(botProperties.getWebhookUrl());
        if (!webhookUri.isAbsolute()) {
            throw new IllegalStateException("telegram.bot.webhook-url must be absolute");
        }

        String configuredPath = webhookUri.getPath();
        if (!botProperties.getWebhookPath().equals(configuredPath)) {
            throw new IllegalStateException(
                "telegram.bot.webhook-url path must match telegram.bot.webhook-path"
            );
        }
    }
}
