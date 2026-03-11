package com.onlinestore.notifications.dto;

import java.time.Instant;

public record NotificationDTO(
    String channel,
    String recipient,
    String subject,
    String status,
    Instant sentAt
) {
}
