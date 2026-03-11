package com.onlinestore.payments.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.onlinestore.common.exception.BusinessException;
import com.onlinestore.payments.entity.PaymentProviderConfig;
import com.onlinestore.payments.provider.BankTransferPaymentProvider;
import com.onlinestore.payments.provider.CardPaymentProvider;
import com.onlinestore.payments.provider.CryptoPaymentProvider;
import com.onlinestore.payments.provider.PayPalPaymentProvider;
import com.onlinestore.payments.provider.PaymentProvider;
import com.onlinestore.payments.repository.PaymentProviderConfigRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentProviderRegistryTest {

    @Mock
    private PaymentProviderConfigRepository configRepository;

    private PaymentProviderRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new PaymentProviderRegistry(
            List.of(
                new PayPalPaymentProvider("test-secret"),
                new CardPaymentProvider(),
                new BankTransferPaymentProvider(),
                new CryptoPaymentProvider()
            ),
            configRepository
        );
    }

    @Test
    void getProviderShouldReturnEnabledCardProvider() {
        when(configRepository.findByProviderCode("card"))
            .thenReturn(Optional.of(providerConfig("card", true)));

        var provider = registry.getProvider("card");

        assertEquals("card", provider.getProviderCode());
    }

    @Test
    void getProviderShouldRejectDisabledBankTransferProvider() {
        when(configRepository.findByProviderCode("bank_transfer"))
            .thenReturn(Optional.of(providerConfig("bank_transfer", false)));

        var exception = assertThrows(BusinessException.class, () -> registry.getProvider("bank_transfer"));

        assertEquals("PROVIDER_DISABLED", exception.getErrorCode());
    }

    @Test
    void getProviderForWebhookShouldResolveCryptoProviderWithoutEnabledCheck() {
        var provider = registry.getProviderForWebhook("crypto");

        assertEquals("crypto", provider.getProviderCode());
    }

    @Test
    void getEnabledProvidersShouldReturnOnlyRegisteredEnabledProviders() {
        when(configRepository.findByEnabledTrue()).thenReturn(List.of(
            providerConfig("paypal", true),
            providerConfig("card", true),
            providerConfig("legacy_provider", true)
        ));

        var enabledCodes = registry.getEnabledProviders().stream()
            .map(PaymentProvider::getProviderCode)
            .toList();

        assertEquals(List.of("paypal", "card"), enabledCodes);
    }

    private PaymentProviderConfig providerConfig(String providerCode, boolean enabled) {
        var config = new PaymentProviderConfig();
        config.setProviderCode(providerCode);
        config.setDisplayName(providerCode);
        config.setEnabled(enabled);
        config.setSupportedCountries(List.of("DE"));
        return config;
    }
}
