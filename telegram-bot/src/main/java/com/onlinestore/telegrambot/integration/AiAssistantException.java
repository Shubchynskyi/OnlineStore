package com.onlinestore.telegrambot.integration;

import lombok.Getter;

@Getter
public class AiAssistantException extends RuntimeException {

    private final String operation;
    private final Integer statusCode;

    public AiAssistantException(String operation, String message, Integer statusCode, Throwable cause) {
        super(message, cause);
        this.operation = operation;
        this.statusCode = statusCode;
    }
}
