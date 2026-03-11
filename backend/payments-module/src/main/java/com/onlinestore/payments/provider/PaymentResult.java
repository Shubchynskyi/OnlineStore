package com.onlinestore.payments.provider;

public record PaymentResult(
    String providerPaymentId,
    PaymentResultStatus status,
    String nextActionUrl,
    String clientSecret,
    String failureReason
) {
    public enum PaymentResultStatus {
        PENDING,
        REQUIRES_ACTION,
        AUTHORIZED,
        PAID,
        FAILED
    }

    public static PaymentResult pending(String message) {
        return new PaymentResult(null, PaymentResultStatus.PENDING, null, null, message);
    }
}
