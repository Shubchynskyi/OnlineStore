package com.onlinestore.telegrambot.notifications;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.onlinestore.telegrambot.integration.dto.orders.OrderDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManagerOrderEventListenerTest {

    @Mock
    private ManagerNotificationService managerNotificationService;

    @Test
    void routesOrderCreatedEventsToManagerNotifications() {
        ManagerOrderEventListener listener = new ManagerOrderEventListener(managerNotificationService);

        listener.handleOrderEvent(order("PENDING"), "order.created");

        verify(managerNotificationService).notifyOrderCreated(order("PENDING"));
    }

    @Test
    void routesOrderStatusChangedEventsToManagerNotifications() {
        ManagerOrderEventListener listener = new ManagerOrderEventListener(managerNotificationService);

        listener.handleOrderEvent(order("PAID"), "order.status-changed");

        verify(managerNotificationService).notifyOrderStatusChanged(order("PAID"));
    }

    @Test
    void ignoresUnknownRoutingKeys() {
        ManagerOrderEventListener listener = new ManagerOrderEventListener(managerNotificationService);

        listener.handleOrderEvent(order("PAID"), "order.deleted");

        verifyNoMoreInteractions(managerNotificationService);
    }

    private OrderDto order(String status) {
        return new OrderDto(42L, 11L, status, new BigDecimal("19.99"), "USD", List.of(), Instant.parse("2026-03-17T18:00:00Z"));
    }
}
