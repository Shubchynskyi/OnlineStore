package com.onlinestore.payments.event;

import com.onlinestore.common.event.DomainEvent;
import com.onlinestore.payments.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentStatusChangedEvent(
    String eventType,
    Long paymentId,
    Long orderId,
    PaymentStatus status,
    BigDecimal amount,
    String currency,
    String failureReason,
    Instant occurredAt
) implements DomainEvent {
}
