package com.onlinestore.payments.provider;

import com.onlinestore.common.util.Money;
import java.util.Map;

public record PaymentRequest(
    String orderId,
    Money amount,
    String returnUrl,
    String idempotencyKey,
    Map<String, String> metadata
) {
}
