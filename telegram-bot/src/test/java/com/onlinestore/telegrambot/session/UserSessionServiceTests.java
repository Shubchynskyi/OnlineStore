package com.onlinestore.telegrambot.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserSessionServiceTests {

    private InMemoryUserSessionStore inMemoryUserSessionStore;
    private UserSessionService userSessionService;

    @BeforeEach
    void setUp() {
        inMemoryUserSessionStore = new InMemoryUserSessionStore();
        userSessionService = new UserSessionService(inMemoryUserSessionStore);
    }

    @Test
    void transitionToClearsAttributesAndUpdatesLastCommand() {
        UserSession existingSession = UserSession.builder()
            .userId(100L)
            .chatId(200L)
            .state(UserState.SEARCHING)
            .lastCommand("/search")
            .attributes(new LinkedHashMap<>(Map.of("searchQuery", "milk")))
            .updatedAtEpochMillis(1L)
            .build();
        inMemoryUserSessionStore.save(existingSession);

        UserSession updatedSession = userSessionService.transitionTo(existingSession, 200L, UserState.MAIN_MENU, "/start");

        assertThat(updatedSession.getState()).isEqualTo(UserState.MAIN_MENU);
        assertThat(updatedSession.getLastCommand()).isEqualTo("/start");
        assertThat(updatedSession.getAttributes()).isEmpty();
        assertThat(updatedSession.getUpdatedAtEpochMillis()).isGreaterThan(1L);
    }

    @Test
    void rememberInputPreservesStateAndStoresAttribute() {
        UserSession initialSession = userSessionService.getOrCreate(100L, 200L);
        UserSession searchingSession = userSessionService.transitionTo(initialSession, 200L, UserState.SEARCHING, "/search");

        UserSession updatedSession = userSessionService.rememberInput(searchingSession, 200L, "searchQuery", "tea");

        assertThat(updatedSession.getState()).isEqualTo(UserState.SEARCHING);
        assertThat(updatedSession.getAttributes()).containsEntry("searchQuery", "tea");
    }

    private static final class InMemoryUserSessionStore implements UserSessionStore {

        private final Map<Long, UserSession> storage = new ConcurrentHashMap<>();

        @Override
        public Optional<UserSession> findByUserId(Long userId) {
            return Optional.ofNullable(storage.get(userId));
        }

        @Override
        public UserSession save(UserSession userSession) {
            storage.put(userSession.getUserId(), userSession);
            return userSession;
        }

        @Override
        public void deleteByUserId(Long userId) {
            storage.remove(userId);
        }
    }
}
