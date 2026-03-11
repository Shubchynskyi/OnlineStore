package com.onlinestore.payments.registry;

import com.onlinestore.common.exception.BusinessException;
import com.onlinestore.payments.provider.PaymentProvider;
import com.onlinestore.payments.repository.PaymentProviderConfigRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class PaymentProviderRegistry {

    private final Map<String, PaymentProvider> providers;
    private final PaymentProviderConfigRepository configRepository;

    public PaymentProviderRegistry(
        List<PaymentProvider> providerList,
        PaymentProviderConfigRepository configRepository
    ) {
        this.providers = providerList.stream()
            .collect(Collectors.toMap(PaymentProvider::getProviderCode, Function.identity()));
        this.configRepository = configRepository;
    }

    public PaymentProvider getProvider(String code) {
        var provider = getRegisteredProvider(code);
        if (!isEnabled(code)) {
            throw new BusinessException("PROVIDER_DISABLED", "Payment provider is disabled: " + code);
        }
        return provider;
    }

    public PaymentProvider getProviderForWebhook(String code) {
        return getRegisteredProvider(code);
    }

    public List<PaymentProvider> getEnabledProviders() {
        return configRepository.findByEnabledTrue().stream()
            .map(cfg -> providers.get(cfg.getProviderCode()))
            .filter(Objects::nonNull)
            .toList();
    }

    public PaymentProvider getProviderForCountry(String countryCode) {
        return getEnabledProviders().stream()
            .filter(provider -> provider.getSupportedCountries().contains(countryCode))
            .findFirst()
            .orElseThrow(() -> new BusinessException(
                "NO_PROVIDER",
                "No payment provider available for country: " + countryCode
            ));
    }

    private PaymentProvider getRegisteredProvider(String code) {
        var provider = providers.get(code);
        if (provider == null) {
            throw new BusinessException("UNKNOWN_PROVIDER", "Unknown payment provider: " + code);
        }
        return provider;
    }

    private boolean isEnabled(String code) {
        return configRepository.findByProviderCode(code)
            .map(config -> config.isEnabled())
            .orElse(false);
    }
}
