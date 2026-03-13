package com.onlinestore.payments.repository;

import com.onlinestore.payments.entity.PaymentMutation;
import com.onlinestore.payments.entity.PaymentMutationStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentMutationRepository extends JpaRepository<PaymentMutation, Long> {

    Optional<PaymentMutation> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentMutation> findFirstByPaymentIdAndStatusOrderByCreatedAtDesc(
        Long paymentId,
        PaymentMutationStatus status
    );
}
