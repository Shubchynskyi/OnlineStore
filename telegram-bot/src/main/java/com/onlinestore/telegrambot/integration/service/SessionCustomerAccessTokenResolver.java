package com.onlinestore.telegrambot.integration.service;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.session.UserSessionStore;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class SessionCustomerAccessTokenResolver implements CustomerAccessTokenResolver {

    private final UserSessionStore userSessionStore;
    private final BotProperties botProperties;

    @Override
    public Optional<String> resolveAccessToken(Long telegramUserId) {
        String attributeKey = botProperties.getBackendApi().getCustomerTokenAttributeKey();
        return userSessionStore.findByUserId(telegramUserId)
            .map(userSession -> userSession.getAttributes().get(attributeKey))
            .filter(StringUtils::hasText);
    }
}
