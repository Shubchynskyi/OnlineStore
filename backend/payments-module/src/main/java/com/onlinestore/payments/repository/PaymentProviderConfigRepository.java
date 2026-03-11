package com.onlinestore.payments.repository;

import com.onlinestore.payments.entity.PaymentProviderConfig;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentProviderConfigRepository extends JpaRepository<PaymentProviderConfig, Long> {

    Optional<PaymentProviderConfig> findByProviderCode(String providerCode);

    List<PaymentProviderConfig> findByEnabledTrue();
}
