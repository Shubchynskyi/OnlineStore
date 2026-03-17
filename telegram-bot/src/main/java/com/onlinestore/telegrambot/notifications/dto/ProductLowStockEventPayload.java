package com.onlinestore.telegrambot.notifications.dto;

import java.time.Instant;

public record ProductLowStockEventPayload(
    Long productId,
    String productName,
    Long variantId,
    String variantName,
    String sku,
    Integer currentStock,
    Integer lowStockThreshold,
    Instant occurredAt
) {
}
