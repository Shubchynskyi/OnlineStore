package com.onlinestore.payments.dto;

import com.onlinestore.payments.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentDTO(
    Long id,
    Long orderId,
    String providerCode,
    String providerPaymentId,
    PaymentStatus status,
    BigDecimal amount,
    String currency,
    String nextActionUrl,
    Instant createdAt
) {
}
