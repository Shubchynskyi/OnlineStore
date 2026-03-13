package com.onlinestore.shipping.dto;

import java.util.List;

public record ShippingProviderConfigDTO(
    String providerCode,
    String displayName,
    boolean enabled,
    List<String> supportedCountries
) {
}
