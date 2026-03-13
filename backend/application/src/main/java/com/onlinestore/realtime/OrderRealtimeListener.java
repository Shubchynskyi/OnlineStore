package com.onlinestore.realtime;

import com.onlinestore.common.config.RabbitMQConfig;
import com.onlinestore.orders.dto.OrderDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderRealtimeListener {

    private final RealtimeMessageBroadcaster realtimeMessageBroadcaster;

    @RabbitListener(queues = RabbitMQConfig.ORDER_REALTIME_QUEUE)
    public void handleOrderEvent(
        OrderDTO order,
        @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey
    ) {
        switch (routingKey) {
            case "order.created", "order.status-changed" ->
                realtimeMessageBroadcaster.broadcastOrderUpdate(order, routingKey);
            default -> {
                // ignore non-realtime order events
            }
        }
    }
}
