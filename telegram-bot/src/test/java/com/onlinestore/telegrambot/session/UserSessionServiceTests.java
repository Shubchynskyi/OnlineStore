package com.onlinestore.telegrambot.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.onlinestore.telegrambot.config.BotProperties;
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
        BotProperties botProperties = new BotProperties();
        botProperties.setToken("test-token");
        userSessionService = new UserSessionService(inMemoryUserSessionStore, botProperties);
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
    void transitionToPreservesConfiguredCustomerTokenAttribute() {
        UserSession existingSession = UserSession.builder()
            .userId(100L)
            .chatId(200L)
            .state(UserState.VIEWING_CART)
            .lastCommand("/cart")
            .attributes(new LinkedHashMap<>(Map.of(
                "backendAccessToken", "customer-token",
                "searchQuery", "milk"
            )))
            .updatedAtEpochMillis(1L)
            .build();
        inMemoryUserSessionStore.save(existingSession);

        UserSession updatedSession = userSessionService.transitionTo(existingSession, 200L, UserState.MAIN_MENU, "/start");

        assertThat(updatedSession.getAttributes())
            .containsEntry("backendAccessToken", "customer-token")
            .doesNotContainKey("searchQuery");
    }

    @Test
    void transitionToPreservesPendingSubmissionGuards() {
        UserSession existingSession = UserSession.builder()
            .userId(100L)
            .chatId(200L)
            .state(UserState.CONFIRMING_ORDER)
            .lastCommand("checkout:confirm")
            .attributes(new LinkedHashMap<>(Map.of(
                "checkoutOrderSubmissionState", "pending",
                "checkoutOrderSubmissionKey", "cart-snapshot|7",
                "checkoutAddressSubmissionFingerprint", "US|New York|Main Street|10001",
                "searchQuery", "milk"
            )))
            .updatedAtEpochMillis(1L)
            .build();
        inMemoryUserSessionStore.save(existingSession);

        UserSession updatedSession = userSessionService.transitionTo(existingSession, 200L, UserState.VIEWING_CART, "/cart");

        assertThat(updatedSession.getAttributes())
            .containsEntry("checkoutOrderSubmissionState", "pending")
            .containsEntry("checkoutOrderSubmissionKey", "cart-snapshot|7")
            .containsEntry("checkoutAddressSubmissionFingerprint", "US|New York|Main Street|10001")
            .doesNotContainKey("searchQuery");
    }

    @Test
    void transitionToPreservesAssistantConversationState() {
        UserSession existingSession = UserSession.builder()
            .userId(100L)
            .chatId(200L)
            .state(UserState.CHATTING_WITH_AI)
            .lastCommand("/assistant")
            .attributes(new LinkedHashMap<>(Map.of(
                "assistantConversationHistory", "[{\"role\":\"user\",\"content\":\"tea\"}]",
                "assistantTotalTokens", "180",
                "assistantPromptTokens", "120",
                "assistantCompletionTokens", "60",
                "searchQuery", "milk"
            )))
            .updatedAtEpochMillis(1L)
            .build();
        inMemoryUserSessionStore.save(existingSession);

        UserSession updatedSession = userSessionService.transitionTo(existingSession, 200L, UserState.MAIN_MENU, "/start");

        assertThat(updatedSession.getAttributes())
            .containsEntry("assistantConversationHistory", "[{\"role\":\"user\",\"content\":\"tea\"}]")
            .containsEntry("assistantTotalTokens", "180")
            .containsEntry("assistantPromptTokens", "120")
            .containsEntry("assistantCompletionTokens", "60")
            .doesNotContainKey("searchQuery");
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
