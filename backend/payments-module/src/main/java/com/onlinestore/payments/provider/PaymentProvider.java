package com.onlinestore.payments.provider;

import com.onlinestore.common.util.Money;
import java.util.Set;

public interface PaymentProvider {

    String getProviderCode();

    Set<String> getSupportedCountries();

    PaymentResult createPayment(PaymentRequest request);

    PaymentResult confirmPayment(String providerPaymentId, String idempotencyKey);

    RefundResult refund(String providerPaymentId, Money amount, String idempotencyKey);

    boolean verifyWebhook(String payload, String signature, String timestamp);
}
