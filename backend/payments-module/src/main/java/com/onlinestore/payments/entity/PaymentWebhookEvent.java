package com.onlinestore.payments.entity;

import com.onlinestore.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "payment_webhook_events",
    uniqueConstraints = @UniqueConstraint(
        name = "ux_payment_webhook_events_provider_event",
        columnNames = {"provider_code", "event_id"}
    )
)
@Getter
@Setter
public class PaymentWebhookEvent extends BaseEntity {

    @Column(name = "provider_code", nullable = false, length = 50)
    private String providerCode;

    @Column(name = "event_id", nullable = false, length = 255)
    private String eventId;

    @Column(name = "payment_id")
    private Long paymentId;
}
