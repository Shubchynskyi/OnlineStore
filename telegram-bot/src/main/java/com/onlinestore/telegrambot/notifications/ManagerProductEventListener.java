package com.onlinestore.telegrambot.notifications;

import com.onlinestore.telegrambot.config.ManagerNotificationsRabbitConfiguration;
import com.onlinestore.telegrambot.notifications.dto.ProductLowStockEventPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ManagerProductEventListener {

    private final ManagerNotificationService managerNotificationService;

    @RabbitListener(
        queues = ManagerNotificationsRabbitConfiguration.MANAGER_PRODUCT_QUEUE,
        containerFactory = "telegramBotRabbitListenerContainerFactory"
    )
    public void handleLowStockEvent(ProductLowStockEventPayload event) {
        managerNotificationService.notifyProductLowStock(event);
    }
}
