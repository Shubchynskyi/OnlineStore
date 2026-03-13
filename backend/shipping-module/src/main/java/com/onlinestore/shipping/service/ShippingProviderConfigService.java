package com.onlinestore.shipping.service;

import com.onlinestore.common.exception.BusinessException;
import com.onlinestore.common.exception.ResourceNotFoundException;
import com.onlinestore.shipping.dto.ShippingProviderConfigDTO;
import com.onlinestore.shipping.dto.UpdateShippingProviderConfigRequest;
import com.onlinestore.shipping.registry.ShippingProviderRegistry;
import com.onlinestore.shipping.repository.ShippingProviderConfigRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShippingProviderConfigService {

    private final ShippingProviderConfigRepository configRepository;
    private final ShippingProviderRegistry providerRegistry;

    public List<ShippingProviderConfigDTO> listConfigs() {
        return configRepository.findAll().stream()
            .sorted(Comparator.comparing(config -> config.getProviderCode().toLowerCase(Locale.ROOT)))
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public ShippingProviderConfigDTO updateProviderConfig(String providerCode, UpdateShippingProviderConfigRequest request) {
        var normalizedProviderCode = providerCode.trim().toLowerCase(Locale.ROOT);
        var provider = providerRegistry.getProviderForExistingShipment(normalizedProviderCode);
        var config = configRepository.findByProviderCode(normalizedProviderCode)
            .orElseThrow(() -> new ResourceNotFoundException("ShippingProviderConfig", "providerCode", normalizedProviderCode));

        var normalizedCountries = normalizeCountries(request.supportedCountries());
        if (request.enabled() && normalizedCountries.isEmpty()) {
            throw new BusinessException(
                "SHIPPING_PROVIDER_COUNTRIES_REQUIRED",
                "Enabled shipping provider must have at least one supported country"
            );
        }

        var unsupportedCountries = normalizedCountries.stream()
            .filter(country -> !provider.getSupportedCountries().contains(country))
            .toList();
        if (!unsupportedCountries.isEmpty()) {
            throw new BusinessException(
                "SHIPPING_PROVIDER_COUNTRY_NOT_SUPPORTED",
                "Provider does not support countries: " + String.join(", ", unsupportedCountries)
            );
        }

        config.setEnabled(request.enabled());
        config.setSupportedCountries(normalizedCountries);
        return toDto(configRepository.save(config));
    }

    private List<String> normalizeCountries(List<String> supportedCountries) {
        return supportedCountries.stream()
            .filter(StringUtils::hasText)
            .map(country -> country.trim().toUpperCase(Locale.ROOT))
            .distinct()
            .toList();
    }

    private ShippingProviderConfigDTO toDto(com.onlinestore.shipping.entity.ShippingProviderConfig config) {
        return new ShippingProviderConfigDTO(
            config.getProviderCode(),
            config.getDisplayName(),
            config.isEnabled(),
            List.copyOf(config.getSupportedCountries())
        );
    }
}
