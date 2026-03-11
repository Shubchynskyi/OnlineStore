package com.onlinestore.payments.provider;

import com.onlinestore.common.util.Money;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CryptoPaymentProvider implements PaymentProvider {

    @Override
    public String getProviderCode() {
        return "crypto";
    }

    @Override
    public Set<String> getSupportedCountries() {
        return Set.of("US", "DE", "GB");
    }

    @Override
    public PaymentResult createPayment(PaymentRequest request) {
        String providerPaymentId = "crypto-" + UUID.randomUUID();
        return new PaymentResult(
            providerPaymentId,
            PaymentResult.PaymentResultStatus.REQUIRES_ACTION,
            request.returnUrl(),
            UUID.randomUUID().toString(),
            null
        );
    }

    @Override
    public PaymentResult confirmPayment(String providerPaymentId) {
        return new PaymentResult(
            providerPaymentId,
            PaymentResult.PaymentResultStatus.PAID,
            null,
            null,
            null
        );
    }

    @Override
    public RefundResult refund(String providerPaymentId, Money amount) {
        return new RefundResult("crypto-refund-" + UUID.randomUUID(), true, null);
    }

    @Override
    public boolean verifyWebhook(String payload, String signature, String timestamp) {
        return false;
    }
}
