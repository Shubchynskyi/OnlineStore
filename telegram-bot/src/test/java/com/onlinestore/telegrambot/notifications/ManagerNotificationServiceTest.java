package com.onlinestore.telegrambot.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.integration.dto.orders.OrderDto;
import com.onlinestore.telegrambot.notifications.dto.ProductLowStockEventPayload;
import com.onlinestore.telegrambot.support.TelegramApiExecutor;
import com.onlinestore.telegrambot.support.TelegramMessageFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

@ExtendWith(MockitoExtension.class)
class ManagerNotificationServiceTest {

    @Mock
    private TelegramApiExecutor telegramApiExecutor;

    private BotProperties botProperties;
    private ManagerNotificationService managerNotificationService;

    @BeforeEach
    void setUp() {
        botProperties = new BotProperties();
        botProperties.setToken("test-token");
        botProperties.getManagerNotifications().setEnabled(true);
        botProperties.getManagerNotifications().setChatId("1000");
        botProperties.getManagerNotifications().setUserId("2000");
        managerNotificationService = new ManagerNotificationService(
            botProperties,
            new TelegramMessageFactory(),
            telegramApiExecutor
        );
    }

    @Test
    void paidOrderNotificationsExposeAcceptAction() {
        managerNotificationService.notifyOrderStatusChanged(order("PAID"));

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramApiExecutor).execute(captor.capture());
        assertThat(captor.getValue().getText()).contains("Order status changed").contains("Status: PAID");
        assertThat(callbackData(captor.getValue()))
            .contains(ManagerActionHandler.acceptOrderCallback(42L))
            .contains(ManagerActionHandler.customerHandoffCallback(42L));
    }

    @Test
    void disabledNotificationsDoNotSendMessages() {
        botProperties.getManagerNotifications().setEnabled(false);

        managerNotificationService.notifyOrderCreated(order("PENDING"));

        verify(telegramApiExecutor, never()).execute(any(SendMessage.class));
    }

    @Test
    void lowStockNotificationBuildsInventoryAction() {
        managerNotificationService.notifyProductLowStock(new ProductLowStockEventPayload(
            10L,
            "Laptop",
            20L,
            "Silver",
            "SKU-20",
            5,
            5,
            Instant.parse("2026-03-17T18:00:00Z")
        ));

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramApiExecutor).execute(captor.capture());
        assertThat(captor.getValue().getText()).contains("Low-stock alert").contains("Laptop").contains("SKU-20");
        assertThat(callbackData(captor.getValue())).contains(ManagerActionHandler.acknowledgeLowStockCallback(20L));
    }

    @Test
    void notificationsWithoutAuthorizedUsersAreDeliveredWithoutActionButtons() {
        botProperties.getManagerNotifications().setUserId(null);
        botProperties.getManagerNotifications().setUserIds("");

        managerNotificationService.notifyOrderStatusChanged(order("PAID"));

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramApiExecutor).execute(captor.capture());
        assertThat(captor.getValue().getReplyMarkup()).isNull();
    }

    private OrderDto order(String status) {
        return new OrderDto(42L, 11L, status, new BigDecimal("19.99"), "USD", List.of(), Instant.parse("2026-03-17T18:00:00Z"));
    }

    private List<String> callbackData(SendMessage sendMessage) {
        InlineKeyboardMarkup keyboard = (InlineKeyboardMarkup) sendMessage.getReplyMarkup();
        return keyboard.getKeyboard().stream()
            .flatMap(List::stream)
            .map(button -> button.getCallbackData())
            .toList();
    }
}
