package com.onlinestore.shipping.repository;

import com.onlinestore.shipping.entity.ShippingProviderConfig;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShippingProviderConfigRepository extends JpaRepository<ShippingProviderConfig, Long> {

    Optional<ShippingProviderConfig> findByProviderCode(String providerCode);

    List<ShippingProviderConfig> findByEnabledTrue();
}
