package com.onlinestore.telegrambot.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.integration.BackendApiException;
import com.onlinestore.telegrambot.integration.client.CartApiClient;
import com.onlinestore.telegrambot.integration.client.CatalogApiClient;
import com.onlinestore.telegrambot.integration.client.OrdersApiClient;
import com.onlinestore.telegrambot.integration.client.SearchApiClient;
import com.onlinestore.telegrambot.integration.dto.PageResponse;
import com.onlinestore.telegrambot.integration.dto.cart.CartDto;
import com.onlinestore.telegrambot.integration.dto.catalog.CategoryDto;
import com.onlinestore.telegrambot.integration.dto.search.ProductSearchResult;
import com.onlinestore.telegrambot.integration.service.CartIntegrationService;
import com.onlinestore.telegrambot.integration.service.CatalogIntegrationService;
import com.onlinestore.telegrambot.integration.service.CustomerAccessTokenResolver;
import com.onlinestore.telegrambot.integration.service.OrdersIntegrationService;
import com.onlinestore.telegrambot.integration.service.SessionCustomerAccessTokenResolver;
import com.onlinestore.telegrambot.integration.service.SearchIntegrationService;
import com.onlinestore.telegrambot.session.UserSession;
import com.onlinestore.telegrambot.session.UserSessionService;
import com.onlinestore.telegrambot.session.UserSessionStore;
import com.onlinestore.telegrambot.session.UserState;
import com.onlinestore.telegrambot.support.TelegramMessageFactory;
import java.math.BigDecimal;
import java.util.List;
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
class BotUpdateDispatcherTests {

    @Mock
    private CatalogApiClient catalogApiClient;

    @Mock
    private SearchApiClient searchApiClient;

    @Mock
    private CartApiClient cartApiClient;

    @Mock
    private OrdersApiClient ordersApiClient;

    @Mock
    private CustomerAccessTokenResolver customerAccessTokenResolver;

    private InMemoryUserSessionStore inMemoryUserSessionStore;
    private UserSessionService userSessionService;
    private BotUpdateDispatcher botUpdateDispatcher;

    @BeforeEach
    void setUp() {
        inMemoryUserSessionStore = new InMemoryUserSessionStore();
        BotProperties botProperties = createBotProperties();
        userSessionService = new UserSessionService(inMemoryUserSessionStore, botProperties);

        UserStateMachine userStateMachine = new UserStateMachine();
        TelegramMessageFactory telegramMessageFactory = new TelegramMessageFactory();

        CatalogIntegrationService catalogIntegrationService = new CatalogIntegrationService(catalogApiClient);
        SearchIntegrationService searchIntegrationService = new SearchIntegrationService(searchApiClient, botProperties);
        CartIntegrationService cartIntegrationService = new CartIntegrationService(cartApiClient, customerAccessTokenResolver);
        OrdersIntegrationService ordersIntegrationService =
            new OrdersIntegrationService(ordersApiClient, customerAccessTokenResolver, botProperties);
        MainMenuRouteResponseService mainMenuRouteResponseService =
            new MainMenuRouteResponseService(
                catalogIntegrationService,
                cartIntegrationService,
                ordersIntegrationService,
                botProperties
            );

        botUpdateDispatcher = new BotUpdateDispatcher(
            userSessionService,
            new CoreCommandRouter(userSessionService, telegramMessageFactory, mainMenuRouteResponseService),
            new CallbackQueryRouter(
                userStateMachine,
                userSessionService,
                telegramMessageFactory,
                mainMenuRouteResponseService
            ),
            new TextMessageRouter(
                userStateMachine,
                userSessionService,
                telegramMessageFactory,
                searchIntegrationService,
                ordersIntegrationService
            ),
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
        when(catalogApiClient.getCategories()).thenReturn(List.of(
            new CategoryDto(1L, "Tea", "tea", "Tea products"),
            new CategoryDto(2L, "Coffee", "coffee", "Coffee products")
        ));

        BotApiMethod<?> response = botUpdateDispatcher.dispatch(callbackUpdate(10L, 20L, 7, "cb-1", "route:catalog"));

        assertThat(response).isInstanceOf(EditMessageText.class);
        assertThat(((EditMessageText) response).getText()).contains("Available categories").contains("Tea");
        assertThat(inMemoryUserSessionStore.findByUserId(10L).orElseThrow().getState()).isEqualTo(UserState.BROWSING_CATALOG);
    }

    @Test
    void textInputStoresStateSpecificAttributeAndReturnsSearchResults() {
        when(searchApiClient.search(any(), eq(0), eq(5))).thenReturn(new PageResponse<>(
            List.of(new ProductSearchResult(
                "sku-1",
                "Green Tea",
                "Loose leaf tea",
                "Tea",
                new BigDecimal("4.50"),
                new BigDecimal("6.00"),
                true,
                List.of(),
                1.0f
            )),
            0,
            5,
            1,
            1,
            true
        ));

        UserSession initialSession = userSessionService.getOrCreate(10L, 20L);
        userSessionService.transitionTo(initialSession, 20L, UserState.SEARCHING, "/search");

        BotApiMethod<?> response = botUpdateDispatcher.dispatch(textUpdate(10L, 20L, 2, "green tea"));

        assertThat(response).isInstanceOf(SendMessage.class);
        assertThat(((SendMessage) response).getText()).contains("Top results").contains("Green Tea");
        assertThat(inMemoryUserSessionStore.findByUserId(10L).orElseThrow().getAttributes())
            .containsEntry("searchQuery", "green tea");
    }

    @Test
    void protectedCartCommandPreservesLinkedCustomerTokenAcrossTransition() {
        BotProperties botProperties = createBotProperties();
        InMemoryUserSessionStore localStore = new InMemoryUserSessionStore();
        UserSessionService localUserSessionService = new UserSessionService(localStore, botProperties);
        UserStateMachine userStateMachine = new UserStateMachine();
        TelegramMessageFactory telegramMessageFactory = new TelegramMessageFactory();

        CatalogIntegrationService catalogIntegrationService = new CatalogIntegrationService(catalogApiClient);
        SearchIntegrationService searchIntegrationService = new SearchIntegrationService(searchApiClient, botProperties);
        SessionCustomerAccessTokenResolver sessionCustomerAccessTokenResolver =
            new SessionCustomerAccessTokenResolver(localStore, botProperties);
        CartIntegrationService cartIntegrationService = new CartIntegrationService(cartApiClient, sessionCustomerAccessTokenResolver);
        OrdersIntegrationService ordersIntegrationService =
            new OrdersIntegrationService(ordersApiClient, sessionCustomerAccessTokenResolver, botProperties);
        MainMenuRouteResponseService mainMenuRouteResponseService =
            new MainMenuRouteResponseService(
                catalogIntegrationService,
                cartIntegrationService,
                ordersIntegrationService,
                botProperties
            );

        BotUpdateDispatcher localDispatcher = new BotUpdateDispatcher(
            localUserSessionService,
            new CoreCommandRouter(localUserSessionService, telegramMessageFactory, mainMenuRouteResponseService),
            new CallbackQueryRouter(
                userStateMachine,
                localUserSessionService,
                telegramMessageFactory,
                mainMenuRouteResponseService
            ),
            new TextMessageRouter(
                userStateMachine,
                localUserSessionService,
                telegramMessageFactory,
                searchIntegrationService,
                ordersIntegrationService
            ),
            telegramMessageFactory
        );

        UserSession initialSession = localUserSessionService.getOrCreate(10L, 20L);
        localUserSessionService.rememberInput(initialSession, 20L, "backendAccessToken", "customer-token");
        when(cartApiClient.getCart("customer-token")).thenReturn(new CartDto(new BigDecimal("12.50"), "USD", List.of()));

        BotApiMethod<?> response = localDispatcher.dispatch(textUpdate(10L, 20L, 3, "/cart"));

        assertThat(response).isInstanceOf(SendMessage.class);
        assertThat(((SendMessage) response).getText()).contains("Cart integration is active");
        assertThat(localStore.findByUserId(10L).orElseThrow().getAttributes())
            .containsEntry("backendAccessToken", "customer-token");
    }

    @Test
    void protectedCartCommandReturnsSanitizedBackendErrorMessage() {
        when(customerAccessTokenResolver.resolveAccessToken(10L)).thenReturn(Optional.of("customer-token"));
        when(cartApiClient.getCart("customer-token")).thenThrow(new BackendApiException(
            "cart.getCart",
            "The store service is temporarily unavailable. Please try again later.",
            503,
            "SERVICE_UNAVAILABLE",
            null,
            new RuntimeException("Downstream path /api/v1/cart should stay internal")
        ));

        BotApiMethod<?> response = botUpdateDispatcher.dispatch(textUpdate(10L, 20L, 3, "/cart"));

        assertThat(response).isInstanceOf(SendMessage.class);
        assertThat(((SendMessage) response).getText())
            .isEqualTo("The store service is temporarily unavailable. Please try again later.")
            .doesNotContain("/api/v1/cart");
    }

    private BotProperties createBotProperties() {
        BotProperties botProperties = new BotProperties();
        botProperties.setToken("test-token");
        botProperties.getBackendApi().setCatalogPageSize(3);
        botProperties.getBackendApi().setSearchPageSize(5);
        botProperties.getBackendApi().setRecentOrdersPageSize(3);
        return botProperties;
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
