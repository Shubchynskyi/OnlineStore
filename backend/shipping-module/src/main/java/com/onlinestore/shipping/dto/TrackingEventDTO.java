package com.onlinestore.shipping.dto;

import com.onlinestore.shipping.entity.ShipmentStatus;
import java.time.Instant;

public record TrackingEventDTO(
    ShipmentStatus status,
    String location,
    String description,
    Instant occurredAt
) {
}
