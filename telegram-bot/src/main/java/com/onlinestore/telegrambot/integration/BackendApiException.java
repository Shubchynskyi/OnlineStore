package com.onlinestore.telegrambot.integration;

import com.onlinestore.telegrambot.integration.dto.BackendApiError;
import lombok.Getter;

@Getter
public class BackendApiException extends RuntimeException {

    private final String operation;
    private final Integer statusCode;
    private final String errorCode;
    private final BackendApiError backendApiError;

    public BackendApiException(
        String operation,
        String message,
        Integer statusCode,
        String errorCode,
        BackendApiError backendApiError,
        Throwable cause
    ) {
        super(message, cause);
        this.operation = operation;
        this.statusCode = statusCode;
        this.errorCode = errorCode;
        this.backendApiError = backendApiError;
    }
}
