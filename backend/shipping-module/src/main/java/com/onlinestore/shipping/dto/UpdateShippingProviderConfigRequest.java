package com.onlinestore.shipping.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateShippingProviderConfigRequest(
    boolean enabled,
    @NotNull List<@NotBlank String> supportedCountries
) {
}
