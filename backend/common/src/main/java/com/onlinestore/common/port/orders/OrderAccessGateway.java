package com.onlinestore.common.port.orders;

import java.util.Optional;

public interface OrderAccessGateway {

    Optional<OrderAccessView> findById(Long orderId);

    Optional<OrderAccessView> findByIdAndUserId(Long orderId, Long userId);
}
