package com.onlinestore.payments.entity;

public enum PaymentStatus {
    PENDING,
    REQUIRES_ACTION,
    AUTHORIZED,
    PAID,
    FAILED,
    REFUNDED
}
