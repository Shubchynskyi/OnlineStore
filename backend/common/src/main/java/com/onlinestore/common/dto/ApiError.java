package com.onlinestore.common.dto;

import java.time.Instant;
import java.util.List;

public record ApiError(
    int status,
    String error,
    String message,
    String path,
    Instant timestamp,
    List<FieldError> fieldErrors
) {

    public record FieldError(String field, String message) {
    }

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(status, error, message, path, Instant.now(), List.of());
    }
}
