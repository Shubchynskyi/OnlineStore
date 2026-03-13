package com.onlinestore.shipping.api;

import com.onlinestore.shipping.dto.ShippingProviderConfigDTO;
import com.onlinestore.shipping.dto.UpdateShippingProviderConfigRequest;
import com.onlinestore.shipping.service.ShippingProviderConfigService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/shipping/providers")
@RequiredArgsConstructor
@Validated
public class AdminShippingController {

    private final ShippingProviderConfigService shippingProviderConfigService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public List<ShippingProviderConfigDTO> listProviderConfigs() {
        return shippingProviderConfigService.listConfigs();
    }

    @PutMapping("/{providerCode}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ShippingProviderConfigDTO updateProviderConfig(
        @PathVariable String providerCode,
        @Valid @RequestBody UpdateShippingProviderConfigRequest request
    ) {
        return shippingProviderConfigService.updateProviderConfig(providerCode, request);
    }
}
