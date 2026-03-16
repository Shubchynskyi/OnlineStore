package com.onlinestore.telegrambot.session;

import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserSessionService {

    private final UserSessionStore userSessionStore;

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
            .attributes(new LinkedHashMap<>())
            .updatedAtEpochMillis(System.currentTimeMillis())
            .build();
        return userSessionStore.save(updatedSession);
    }

    public UserSession rememberInput(UserSession userSession, Long chatId, String attributeKey, String attributeValue) {
        LinkedHashMap<String, String> updatedAttributes = new LinkedHashMap<>(userSession.getAttributes());
        updatedAttributes.put(attributeKey, attributeValue);

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
}
