package com.onlinestore.notifications.channel;

import java.util.Map;

public record NotificationMessage(
    String recipient,
    String subject,
    String body,
    Map<String, Object> data
) {
}
