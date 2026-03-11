package com.onlinestore.payments.provider;

import com.onlinestore.common.util.Money;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class BankTransferPaymentProvider implements PaymentProvider {

    @Override
    public String getProviderCode() {
        return "bank_transfer";
    }

    @Override
    public Set<String> getSupportedCountries() {
        return Set.of("DE", "FR", "IT", "ES", "NL", "AT", "BE");
    }

    @Override
    public PaymentResult createPayment(PaymentRequest request) {
        String providerPaymentId = "bank-transfer-" + UUID.randomUUID();
        return new PaymentResult(
            providerPaymentId,
            PaymentResult.PaymentResultStatus.REQUIRES_ACTION,
            request.returnUrl(),
            null,
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
        return new RefundResult("bank-transfer-refund-" + UUID.randomUUID(), true, null);
    }

    @Override
    public boolean verifyWebhook(String payload, String signature, String timestamp) {
        return false;
    }
}
