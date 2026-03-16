package com.onlinestore.telegrambot.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.onlinestore.telegrambot.session.UserSession;
import com.onlinestore.telegrambot.session.UserSessionService;
import com.onlinestore.telegrambot.session.UserSessionStore;
import com.onlinestore.telegrambot.session.UserState;
import com.onlinestore.telegrambot.support.TelegramMessageFactory;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;

class BotUpdateDispatcherTests {

    private InMemoryUserSessionStore inMemoryUserSessionStore;
    private UserSessionService userSessionService;
    private BotUpdateDispatcher botUpdateDispatcher;

    @BeforeEach
    void setUp() {
        inMemoryUserSessionStore = new InMemoryUserSessionStore();
        userSessionService = new UserSessionService(inMemoryUserSessionStore);

        UserStateMachine userStateMachine = new UserStateMachine();
        TelegramMessageFactory telegramMessageFactory = new TelegramMessageFactory();

        botUpdateDispatcher = new BotUpdateDispatcher(
            userSessionService,
            new CoreCommandRouter(userSessionService, telegramMessageFactory),
            new CallbackQueryRouter(userStateMachine, userSessionService, telegramMessageFactory),
            new TextMessageRouter(userStateMachine, userSessionService, telegramMessageFactory),
            telegramMessageFactory
        );
    }

    @Test
    void commandRoutingMovesSessionToSearching() {
        BotApiMethod<?> response = botUpdateDispatcher.dispatch(textUpdate(10L, 20L, 1, "/search"));

        assertThat(response).isInstanceOf(SendMessage.class);
        assertThat(((SendMessage) response).getText()).contains("Search mode is active");
        assertThat(inMemoryUserSessionStore.findByUserId(10L).orElseThrow().getState()).isEqualTo(UserState.SEARCHING);
    }

    @Test
    void callbackRoutingEditsMessageAndUpdatesSessionState() {
        BotApiMethod<?> response = botUpdateDispatcher.dispatch(callbackUpdate(10L, 20L, 7, "cb-1", "route:catalog"));

        assertThat(response).isInstanceOf(EditMessageText.class);
        assertThat(((EditMessageText) response).getText()).contains("Catalog routing is active");
        assertThat(inMemoryUserSessionStore.findByUserId(10L).orElseThrow().getState()).isEqualTo(UserState.BROWSING_CATALOG);
    }

    @Test
    void textInputStoresStateSpecificAttribute() {
        UserSession initialSession = userSessionService.getOrCreate(10L, 20L);
        userSessionService.transitionTo(initialSession, 20L, UserState.SEARCHING, "/search");

        BotApiMethod<?> response = botUpdateDispatcher.dispatch(textUpdate(10L, 20L, 2, "green tea"));

        assertThat(response).isInstanceOf(SendMessage.class);
        assertThat(((SendMessage) response).getText()).contains("Search query saved");
        assertThat(inMemoryUserSessionStore.findByUserId(10L).orElseThrow().getAttributes())
            .containsEntry("searchQuery", "green tea");
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
}
