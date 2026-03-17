package com.onlinestore.telegrambot.integration.dto;

import java.time.Instant;
import java.util.List;

public record BackendApiError(
    int status,
    String error,
    String message,
    String path,
    Instant timestamp,
    List<FieldError> fieldErrors
) {

    public record FieldError(String field, String message) {
    }
}
