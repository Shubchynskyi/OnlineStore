package com.onlinestore.payments.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.onlinestore.payments.entity.Payment;
import com.onlinestore.payments.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PaymentMapperTest {

    private final PaymentMapper paymentMapper = new PaymentMapper();

    @Test
    void toDtoShouldExposePaymentFields() {
        var payment = payment();
        payment.setCreatedAt(Instant.parse("2026-03-14T00:15:00Z"));

        var dto = paymentMapper.toDto(payment, "https://pay.example.test/next");

        assertThat(dto.id()).isEqualTo(55L);
        assertThat(dto.orderId()).isEqualTo(21L);
        assertThat(dto.providerCode()).isEqualTo("card");
        assertThat(dto.providerPaymentId()).isEqualTo("provider-21");
        assertThat(dto.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(dto.amount()).isEqualByComparingTo("120.50");
        assertThat(dto.currency()).isEqualTo("EUR");
        assertThat(dto.nextActionUrl()).isEqualTo("https://pay.example.test/next");
        assertThat(dto.createdAt()).isEqualTo(Instant.parse("2026-03-14T00:15:00Z"));
    }

    @Test
    void toCompletedEventShouldCaptureCurrentPaymentSnapshot() {
        var payment = payment();
        Instant before = Instant.now();
        var event = paymentMapper.toCompletedEvent(payment);
        Instant after = Instant.now();

        assertThat(event.eventType()).isEqualTo("payment.completed");
        assertThat(event.paymentId()).isEqualTo(55L);
        assertThat(event.orderId()).isEqualTo(21L);
        assertThat(event.amount()).isEqualByComparingTo("120.50");
        assertThat(event.currency()).isEqualTo("EUR");
        assertThat(event.occurredAt()).isBetween(before, after);
    }

    @Test
    void toStatusChangedEventShouldPreserveFailureReason() {
        var payment = payment();
        payment.setFailureReason("Provider timeout");
        Instant before = Instant.now();
        var event = paymentMapper.toStatusChangedEvent(payment, "payments.failed");
        Instant after = Instant.now();

        assertThat(event.eventType()).isEqualTo("payments.failed");
        assertThat(event.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(event.failureReason()).isEqualTo("Provider timeout");
        assertThat(event.occurredAt()).isBetween(before, after);
    }

    private Payment payment() {
        var payment = new Payment();
        payment.setId(55L);
        payment.setOrderId(21L);
        payment.setProviderCode("card");
        payment.setProviderPaymentId("provider-21");
        payment.setStatus(PaymentStatus.AUTHORIZED);
        payment.setAmount(new BigDecimal("120.50"));
        payment.setCurrency("EUR");
        return payment;
    }
}
