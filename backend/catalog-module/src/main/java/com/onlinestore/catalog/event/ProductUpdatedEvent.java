package com.onlinestore.catalog.event;

import com.onlinestore.common.event.DomainEvent;
import java.time.Instant;

public record ProductUpdatedEvent(
    Long productId,
    String slug,
    Instant occurredAt
) implements DomainEvent {

    @Override
    public String eventType() {
        return "product.updated";
    }
}
