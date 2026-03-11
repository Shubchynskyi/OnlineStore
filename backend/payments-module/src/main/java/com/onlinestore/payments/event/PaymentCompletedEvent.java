package com.onlinestore.payments.event;

import com.onlinestore.common.event.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentCompletedEvent(
    Long paymentId,
    Long orderId,
    BigDecimal amount,
    String currency,
    Instant occurredAt
) implements DomainEvent {

    @Override
    public String eventType() {
        return "payment.completed";
    }
}
