package com.onlinestore.orders.entity;

public enum OrderEvent {
    PAYMENT_INITIATED,
    PAYMENT_RECEIVED,
    PAYMENT_FAILED,
    MANAGER_CONFIRM,
    SHIPMENT_CREATED,
    DELIVERY_CONFIRMED,
    CANCEL_REQUEST,
    REFUND_COMPLETED
}
