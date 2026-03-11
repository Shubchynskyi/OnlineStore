package com.onlinestore.orders.entity;

public enum OrderStatus {
    PENDING,
    AWAITING_PAYMENT,
    PAID,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    REFUNDED
}
