package com.onlinestore.payments.provider;

public record RefundResult(
    String refundId,
    boolean success,
    String failureReason
) {
}
