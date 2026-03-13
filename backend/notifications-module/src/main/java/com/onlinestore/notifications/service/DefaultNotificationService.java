package com.onlinestore.notifications.service;

import com.onlinestore.notifications.channel.NotificationChannel;
import com.onlinestore.notifications.channel.NotificationMessage;
import com.onlinestore.notifications.dto.NotificationEvent;
import com.onlinestore.notifications.template.TemplateService;
import com.onlinestore.orders.dto.OrderDTO;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DefaultNotificationService implements NotificationService {

    private static final String EMAIL_CHANNEL = "EMAIL";
    private static final String PUSH_CHANNEL = "PUSH";

    private final Map<String, NotificationChannel> channelsByType;
    private final TemplateService templateService;

    public DefaultNotificationService(List<NotificationChannel> channels, TemplateService templateService) {
        this.channelsByType = channels.stream()
            .collect(Collectors.toMap(
                channel -> normalizeChannel(channel.getChannelType()),
                Function.identity()
            ));
        this.templateService = templateService;
    }

    @Override
    public void send(NotificationEvent event) {
        String channelType = normalizeChannel(event.channel());
        NotificationChannel channel = channelsByType.get(channelType);
        if (channel == null) {
            log.warn(
                "Notification channel {} is not configured for recipient {}",
                channelType,
                event.recipient()
            );
            return;
        }

        channel.send(new NotificationMessage(
            event.recipient(),
            event.subject(),
            event.body(),
            event.data() == null ? Map.of() : event.data()
        ));
    }

    @Override
    public void notifyOrderCreated(OrderDTO order) {
        String emailBody = templateService.render(
            "<p>Your order <b>#{{orderId}}</b> has been created.</p>",
            Map.of("orderId", order.id())
        );
        sendOrderNotifications(
            order,
            "order.created",
            "Order created",
            emailBody,
            "Order #%d has been created.".formatted(order.id())
        );
    }

    @Override
    public void notifyOrderStatusChanged(OrderDTO order) {
        String status = order.status().name();
        String emailBody = templateService.render(
            "<p>Order <b>#{{orderId}}</b> status changed to <b>{{status}}</b>.</p>",
            Map.of("orderId", order.id(), "status", status)
        );
        sendOrderNotifications(
            order,
            "order.status-changed",
            "Order status updated",
            emailBody,
            "Order #%d status changed to %s.".formatted(order.id(), status)
        );
    }

    private void sendOrderNotifications(
        OrderDTO order,
        String eventType,
        String subject,
        String emailBody,
        String pushBody
    ) {
        Map<String, Object> data = Map.of(
            "eventType", eventType,
            "orderId", order.id(),
            "status", order.status().name(),
            "userId", order.userId()
        );
        List<NotificationEvent> deliveries = List.of(
            new NotificationEvent(
                EMAIL_CHANNEL,
                "customer-" + order.userId() + "@example.com",
                subject,
                emailBody,
                data
            ),
            new NotificationEvent(
                PUSH_CHANNEL,
                "user:" + order.userId(),
                subject,
                pushBody,
                data
            )
        );

        IllegalStateException aggregatedFailure = null;
        for (NotificationEvent delivery : deliveries) {
            try {
                send(delivery);
            } catch (RuntimeException ex) {
                if (aggregatedFailure == null) {
                    aggregatedFailure = new IllegalStateException(
                        "Notification delivery failed for order " + order.id()
                    );
                }
                aggregatedFailure.addSuppressed(ex);
                log.error(
                    "Failed to deliver {} notification for order {}",
                    delivery.channel(),
                    order.id(),
                    ex
                );
            }
        }

        if (aggregatedFailure != null) {
            throw aggregatedFailure;
        }
    }

    private static String normalizeChannel(String channelType) {
        if (channelType == null) {
            return "";
        }
        return channelType.trim().toUpperCase(Locale.ROOT);
    }
}
