package com.onlinestore.payments.provider;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class StubPaymentProviderWebhookSecurityTest {

    @ParameterizedTest
    @MethodSource("stubProviders")
    void verifyWebhookShouldFailClosedForStubProviders(PaymentProvider provider) {
        assertFalse(provider.verifyWebhook("{\"eventId\":\"evt-1\"}", "signature", "1234567890"));
    }

    private static Stream<PaymentProvider> stubProviders() {
        return Stream.of(
            new CardPaymentProvider(),
            new BankTransferPaymentProvider(),
            new CryptoPaymentProvider()
        );
    }
}
