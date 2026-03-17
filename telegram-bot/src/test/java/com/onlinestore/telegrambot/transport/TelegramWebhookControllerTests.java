package com.onlinestore.telegrambot.transport;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.support.BotInteractionBoundary;
import com.onlinestore.telegrambot.support.SecurityAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.telegram.telegrambots.meta.api.objects.Update;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TelegramWebhookControllerTests {

    @Mock
    private BotInteractionBoundary botInteractionBoundary;

    @Mock
    private SecurityAuditService securityAuditService;

    private BotProperties botProperties;
    private TelegramWebhookController telegramWebhookController;

    @BeforeEach
    void setUp() {
        botProperties = new BotProperties();
        botProperties.setToken("test-token");
        botProperties.setEnabled(true);
        botProperties.setWebhookUrl("https://example.com/telegram/webhook");
        botProperties.getProtection().setWebhookSecretToken("telegram-webhook-secret");
        telegramWebhookController = new TelegramWebhookController(
            botProperties,
            botInteractionBoundary,
            securityAuditService
        );
    }

    @Test
    void receiveUpdateRejectsRequestsWithUnexpectedSecretToken() {
        Update update = new Update();
        update.setUpdateId(7);

        var response = telegramWebhookController.receiveUpdate("wrong-secret", update);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(botInteractionBoundary, never()).handleWebhookUpdate(update);
    }

    @Test
    void receiveUpdateAcceptsRequestsWithMatchingSecretToken() {
        Update update = new Update();
        update.setUpdateId(8);
        when(botInteractionBoundary.handleWebhookUpdate(update)).thenReturn(null);

        var response = telegramWebhookController.receiveUpdate("telegram-webhook-secret", update);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(botInteractionBoundary).handleWebhookUpdate(update);
    }
}
