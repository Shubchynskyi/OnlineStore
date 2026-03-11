package com.onlinestore.payments.repository;

import com.onlinestore.payments.entity.Payment;
import com.onlinestore.payments.entity.PaymentStatus;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByProviderCodeAndProviderPaymentId(String providerCode, String providerPaymentId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findFirstByOrderIdAndProviderCodeAndStatusInOrderByCreatedAtDesc(
        Long orderId,
        String providerCode,
        Collection<PaymentStatus> statuses
    );
}
