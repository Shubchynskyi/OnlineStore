package com.onlinestore.notifications.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.onlinestore.notifications.channel.NotificationChannel;
import com.onlinestore.notifications.channel.NotificationMessage;
import com.onlinestore.notifications.dto.NotificationEvent;
import com.onlinestore.notifications.template.TemplateService;
import com.onlinestore.orders.dto.OrderDTO;
import com.onlinestore.orders.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultNotificationServiceTest {

    @Mock
    private NotificationChannel emailChannel;
    @Mock
    private NotificationChannel pushChannel;
    @Mock
    private TemplateService templateService;

    private DefaultNotificationService notificationService;

    @BeforeEach
    void setUp() {
        when(emailChannel.getChannelType()).thenReturn("EMAIL");
        when(pushChannel.getChannelType()).thenReturn("PUSH");
        notificationService = new DefaultNotificationService(List.of(emailChannel, pushChannel), templateService);
        clearInvocations(emailChannel, pushChannel, templateService);
    }

    @Test
    void sendShouldRouteNotificationToNamedChannel() {
        var event = new NotificationEvent(
            "push",
            "user:7",
            "Subject",
            "Body",
            Map.of("orderId", 10L)
        );

        notificationService.send(event);

        verify(pushChannel).send(new NotificationMessage(
            "user:7",
            "Subject",
            "Body",
            Map.of("orderId", 10L)
        ));
        verifyNoInteractions(emailChannel);
    }

    @Test
    void notifyOrderCreatedShouldDispatchEmailAndPushMessages() {
        var order = orderDto(OrderStatus.PENDING);
        when(templateService.render(
            eq("<p>Your order <b>#{{orderId}}</b> has been created.</p>"),
            eq(Map.of("orderId", 10L))
        )).thenReturn("<p>Your order <b>#10</b> has been created.</p>");

        notificationService.notifyOrderCreated(order);

        Map<String, Object> data = Map.of(
            "eventType", "order.created",
            "orderId", 10L,
            "status", "PENDING",
            "userId", 7L
        );
        verify(emailChannel).send(new NotificationMessage(
            "customer-7@example.com",
            "Order created",
            "<p>Your order <b>#10</b> has been created.</p>",
            data
        ));
        verify(pushChannel).send(new NotificationMessage(
            "user:7",
            "Order created",
            "Order #10 has been created.",
            data
        ));
    }

    @Test
    void notifyOrderStatusChangedShouldDispatchEmailAndPushMessages() {
        var order = orderDto(OrderStatus.PAID);
        when(templateService.render(
            eq("<p>Order <b>#{{orderId}}</b> status changed to <b>{{status}}</b>.</p>"),
            eq(Map.of("orderId", 10L, "status", "PAID"))
        )).thenReturn("<p>Order <b>#10</b> status changed to <b>PAID</b>.</p>");

        notificationService.notifyOrderStatusChanged(order);

        Map<String, Object> data = Map.of(
            "eventType", "order.status-changed",
            "orderId", 10L,
            "status", "PAID",
            "userId", 7L
        );
        verify(emailChannel).send(new NotificationMessage(
            "customer-7@example.com",
            "Order status updated",
            "<p>Order <b>#10</b> status changed to <b>PAID</b>.</p>",
            data
        ));
        verify(pushChannel).send(new NotificationMessage(
            "user:7",
            "Order status updated",
            "Order #10 status changed to PAID.",
            data
        ));
    }

    @Test
    void notifyOrderCreatedShouldAttemptPushEvenWhenEmailFails() {
        var order = orderDto(OrderStatus.PENDING);
        when(templateService.render(
            eq("<p>Your order <b>#{{orderId}}</b> has been created.</p>"),
            eq(Map.of("orderId", 10L))
        )).thenReturn("<p>Your order <b>#10</b> has been created.</p>");
        doThrow(new RuntimeException("Email sending failed"))
            .when(emailChannel)
            .send(new NotificationMessage(
                "customer-7@example.com",
                "Order created",
                "<p>Your order <b>#10</b> has been created.</p>",
                Map.of(
                    "eventType", "order.created",
                    "orderId", 10L,
                    "status", "PENDING",
                    "userId", 7L
                )
            ));

        assertThrows(IllegalStateException.class, () -> notificationService.notifyOrderCreated(order));

        verify(pushChannel).send(new NotificationMessage(
            "user:7",
            "Order created",
            "Order #10 has been created.",
            Map.of(
                "eventType", "order.created",
                "orderId", 10L,
                "status", "PENDING",
                "userId", 7L
            )
        ));
    }

    private OrderDTO orderDto(OrderStatus status) {
        return new OrderDTO(
            10L,
            7L,
            status,
            new BigDecimal("25.00"),
            "EUR",
            List.of(),
            Instant.parse("2026-03-13T12:00:00Z")
        );
    }
}
