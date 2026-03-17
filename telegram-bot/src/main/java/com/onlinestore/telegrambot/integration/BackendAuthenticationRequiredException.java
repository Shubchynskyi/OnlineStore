package com.onlinestore.telegrambot.integration;

public class BackendAuthenticationRequiredException extends RuntimeException {

    public BackendAuthenticationRequiredException(String message) {
        super(message);
    }
}
