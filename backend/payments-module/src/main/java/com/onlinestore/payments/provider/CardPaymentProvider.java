package com.onlinestore.payments.provider;

import com.onlinestore.common.util.Money;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CardPaymentProvider implements PaymentProvider {

    @Override
    public String getProviderCode() {
        return "card";
    }

    @Override
    public Set<String> getSupportedCountries() {
        return Set.of("US", "DE", "GB", "FR", "IT", "ES", "NL", "PL");
    }

    @Override
    public PaymentResult createPayment(PaymentRequest request) {
        String providerPaymentId = "card-" + UUID.randomUUID();
        return new PaymentResult(
            providerPaymentId,
            PaymentResult.PaymentResultStatus.REQUIRES_ACTION,
            request.returnUrl(),
            UUID.randomUUID().toString(),
            null
        );
    }

    @Override
    public PaymentResult confirmPayment(String providerPaymentId, String idempotencyKey) {
        return new PaymentResult(
            providerPaymentId,
            PaymentResult.PaymentResultStatus.PAID,
            null,
            null,
            null
        );
    }

    @Override
    public RefundResult refund(String providerPaymentId, Money amount, String idempotencyKey) {
        return new RefundResult("card-refund-" + UUID.randomUUID(), true, null);
    }

    @Override
    public boolean verifyWebhook(String payload, String signature, String timestamp) {
        return false;
    }
}
