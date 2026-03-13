package com.onlinestore.payments.mapper;

import com.onlinestore.payments.dto.PaymentDTO;
import com.onlinestore.payments.entity.Payment;
import com.onlinestore.payments.event.PaymentCompletedEvent;
import com.onlinestore.payments.event.PaymentStatusChangedEvent;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public PaymentDTO toDto(Payment payment, String nextActionUrl) {
        return new PaymentDTO(
            payment.getId(),
            payment.getOrderId(),
            payment.getProviderCode(),
            payment.getProviderPaymentId(),
            payment.getStatus(),
            payment.getAmount(),
            payment.getCurrency(),
            nextActionUrl,
            payment.getCreatedAt()
        );
    }

    public PaymentCompletedEvent toCompletedEvent(Payment payment) {
        return new PaymentCompletedEvent(
            payment.getId(),
            payment.getOrderId(),
            payment.getAmount(),
            payment.getCurrency(),
            Instant.now()
        );
    }

    public PaymentStatusChangedEvent toStatusChangedEvent(Payment payment, String eventType) {
        return new PaymentStatusChangedEvent(
            eventType,
            payment.getId(),
            payment.getOrderId(),
            payment.getStatus(),
            payment.getAmount(),
            payment.getCurrency(),
            payment.getFailureReason(),
            Instant.now()
        );
    }
}
