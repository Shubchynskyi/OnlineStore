package com.onlinestore.telegrambot.integration.auth;

public interface BackendServiceAccessTokenProvider {

    boolean isEnabled();

    String getAccessToken();
}
