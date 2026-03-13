package com.onlinestore.realtime;

import com.onlinestore.catalog.dto.ProductDTO;
import com.onlinestore.orders.dto.OrderDTO;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class RealtimeMessageBroadcaster {

    private static final String PRODUCT_TOPIC_PREFIX = "/topic/products/";
    private static final String ORDER_TOPIC_PREFIX = "/topic/orders/";

    private final SimpMessagingTemplate messagingTemplate;

    public RealtimeMessageBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcastProductUpdate(ProductDTO product, String eventType) {
        messagingTemplate.convertAndSend(
            PRODUCT_TOPIC_PREFIX + product.id(),
            new ProductRealtimeMessage(
                product.id(),
                eventType,
                product.slug(),
                product.name(),
                product.status() == null ? null : product.status().name()
            )
        );
    }

    public void broadcastOrderUpdate(OrderDTO order, String eventType) {
        messagingTemplate.convertAndSend(
            ORDER_TOPIC_PREFIX + order.id(),
            new OrderRealtimeMessage(
                order.id(),
                eventType,
                order.status() == null ? null : order.status().name()
            )
        );
    }

    record ProductRealtimeMessage(
        Long productId,
        String eventType,
        String slug,
        String name,
        String status
    ) {
    }

    record OrderRealtimeMessage(
        Long orderId,
        String eventType,
        String status
    ) {
    }
}
