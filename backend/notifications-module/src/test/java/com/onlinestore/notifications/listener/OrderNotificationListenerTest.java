package com.onlinestore.notifications.listener;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.onlinestore.notifications.service.NotificationService;
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
class OrderNotificationListenerTest {

    @Mock
    private NotificationService notificationService;

    private OrderNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrderNotificationListener(notificationService);
    }

    @Test
    void handleOrderCreatedShouldDelegateToNotificationService() {
        OrderDTO order = orderDto();

        listener.handleOrderEvent(order, "order.created");

        verify(notificationService).notifyOrderCreated(order);
    }

    @Test
    void handleOrderStatusChangedShouldDelegateToNotificationService() {
        OrderDTO order = orderDto();

        listener.handleOrderEvent(order, "order.status-changed");

        verify(notificationService).notifyOrderStatusChanged(order);
    }

    @Test
    void handleUnknownRoutingKeyShouldIgnoreEvent() {
        listener.handleOrderEvent(orderDto(), "order.deleted");

        verifyNoInteractions(notificationService);
    }

    private OrderDTO orderDto() {
        return new OrderDTO(
            10L,
            7L,
            OrderStatus.PENDING,
            new BigDecimal("25.00"),
            "EUR",
            List.of(),
            Instant.parse("2026-03-13T12:00:00Z")
        );
    }
}
