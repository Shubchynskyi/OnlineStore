package com.onlinestore.notifications.listener;

import com.onlinestore.common.config.RabbitMQConfig;
import com.onlinestore.notifications.service.NotificationService;
import com.onlinestore.orders.dto.OrderDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderNotificationListener {

    private final NotificationService notificationService;

    @RabbitListener(queues = RabbitMQConfig.ORDER_NOTIFICATION_QUEUE)
    public void handleOrderEvent(
        OrderDTO order,
        @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey
    ) {
        switch (routingKey) {
            case "order.created" -> notificationService.notifyOrderCreated(order);
            case "order.status-changed" -> notificationService.notifyOrderStatusChanged(order);
            default -> {
                // TODO unexpected routing key, log and ignore
            }
        }
    }
}
