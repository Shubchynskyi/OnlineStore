package com.onlinestore.shipping.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.onlinestore.common.exception.BusinessException;
import com.onlinestore.shipping.entity.ShippingProviderConfig;
import com.onlinestore.shipping.provider.DhlEuropeShippingProvider;
import com.onlinestore.shipping.provider.NovaPoshtaShippingProvider;
import com.onlinestore.shipping.provider.StubShippingProvider;
import com.onlinestore.shipping.repository.ShippingProviderConfigRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShippingProviderRegistryTest {

    @Mock
    private ShippingProviderConfigRepository configRepository;

    private ShippingProviderRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ShippingProviderRegistry(
            List.of(
                new DhlEuropeShippingProvider(),
                new NovaPoshtaShippingProvider(),
                new StubShippingProvider()
            ),
            configRepository
        );
    }

    @Test
    void getByCodeShouldReturnEnabledProvider() {
        when(configRepository.findByProviderCode("dhl"))
            .thenReturn(Optional.of(config("dhl", true, List.of("DE", "FR"))));

        var provider = registry.getByCode("dhl");

        assertEquals("dhl", provider.getProviderCode());
    }

    @Test
    void getByCodeShouldRejectDisabledProvider() {
        when(configRepository.findByProviderCode("stub"))
            .thenReturn(Optional.of(config("stub", false, List.of("DE"))));

        var exception = assertThrows(BusinessException.class, () -> registry.getByCode("stub"));

        assertEquals("SHIPPING_PROVIDER_DISABLED", exception.getErrorCode());
    }

    @Test
    void getProviderForExistingShipmentShouldReturnRegisteredProviderEvenWhenDisabled() {
        var provider = registry.getProviderForExistingShipment("stub");

        assertEquals("stub", provider.getProviderCode());
    }

    @Test
    void getProvidersForCountryShouldReturnEnabledConfiguredProvidersOnly() {
        when(configRepository.findByEnabledTrue()).thenReturn(List.of(
            config("dhl", true, List.of("DE", "FR")),
            config("nova_poshta", true, List.of("UA")),
            config("legacy_provider", true, List.of("DE"))
        ));

        var providers = registry.getProvidersForCountry("DE").stream()
            .map(provider -> provider.getProviderCode())
            .toList();

        assertEquals(List.of("dhl"), providers);
    }

    private ShippingProviderConfig config(String providerCode, boolean enabled, List<String> supportedCountries) {
        var config = new ShippingProviderConfig();
        config.setProviderCode(providerCode);
        config.setDisplayName(providerCode);
        config.setEnabled(enabled);
        config.setSupportedCountries(supportedCountries);
        return config;
    }
}
