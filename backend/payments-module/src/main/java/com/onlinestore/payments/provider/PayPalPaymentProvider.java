package com.onlinestore.payments.provider;

import com.onlinestore.common.util.Money;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

@Component
public class PayPalPaymentProvider implements PaymentProvider {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    private final String webhookSecret;

    public PayPalPaymentProvider(@Value("${onlinestore.payments.paypal.webhook-secret:}") String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    @Override
    public String getProviderCode() {
        return "paypal";
    }

    @Override
    public Set<String> getSupportedCountries() {
        return Set.of("US", "DE", "GB", "FR", "IT", "ES", "NL", "PL", "UA");
    }

    @Override
    public PaymentResult createPayment(PaymentRequest request) {
        String providerPaymentId = "paypal-" + UUID.randomUUID();
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
        return new RefundResult("refund-" + UUID.randomUUID(), true, null);
    }

    @Override
    public boolean verifyWebhook(String payload, String signature, String timestamp) {
        if (payload == null || payload.isBlank()
            || signature == null || signature.isBlank()
            || timestamp == null || timestamp.isBlank()) {
            return false;
        }
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return false;
        }

        String normalizedSignature = signature.trim();
        if (normalizedSignature.startsWith(SIGNATURE_PREFIX)) {
            normalizedSignature = normalizedSignature.substring(SIGNATURE_PREFIX.length());
        }

        try {
            var mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            String signedPayload = timestamp.trim() + "." + payload;
            byte[] digest = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = HexFormat.of().formatHex(digest);
            return MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                normalizedSignature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception ex) {
            return false;
        }
    }
}
