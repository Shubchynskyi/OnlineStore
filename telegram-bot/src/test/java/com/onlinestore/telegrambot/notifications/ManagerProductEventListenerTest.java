package com.onlinestore.telegrambot.notifications;

import static org.mockito.Mockito.verify;

import com.onlinestore.telegrambot.notifications.dto.ProductLowStockEventPayload;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManagerProductEventListenerTest {

    @Mock
    private ManagerNotificationService managerNotificationService;

    @Test
    void routesLowStockEventsToManagerNotifications() {
        ManagerProductEventListener listener = new ManagerProductEventListener(managerNotificationService);
        ProductLowStockEventPayload event = new ProductLowStockEventPayload(
            10L,
            "Laptop",
            20L,
            "Silver",
            "SKU-20",
            5,
            5,
            Instant.parse("2026-03-17T18:00:00Z")
        );

        listener.handleLowStockEvent(event);

        verify(managerNotificationService).notifyProductLowStock(event);
    }
}
