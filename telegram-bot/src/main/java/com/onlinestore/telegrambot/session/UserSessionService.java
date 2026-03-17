package com.onlinestore.telegrambot.session;

import com.onlinestore.telegrambot.config.BotProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserSessionService {

    private final UserSessionStore userSessionStore;
    private final BotProperties botProperties;

    public UserSession getOrCreate(Long userId, Long chatId) {
        return userSessionStore.findByUserId(userId)
            .map(existingSession -> refreshChatBinding(existingSession, chatId))
            .orElseGet(() -> userSessionStore.save(UserSession.initial(userId, chatId)));
    }

    public UserSession transitionTo(UserSession userSession, Long chatId, UserState nextState, String lastCommand) {
        UserSession updatedSession = userSession.toBuilder()
            .chatId(chatId)
            .state(nextState)
            .lastCommand(lastCommand)
            .attributes(preserveDurableAttributes(userSession))
            .updatedAtEpochMillis(System.currentTimeMillis())
            .build();
        return userSessionStore.save(updatedSession);
    }

    public UserSession rememberInput(UserSession userSession, Long chatId, String attributeKey, String attributeValue) {
        return rememberInputs(userSession, chatId, Map.of(attributeKey, attributeValue));
    }

    public UserSession rememberInputs(UserSession userSession, Long chatId, Map<String, String> attributes) {
        LinkedHashMap<String, String> updatedAttributes = new LinkedHashMap<>(userSession.getAttributes());
        attributes.forEach((attributeKey, attributeValue) -> {
            if (attributeValue == null) {
                updatedAttributes.remove(attributeKey);
            } else {
                updatedAttributes.put(attributeKey, attributeValue);
            }
        });

        UserSession updatedSession = userSession.toBuilder()
            .chatId(chatId)
            .attributes(updatedAttributes)
            .updatedAtEpochMillis(System.currentTimeMillis())
            .build();
        return userSessionStore.save(updatedSession);
    }

    private UserSession refreshChatBinding(UserSession userSession, Long chatId) {
        if (chatId.equals(userSession.getChatId())) {
            return userSession;
        }

        UserSession reboundSession = userSession.toBuilder()
            .chatId(chatId)
            .updatedAtEpochMillis(System.currentTimeMillis())
            .build();
        return userSessionStore.save(reboundSession);
    }

    private LinkedHashMap<String, String> preserveDurableAttributes(UserSession userSession) {
        LinkedHashMap<String, String> durableAttributes = new LinkedHashMap<>();
        String tokenAttributeKey = botProperties.getBackendApi().getCustomerTokenAttributeKey();
        userSession.getAttributes().forEach((attributeKey, attributeValue) -> {
            if (attributeValue != null && isDurableAttribute(attributeKey, tokenAttributeKey)) {
                durableAttributes.put(attributeKey, attributeValue);
            }
        });
        return durableAttributes;
    }

    private boolean isDurableAttribute(String attributeKey, String tokenAttributeKey) {
        return tokenAttributeKey.equals(attributeKey)
            || attributeKey.endsWith("SubmissionState")
            || attributeKey.endsWith("SubmissionKey")
            || attributeKey.endsWith("SubmissionFingerprint");
    }
}
