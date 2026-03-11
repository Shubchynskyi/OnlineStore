package com.onlinestore.orders.event;

import com.onlinestore.common.event.DomainEvent;
import java.time.Instant;

public record OrderCreatedEvent(
    Long orderId,
    Long userId,
    Instant occurredAt
) implements DomainEvent {

    @Override
    public String eventType() {
        return "order.created";
    }
}
