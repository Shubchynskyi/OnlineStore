package com.onlinestore.realtime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.onlinestore.orders.dto.OrderDTO;
import com.onlinestore.orders.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderRealtimeListenerTest {

    @Mock
    private RealtimeMessageBroadcaster realtimeMessageBroadcaster;

    private OrderRealtimeListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrderRealtimeListener(realtimeMessageBroadcaster);
    }

    @Test
    void handleOrderStatusChangedShouldBroadcastToRealtimeTopic() {
        OrderDTO order = orderDto();

        listener.handleOrderEvent(order, "order.status-changed");

        verify(realtimeMessageBroadcaster).broadcastOrderUpdate(order, "order.status-changed");
    }

    @Test
    void handleOrderCreatedShouldBroadcastToRealtimeTopic() {
        OrderDTO order = orderDto();

        listener.handleOrderEvent(order, "order.created");

        verify(realtimeMessageBroadcaster).broadcastOrderUpdate(order, "order.created");
    }

    @Test
    void handleUnknownOrderRoutingKeyShouldIgnoreEvent() {
        listener.handleOrderEvent(orderDto(), "payment.completed");

        verifyNoInteractions(realtimeMessageBroadcaster);
    }

    private OrderDTO orderDto() {
        return new OrderDTO(
            25L,
            7L,
            OrderStatus.PAID,
            new BigDecimal("45.00"),
            "EUR",
            List.of(),
            Instant.parse("2026-03-13T12:00:00Z")
        );
    }
}
