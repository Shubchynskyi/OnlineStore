package com.onlinestore.realtime;

import com.onlinestore.catalog.dto.ProductDTO;
import com.onlinestore.common.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductRealtimeListener {

    private final RealtimeMessageBroadcaster realtimeMessageBroadcaster;

    @RabbitListener(queues = RabbitMQConfig.PRODUCT_REALTIME_QUEUE)
    public void handleProductEvent(
        ProductDTO product,
        @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey
    ) {
        switch (routingKey) {
            case "product.created", "product.updated" ->
                realtimeMessageBroadcaster.broadcastProductUpdate(product, routingKey);
            default -> {
                // ignore non-realtime product events
            }
        }
    }
}
