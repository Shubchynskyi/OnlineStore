package com.onlinestore.shipping.dto;

import com.onlinestore.shipping.entity.ShipmentStatus;
import java.time.LocalDate;

public record ShipmentDTO(
    Long id,
    Long orderId,
    String providerCode,
    String trackingNumber,
    ShipmentStatus status,
    LocalDate estimatedDelivery,
    String labelUrl
) {
}
