package com.onlinestore.telegrambot.integration.auth;

import java.time.Duration;
import java.time.Instant;

public record BackendAccessToken(String value, Instant expiresAt) {

    public boolean isUsableAt(Instant instant, Duration safetyMargin) {
        return expiresAt != null && expiresAt.isAfter(instant.plus(safetyMargin));
    }
}
