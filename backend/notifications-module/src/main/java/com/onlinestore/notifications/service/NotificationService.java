package com.onlinestore.notifications.service;

import com.onlinestore.notifications.channel.NotificationChannel;
import com.onlinestore.notifications.channel.NotificationMessage;
import com.onlinestore.notifications.template.TemplateService;
import com.onlinestore.orders.dto.OrderDTO;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final Map<String, NotificationChannel> channelsByType;
    private final TemplateService templateService;

    public NotificationService(List<NotificationChannel> channels, TemplateService templateService) {
        this.channelsByType = channels.stream()
            .collect(Collectors.toMap(NotificationChannel::getChannelType, Function.identity()));
        this.templateService = templateService;
    }

    public void notifyOrderCreated(OrderDTO order) {
        String body = templateService.render(
            "<p>Your order <b>#{{orderId}}</b> has been created.</p>",
            Map.of("orderId", order.id())
        );
        sendEmail("customer-" + order.userId() + "@example.com", "Order created", body);
    }

    public void notifyOrderStatusChanged(OrderDTO order) {
        String body = templateService.render(
            "<p>Order <b>#{{orderId}}</b> status changed to <b>{{status}}</b>.</p>",
            Map.of("orderId", order.id(), "status", order.status())
        );
        sendEmail("customer-" + order.userId() + "@example.com", "Order status updated", body);
    }

    private void sendEmail(String recipient, String subject, String body) {
        NotificationChannel emailChannel = channelsByType.get("EMAIL");
        if (emailChannel == null) {
            return;
        }
        emailChannel.send(new NotificationMessage(recipient, subject, body, Map.of()));
    }
}
