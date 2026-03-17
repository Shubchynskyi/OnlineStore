package com.onlinestore.telegrambot.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.integration.service.AiAssistantService;
import com.onlinestore.telegrambot.session.UserSession;
import com.onlinestore.telegrambot.session.UserSessionService;
import com.onlinestore.telegrambot.session.UserSessionStore;
import com.onlinestore.telegrambot.session.UserState;
import com.onlinestore.telegrambot.support.InteractionThrottlingService;
import com.onlinestore.telegrambot.support.SecurityAuditService;
import com.onlinestore.telegrambot.support.TelegramMessageFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@ExtendWith(MockitoExtension.class)
class AiAssistantFlowServiceTests {

    @Mock
    private AiAssistantService aiAssistantService;
    @Mock
    private SecurityAuditService securityAuditService;

    private InMemoryUserSessionStore inMemoryUserSessionStore;
    private UserSessionService userSessionService;
    private AiAssistantFlowService aiAssistantFlowService;
    private BotProperties botProperties;
    private InteractionThrottlingService interactionThrottlingService;

    @BeforeEach
    void setUp() {
        inMemoryUserSessionStore = new InMemoryUserSessionStore();

        botProperties = new BotProperties();
        botProperties.setToken("test-token");
        botProperties.getAiAssistant().setEnabled(true);
        interactionThrottlingService = new InteractionThrottlingService(botProperties, null);

        userSessionService = new UserSessionService(inMemoryUserSessionStore, botProperties);
        aiAssistantFlowService = new AiAssistantFlowService(
            aiAssistantService,
            userSessionService,
            new TelegramMessageFactory(),
            botProperties,
            interactionThrottlingService,
            securityAuditService
        );
        LinkedHashMap<String, String> clearedConversationAttributes = new LinkedHashMap<>();
        clearedConversationAttributes.put("assistantConversationHistory", null);
        clearedConversationAttributes.put("assistantTotalTokens", null);
        clearedConversationAttributes.put("assistantPromptTokens", null);
        clearedConversationAttributes.put("assistantCompletionTokens", null);
        lenient().when(aiAssistantService.clearConversationAttributes()).thenReturn(clearedConversationAttributes);
    }

    @Test
    void openPromptTransitionsSessionToAssistantState() {
        UserSession userSession = userSessionService.getOrCreate(10L, 20L);

        BotApiMethod<?> response = aiAssistantFlowService.openPrompt(
            updateContext(textUpdate(10L, 20L, 1, "/assistant")),
            userSession,
            "/assistant"
        );

        assertThat(response).isInstanceOf(SendMessage.class);
        assertThat(((SendMessage) response).getText()).contains("Assistant mode is active");
        assertThat(inMemoryUserSessionStore.findByUserId(10L).orElseThrow().getState()).isEqualTo(UserState.CHATTING_WITH_AI);
    }

    @Test
    void handleAssistantInputPersistsReplyAttributes() {
        when(aiAssistantService.answer(any(UserSession.class), eq("recommend tea"))).thenReturn(
            new AiAssistantService.AiAssistantReply(
                "Try Green Tea for a balanced everyday option.",
                Map.of(
                    "assistantConversationHistory", "[{\"role\":\"user\",\"content\":\"recommend tea\"}]",
                    "assistantTotalTokens", "50"
                )
            )
        );

        UserSession initialSession = userSessionService.getOrCreate(10L, 20L);
        UserSession assistantSession = userSessionService.transitionTo(initialSession, 20L, UserState.CHATTING_WITH_AI, "/assistant");

        BotApiMethod<?> response = aiAssistantFlowService.handleAssistantInput(
            updateContext(textUpdate(10L, 20L, 2, "recommend tea")),
            assistantSession
        );

        assertThat(response).isInstanceOf(SendMessage.class);
        assertThat(((SendMessage) response).getText()).contains("Green Tea");
        assertThat(inMemoryUserSessionStore.findByUserId(10L).orElseThrow().getAttributes())
            .containsEntry("assistantTotalTokens", "50")
            .containsKey("assistantConversationHistory");
    }

    @Test
    void clearCallbackRemovesAssistantConversationAttributes() {
        UserSession initialSession = userSessionService.getOrCreate(10L, 20L);
        UserSession assistantSession = userSessionService.transitionTo(initialSession, 20L, UserState.CHATTING_WITH_AI, "/assistant");
        UserSession enrichedSession = userSessionService.rememberInputs(assistantSession, 20L, Map.of(
            "assistantConversationHistory", "[{\"role\":\"user\",\"content\":\"hello\"}]",
            "assistantTotalTokens", "90",
            "assistantPromptTokens", "60",
            "assistantCompletionTokens", "30"
        ));

        BotApiMethod<?> response = aiAssistantFlowService.handleCallback(
            updateContext(callbackUpdate(10L, 20L, 5, "cb-assistant", "assistant:clear")),
            enrichedSession
        );

        assertThat(response).isInstanceOf(EditMessageText.class);
        assertThat(((EditMessageText) response).getText()).contains("Conversation cleared");
        assertThat(inMemoryUserSessionStore.findByUserId(10L).orElseThrow().getAttributes())
            .doesNotContainKeys(
                "assistantConversationHistory",
                "assistantTotalTokens",
                "assistantPromptTokens",
                "assistantCompletionTokens"
            );
    }

    @Test
    void assistantInputIsRateLimitedBeforeRepeatedAiCalls() {
        botProperties.getProtection().getRateLimit().getAiRequests().setMaxEvents(1);
        when(aiAssistantService.answer(any(UserSession.class), eq("recommend tea"))).thenReturn(
            new AiAssistantService.AiAssistantReply("Try Green Tea.", Map.of())
        );

        UserSession initialSession = userSessionService.getOrCreate(10L, 20L);
        UserSession assistantSession = userSessionService.transitionTo(initialSession, 20L, UserState.CHATTING_WITH_AI, "/assistant");

        aiAssistantFlowService.handleAssistantInput(updateContext(textUpdate(10L, 20L, 2, "recommend tea")), assistantSession);
        BotApiMethod<?> throttledResponse = aiAssistantFlowService.handleAssistantInput(
            updateContext(textUpdate(10L, 20L, 3, "recommend tea")),
            assistantSession
        );

        assertThat(throttledResponse).isInstanceOf(SendMessage.class);
        assertThat(((SendMessage) throttledResponse).getText()).contains("too quickly");
        verify(aiAssistantService, times(1)).answer(any(UserSession.class), eq("recommend tea"));
    }

    @Test
    void assistantInputReturnsReplyButWarnsWhenConversationStateCannotBeSaved() {
        when(aiAssistantService.answer(any(UserSession.class), eq("recommend tea"))).thenReturn(
            new AiAssistantService.AiAssistantReply(
                "Try Green Tea for a balanced everyday option.",
                Map.of("assistantConversationHistory", "[{\"role\":\"assistant\",\"content\":\"Try Green Tea\"}]")
            )
        );

        FailingUserSessionStore failingUserSessionStore = new FailingUserSessionStore();
        UserSessionService failingSessionService = new UserSessionService(failingUserSessionStore, botProperties);
        AiAssistantFlowService failingFlowService = new AiAssistantFlowService(
            aiAssistantService,
            failingSessionService,
            new TelegramMessageFactory(),
            botProperties,
            interactionThrottlingService,
            securityAuditService
        );

        UserSession initialSession = failingSessionService.getOrCreate(10L, 20L);
        UserSession assistantSession = failingSessionService.transitionTo(initialSession, 20L, UserState.CHATTING_WITH_AI, "/assistant");
        failingUserSessionStore.failOnNextSave();

        BotApiMethod<?> response = failingFlowService.handleAssistantInput(
            updateContext(textUpdate(10L, 20L, 4, "recommend tea")),
            assistantSession
        );

        assertThat(response).isInstanceOf(SendMessage.class);
        assertThat(((SendMessage) response).getText())
            .contains("Try Green Tea")
            .contains("conversation memory could not be saved");
        assertThat(failingUserSessionStore.findByUserId(10L).orElseThrow().getAttributes())
            .doesNotContainKey("assistantConversationHistory");
    }

    private BotUpdateContext updateContext(Update update) {
        return BotUpdateContext.from(update).orElseThrow();
    }

    private Update textUpdate(Long userId, Long chatId, Integer messageId, String text) {
        Update update = new Update();
        Message message = new Message();
        message.setMessageId(messageId);
        message.setText(text);
        message.setChat(Chat.builder().id(chatId).type("private").build());
        message.setFrom(User.builder().id(userId).firstName("Tester").isBot(false).build());
        update.setMessage(message);
        return update;
    }

    private Update callbackUpdate(Long userId, Long chatId, Integer messageId, String callbackId, String callbackData) {
        Update update = new Update();
        CallbackQuery callbackQuery = new CallbackQuery();
        callbackQuery.setId(callbackId);
        callbackQuery.setData(callbackData);
        callbackQuery.setFrom(User.builder().id(userId).firstName("Tester").isBot(false).build());

        Message message = new Message();
        message.setMessageId(messageId);
        message.setChat(Chat.builder().id(chatId).type("private").build());
        callbackQuery.setMessage(message);

        update.setCallbackQuery(callbackQuery);
        return update;
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

    private static final class FailingUserSessionStore implements UserSessionStore {

        private final Map<Long, UserSession> storage = new ConcurrentHashMap<>();
        private volatile boolean failOnNextSave;

        private void failOnNextSave() {
            this.failOnNextSave = true;
        }

        @Override
        public Optional<UserSession> findByUserId(Long userId) {
            return Optional.ofNullable(storage.get(userId));
        }

        @Override
        public UserSession save(UserSession userSession) {
            if (failOnNextSave) {
                failOnNextSave = false;
                throw new IllegalStateException("Redis save failed");
            }
            storage.put(userSession.getUserId(), userSession);
            return userSession;
        }

        @Override
        public void deleteByUserId(Long userId) {
            storage.remove(userId);
        }
    }
}
