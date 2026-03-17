package com.onlinestore.telegrambot.integration.service;

import java.util.Optional;

public interface CustomerAccessTokenResolver {

    Optional<String> resolveAccessToken(Long telegramUserId);
}
