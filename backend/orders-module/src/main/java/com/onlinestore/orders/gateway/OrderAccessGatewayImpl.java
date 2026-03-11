package com.onlinestore.orders.gateway;

import com.onlinestore.common.port.orders.OrderAccessGateway;
import com.onlinestore.common.port.orders.OrderAccessView;
import com.onlinestore.orders.entity.Order;
import com.onlinestore.orders.repository.OrderRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderAccessGatewayImpl implements OrderAccessGateway {

    private final OrderRepository orderRepository;

    @Override
    public Optional<OrderAccessView> findById(Long orderId) {
        return orderRepository.findById(orderId).map(this::toView);
    }

    @Override
    public Optional<OrderAccessView> findByIdAndUserId(Long orderId, Long userId) {
        return orderRepository.findByIdAndUserId(orderId, userId).map(this::toView);
    }

    private OrderAccessView toView(Order order) {
        return new OrderAccessView(
            order.getId(),
            order.getUserId(),
            order.getTotalAmount(),
            order.getTotalCurrency()
        );
    }
}
