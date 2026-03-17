package com.onlinestore.telegrambot.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.integration.dto.orders.OrderDto;
import com.onlinestore.telegrambot.integration.service.ManagerOrdersIntegrationService;
import com.onlinestore.telegrambot.routing.BotUpdateContext;
import com.onlinestore.telegrambot.support.TelegramApiExecutor;
import com.onlinestore.telegrambot.support.TelegramMessageFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@ExtendWith(MockitoExtension.class)
class ManagerActionHandlerTest {

    @Mock
    private ManagerOrdersIntegrationService managerOrdersIntegrationService;
    @Mock
    private TelegramApiExecutor telegramApiExecutor;

    private ManagerActionHandler managerActionHandler;

    @BeforeEach
    void setUp() {
        BotProperties botProperties = new BotProperties();
        botProperties.setToken("test-token");
        botProperties.getManagerNotifications().setEnabled(true);
        botProperties.getManagerNotifications().setChatId("20");
        botProperties.getManagerNotifications().setUserId("10");
        managerActionHandler = new ManagerActionHandler(
            botProperties,
            managerOrdersIntegrationService,
            new TelegramMessageFactory(),
            telegramApiExecutor
        );
    }

    @Test
    void acceptOrderUsesManagerIntegrationAndSendsFollowUp() {
        when(managerOrdersIntegrationService.confirmOrder(55L, "Accepted from Telegram by manager 10")).thenReturn(new OrderDto(
            55L,
            11L,
            "PROCESSING",
            new BigDecimal("19.99"),
            "USD",
            List.of(),
            Instant.parse("2026-03-17T18:00:00Z")
        ));

        BotApiMethod<?> response = managerActionHandler.handleCallback(context(callbackUpdate(10L, 20L, "cb-1", "manager:order:accept:55")));

        assertThat(response).isInstanceOf(AnswerCallbackQuery.class);
        assertThat(((AnswerCallbackQuery) response).getText()).isEqualTo("Order accepted.");
        verify(managerOrdersIntegrationService).confirmOrder(55L, "Accepted from Telegram by manager 10");
        verify(telegramApiExecutor).execute(any(SendMessage.class));
    }

    @Test
    void rejectsActionsFromNonManagerChat() {
        BotApiMethod<?> response = managerActionHandler.handleCallback(context(callbackUpdate(10L, 999L, "cb-2", "manager:order:ack:55")));

        assertThat(response).isInstanceOf(AnswerCallbackQuery.class);
        assertThat(((AnswerCallbackQuery) response).getText()).isEqualTo("Manager actions are not available in this chat.");
        verify(telegramApiExecutor, never()).execute(any(SendMessage.class));
    }

    @Test
    void rejectsActionsFromUnauthorizedManagerAccount() {
        BotApiMethod<?> response = managerActionHandler.handleCallback(context(callbackUpdate(999L, 20L, "cb-3", "manager:order:accept:55")));

        assertThat(response).isInstanceOf(AnswerCallbackQuery.class);
        assertThat(((AnswerCallbackQuery) response).getText()).isEqualTo("Manager actions are not available for this account.");
        verify(managerOrdersIntegrationService, never()).confirmOrder(any(), any());
        verify(telegramApiExecutor, never()).execute(any(SendMessage.class));
    }

    private BotUpdateContext context(Update update) {
        return BotUpdateContext.from(update).orElseThrow();
    }

    private Update callbackUpdate(Long userId, Long chatId, String callbackId, String callbackData) {
        Update update = new Update();
        CallbackQuery callbackQuery = new CallbackQuery();
        callbackQuery.setId(callbackId);
        callbackQuery.setData(callbackData);
        callbackQuery.setFrom(User.builder().id(userId).firstName("Manager").isBot(false).build());

        Message message = new Message();
        message.setMessageId(1);
        message.setChat(Chat.builder().id(chatId).type("private").build());
        callbackQuery.setMessage(message);

        update.setCallbackQuery(callbackQuery);
        return update;
    }
}
