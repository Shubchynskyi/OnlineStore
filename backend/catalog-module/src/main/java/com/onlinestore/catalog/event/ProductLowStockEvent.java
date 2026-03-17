package com.onlinestore.catalog.event;

import com.onlinestore.common.event.DomainEvent;
import java.time.Instant;

public record ProductLowStockEvent(
    Long productId,
    String productName,
    Long variantId,
    String variantName,
    String sku,
    Integer currentStock,
    Integer lowStockThreshold,
    Instant occurredAt
) implements DomainEvent {

    @Override
    public String eventType() {
        return "product.low-stock";
    }
}
