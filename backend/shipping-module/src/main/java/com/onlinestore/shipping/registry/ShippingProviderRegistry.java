package com.onlinestore.shipping.registry;

import com.onlinestore.common.exception.BusinessException;
import com.onlinestore.shipping.provider.ShippingProvider;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ShippingProviderRegistry {

    private final Map<String, ShippingProvider> providers;

    public ShippingProviderRegistry(List<ShippingProvider> providerList) {
        this.providers = providerList.stream()
            .collect(Collectors.toMap(ShippingProvider::getProviderCode, Function.identity()));
    }

    public ShippingProvider getByCode(String providerCode) {
        var provider = providers.get(providerCode);
        if (provider == null) {
            throw new BusinessException("UNKNOWN_SHIPPING_PROVIDER", "Unknown shipping provider: " + providerCode);
        }
        return provider;
    }

    public ShippingProvider getByCountry(String countryCode) {
        return providers.values().stream()
            .filter(provider -> provider.getSupportedCountries().contains(countryCode))
            .findFirst()
            .orElseThrow(() -> new BusinessException(
                "NO_SHIPPING_PROVIDER",
                "No shipping provider available for country: " + countryCode
            ));
    }
}
