package com.onlinestore.orders.statemachine;

import com.onlinestore.common.exception.BusinessException;
import com.onlinestore.orders.entity.OrderEvent;
import com.onlinestore.orders.entity.OrderStatus;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class OrderStateMachineConfig {

    private final Map<OrderStatus, Map<OrderEvent, OrderStatus>> transitions = Map.of(
        OrderStatus.PENDING, Map.of(
            OrderEvent.PAYMENT_INITIATED, OrderStatus.AWAITING_PAYMENT,
            OrderEvent.CANCEL_REQUEST, OrderStatus.CANCELLED
        ),
        OrderStatus.AWAITING_PAYMENT, Map.of(
            OrderEvent.PAYMENT_RECEIVED, OrderStatus.PAID,
            OrderEvent.PAYMENT_FAILED, OrderStatus.CANCELLED
        ),
        OrderStatus.PAID, Map.of(
            OrderEvent.MANAGER_CONFIRM, OrderStatus.PROCESSING,
            OrderEvent.REFUND_COMPLETED, OrderStatus.REFUNDED
        ),
        OrderStatus.PROCESSING, Map.of(
            OrderEvent.SHIPMENT_CREATED, OrderStatus.SHIPPED
        ),
        OrderStatus.SHIPPED, Map.of(
            OrderEvent.DELIVERY_CONFIRMED, OrderStatus.DELIVERED
        )
    );

    public OrderStatus apply(OrderStatus currentStatus, OrderEvent event) {
        OrderStatus newStatus = transitions.getOrDefault(currentStatus, Map.of()).get(event);
        if (newStatus == null) {
            throw new BusinessException(
                "INVALID_TRANSITION",
                "Cannot transition from %s with event %s".formatted(currentStatus, event)
            );
        }
        return newStatus;
    }

    public Set<OrderEvent> allowedEvents(OrderStatus status) {
        return transitions.getOrDefault(status, Map.of()).keySet();
    }
}
