package com.onlinestore.telegrambot.notifications;

import com.onlinestore.telegrambot.config.ManagerNotificationsRabbitConfiguration;
import com.onlinestore.telegrambot.integration.dto.orders.OrderDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ManagerOrderEventListener {

    private final ManagerNotificationService managerNotificationService;

    @RabbitListener(
        queues = ManagerNotificationsRabbitConfiguration.MANAGER_ORDER_QUEUE,
        containerFactory = "telegramBotRabbitListenerContainerFactory"
    )
    public void handleOrderEvent(OrderDto order, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        switch (routingKey) {
            case "order.created" -> managerNotificationService.notifyOrderCreated(order);
            case "order.status-changed" -> managerNotificationService.notifyOrderStatusChanged(order);
            default -> log.debug("Ignoring unsupported manager order routing key: {}", routingKey);
        }
    }
}
