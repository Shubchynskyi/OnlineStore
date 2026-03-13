package com.onlinestore.shipping.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onlinestore.common.exception.BusinessException;
import com.onlinestore.shipping.dto.UpdateShippingProviderConfigRequest;
import com.onlinestore.shipping.entity.ShippingProviderConfig;
import com.onlinestore.shipping.provider.ShippingProvider;
import com.onlinestore.shipping.registry.ShippingProviderRegistry;
import com.onlinestore.shipping.repository.ShippingProviderConfigRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShippingProviderConfigServiceTest {

    @Mock
    private ShippingProviderConfigRepository configRepository;
    @Mock
    private ShippingProviderRegistry providerRegistry;
    @Mock
    private ShippingProvider shippingProvider;

    private ShippingProviderConfigService service;

    @BeforeEach
    void setUp() {
        service = new ShippingProviderConfigService(configRepository, providerRegistry);
    }

    @Test
    void updateProviderConfigShouldNormalizeCountriesAndPersist() {
        var config = providerConfig("dhl", true, List.of("DE"));
        when(providerRegistry.getProviderForExistingShipment("dhl")).thenReturn(shippingProvider);
        when(shippingProvider.getSupportedCountries()).thenReturn(Set.of("DE", "FR", "NL"));
        when(configRepository.findByProviderCode("dhl")).thenReturn(Optional.of(config));
        when(configRepository.save(any(ShippingProviderConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var updated = service.updateProviderConfig(
            "dhl",
            new UpdateShippingProviderConfigRequest(false, List.of(" de ", "FR", "DE"))
        );

        assertFalse(updated.enabled());
        assertEquals(List.of("DE", "FR"), updated.supportedCountries());
    }

    @Test
    void updateProviderConfigShouldRejectUnsupportedCountries() {
        var config = providerConfig("dhl", true, List.of("DE"));
        when(providerRegistry.getProviderForExistingShipment("dhl")).thenReturn(shippingProvider);
        when(shippingProvider.getSupportedCountries()).thenReturn(Set.of("DE", "FR"));
        when(configRepository.findByProviderCode("dhl")).thenReturn(Optional.of(config));

        var exception = assertThrows(
            BusinessException.class,
            () -> service.updateProviderConfig("dhl", new UpdateShippingProviderConfigRequest(true, List.of("DE", "UA")))
        );

        assertEquals("SHIPPING_PROVIDER_COUNTRY_NOT_SUPPORTED", exception.getErrorCode());
        verify(configRepository, never()).save(any(ShippingProviderConfig.class));
    }

    private ShippingProviderConfig providerConfig(String providerCode, boolean enabled, List<String> supportedCountries) {
        var config = new ShippingProviderConfig();
        config.setProviderCode(providerCode);
        config.setDisplayName(providerCode);
        config.setEnabled(enabled);
        config.setSupportedCountries(supportedCountries);
        return config;
    }
}
