package com.onlinestore.shipping.provider;

import com.onlinestore.shipping.entity.ShipmentStatus;
import java.time.Instant;

public record TrackingInfo(
    String trackingNumber,
    ShipmentStatus status,
    String location,
    String description,
    Instant occurredAt
) {
}
