package com.onlinestore.telegrambot.notifications;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.integration.dto.orders.OrderDto;
import com.onlinestore.telegrambot.notifications.dto.ProductLowStockEventPayload;
import com.onlinestore.telegrambot.support.TelegramApiExecutor;
import com.onlinestore.telegrambot.support.TelegramMessageFactory;
import com.onlinestore.telegrambot.support.TelegramInteractionException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManagerNotificationService {

    private final BotProperties botProperties;
    private final TelegramMessageFactory telegramMessageFactory;
    private final TelegramApiExecutor telegramApiExecutor;

    public void notifyOrderCreated(OrderDto order) {
        BotProperties.ManagerNotifications config = botProperties.getManagerNotifications();
        if (!config.isEnabled() || !config.isNotifyOrderCreated()) {
            return;
        }
        deliverToManagers(
            buildOrderText("New order received", order),
            orderKeyboard(order)
        );
    }

    public void notifyOrderStatusChanged(OrderDto order) {
        BotProperties.ManagerNotifications config = botProperties.getManagerNotifications();
        if (!config.isEnabled() || !config.isNotifyOrderStatusChanged()) {
            return;
        }
        deliverToManagers(
            buildOrderText("Order status changed", order),
            orderKeyboard(order)
        );
    }

    public void notifyProductLowStock(ProductLowStockEventPayload event) {
        BotProperties.ManagerNotifications config = botProperties.getManagerNotifications();
        if (!config.isEnabled() || !config.isNotifyProductLowStock()) {
            return;
        }
        deliverToManagers(
            buildLowStockText(event),
            lowStockKeyboard(event)
        );
    }

    private void deliverToManagers(String text, InlineKeyboardMarkup keyboard) {
        List<Long> recipients = botProperties.getManagerNotifications().resolveChatIds();
        TelegramInteractionException aggregatedFailure = null;
        for (Long chatId : recipients) {
            try {
                telegramApiExecutor.execute(telegramMessageFactory.message(chatId, text, keyboard));
            } catch (TelegramInteractionException ex) {
                if (aggregatedFailure == null) {
                    aggregatedFailure = new TelegramInteractionException(
                        "Failed to deliver manager notification to one or more configured chats.",
                        ex
                    );
                } else {
                    aggregatedFailure.addSuppressed(ex);
                }
                log.error("Failed to deliver manager notification. chatId={}", chatId, ex);
            }
        }

        if (aggregatedFailure != null) {
            throw aggregatedFailure;
        }
    }

    private String buildOrderText(String title, OrderDto order) {
        int itemsCount = order.items() == null ? 0 : order.items().size();
        return title
            + "\nOrder #" + valueOrDash(order.id())
            + "\nStatus: " + valueOrDash(order.status())
            + "\nCustomer id: " + valueOrDash(order.userId())
            + "\nItems: " + itemsCount
            + "\nTotal: " + formatAmount(order.totalAmount()) + appendCurrency(order.totalCurrency())
            + "\nCreated: " + valueOrDash(order.createdAt());
    }

    private String buildLowStockText(ProductLowStockEventPayload event) {
        return "Low-stock alert"
            + "\nProduct: " + valueOrDash(event.productName())
            + "\nVariant: " + valueOrDash(event.variantName())
            + "\nSKU: " + valueOrDash(event.sku())
            + "\nCurrent stock: " + valueOrDash(event.currentStock())
            + "\nThreshold: " + valueOrDash(event.lowStockThreshold())
            + "\nReported: " + valueOrDash(event.occurredAt());
    }

    private InlineKeyboardMarkup orderKeyboard(OrderDto order) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        if ("PAID".equals(order.status())) {
            rows.add(new InlineKeyboardRow(
                telegramMessageFactory.callbackButton("Accept order", ManagerActionHandler.acceptOrderCallback(order.id()))
            ));
        }
        rows.add(new InlineKeyboardRow(
            telegramMessageFactory.callbackButton("Acknowledge", ManagerActionHandler.acknowledgeOrderCallback(order.id())),
            telegramMessageFactory.callbackButton("Customer handoff", ManagerActionHandler.customerHandoffCallback(order.id()))
        ));
        return telegramMessageFactory.keyboard(rows);
    }

    private InlineKeyboardMarkup lowStockKeyboard(ProductLowStockEventPayload event) {
        return telegramMessageFactory.keyboard(List.of(
            new InlineKeyboardRow(
                telegramMessageFactory.callbackButton("Acknowledge", ManagerActionHandler.acknowledgeLowStockCallback(event.variantId()))
            )
        ));
    }

    private String appendCurrency(String currency) {
        return currency == null || currency.isBlank() ? "" : " " + currency;
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "-";
        }
        return amount.stripTrailingZeros().toPlainString();
    }

    private String valueOrDash(Object value) {
        return value == null ? "-" : value.toString();
    }
}
