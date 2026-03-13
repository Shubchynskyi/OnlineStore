package com.onlinestore.notifications.dto;

import java.util.Map;

public record NotificationEvent(
    String channel,
    String recipient,
    String subject,
    String body,
    Map<String, Object> data
) {
}
