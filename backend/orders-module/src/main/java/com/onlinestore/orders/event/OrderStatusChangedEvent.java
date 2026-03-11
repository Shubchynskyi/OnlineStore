package com.onlinestore.orders.event;

import com.onlinestore.common.event.DomainEvent;
import com.onlinestore.orders.entity.OrderStatus;
import java.time.Instant;

public record OrderStatusChangedEvent(
    Long orderId,
    OrderStatus status,
    Instant occurredAt
) implements DomainEvent {

    @Override
    public String eventType() {
        return "order.status-changed";
    }
}
