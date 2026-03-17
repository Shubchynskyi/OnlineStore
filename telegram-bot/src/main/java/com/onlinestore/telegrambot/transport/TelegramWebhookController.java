package com.onlinestore.telegrambot.transport;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.support.SecurityAuditService;
import com.onlinestore.telegrambot.support.BotInteractionBoundary;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;

@RestController
@RequiredArgsConstructor
public class TelegramWebhookController {

    private static final String TELEGRAM_SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    private final BotProperties botProperties;
    private final BotInteractionBoundary botInteractionBoundary;
    private final SecurityAuditService securityAuditService;

    @PostMapping("${telegram.bot.webhook-path:/telegram/webhook}")
    public ResponseEntity<BotApiMethod<?>> receiveUpdate(
        @RequestHeader(name = TELEGRAM_SECRET_HEADER, required = false) String providedSecretToken,
        @RequestBody Update update
    ) {
        if (!botProperties.isEnabled() || !botProperties.isWebhookEnabled()) {
            return ResponseEntity.notFound().build();
        }
        if (!isAuthorizedWebhookRequest(providedSecretToken)) {
            securityAuditService.logWebhookRejected("invalid_secret_token", update != null ? update.getUpdateId() : null);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        BotApiMethod<?> response = botInteractionBoundary.handleWebhookUpdate(update);
        if (response == null) {
            return ResponseEntity.ok().build();
        }

        return ResponseEntity.ok(response);
    }

    private boolean isAuthorizedWebhookRequest(String providedSecretToken) {
        String expectedSecretToken = botProperties.getProtection().getWebhookSecretToken();
        return StringUtils.hasText(expectedSecretToken) && expectedSecretToken.equals(providedSecretToken);
    }
}
