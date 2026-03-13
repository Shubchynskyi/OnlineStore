package com.onlinestore.shipping.registry;

import com.onlinestore.common.exception.BusinessException;
import com.onlinestore.shipping.provider.ShippingProvider;
import com.onlinestore.shipping.repository.ShippingProviderConfigRepository;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class ShippingProviderRegistry {

    private final java.util.Map<String, ShippingProvider> providers;
    private final ShippingProviderConfigRepository configRepository;

    public ShippingProviderRegistry(List<ShippingProvider> providerList, ShippingProviderConfigRepository configRepository) {
        this.providers = providerList.stream()
            .collect(Collectors.toMap(ShippingProvider::getProviderCode, Function.identity()));
        this.configRepository = configRepository;
    }

    public ShippingProvider getByCode(String providerCode) {
        var normalizedProviderCode = normalizeProviderCode(providerCode);
        var provider = getProviderForExistingShipment(normalizedProviderCode);
        if (!isEnabled(normalizedProviderCode)) {
            throw new BusinessException("SHIPPING_PROVIDER_DISABLED", "Shipping provider is disabled: " + normalizedProviderCode);
        }
        return provider;
    }

    public ShippingProvider getProviderForExistingShipment(String providerCode) {
        var normalizedProviderCode = normalizeProviderCode(providerCode);
        // Existing shipments must remain traceable/cancellable even after a provider is disabled for new bookings.
        var provider = providers.get(normalizedProviderCode);
        if (provider == null) {
            throw new BusinessException("UNKNOWN_SHIPPING_PROVIDER", "Unknown shipping provider: " + normalizedProviderCode);
        }
        return provider;
    }

    public List<ShippingProvider> getEnabledProviders() {
        return configRepository.findByEnabledTrue().stream()
            .map(config -> providers.get(config.getProviderCode()))
            .filter(Objects::nonNull)
            .toList();
    }

    public List<ShippingProvider> getProvidersForCountry(String countryCode) {
        String normalizedCountryCode = normalizeCountryCode(countryCode);

        return configRepository.findByEnabledTrue().stream()
            .filter(config -> supportsCountry(config.getSupportedCountries(), normalizedCountryCode))
            .map(config -> providers.get(config.getProviderCode()))
            .filter(Objects::nonNull)
            .filter(provider -> provider.getSupportedCountries().contains(normalizedCountryCode))
            .toList();
    }

    public ShippingProvider getByCountry(String countryCode) {
        return getProvidersForCountry(countryCode).stream()
            .findFirst()
            .orElseThrow(() -> new BusinessException(
                "NO_SHIPPING_PROVIDER",
                "No shipping provider available for country: " + countryCode
            ));
    }

    private boolean isEnabled(String providerCode) {
        return configRepository.findByProviderCode(providerCode)
            .map(config -> config.isEnabled())
            .orElse(false);
    }

    private boolean supportsCountry(List<String> configuredCountries, String countryCode) {
        return Optional.ofNullable(configuredCountries)
            .orElseGet(List::of)
            .stream()
            .map(this::normalizeCountryCode)
            .anyMatch(countryCode::equals);
    }

    private String normalizeCountryCode(String countryCode) {
        return countryCode == null ? "" : countryCode.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeProviderCode(String providerCode) {
        return providerCode == null ? "" : providerCode.trim().toLowerCase(Locale.ROOT);
    }
}
