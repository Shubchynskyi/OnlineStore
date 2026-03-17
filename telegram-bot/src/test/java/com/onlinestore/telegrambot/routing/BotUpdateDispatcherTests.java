package com.onlinestore.telegrambot.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.integration.BackendApiException;
import com.onlinestore.telegrambot.integration.client.AddressApiClient;
import com.onlinestore.telegrambot.integration.client.CartApiClient;
import com.onlinestore.telegrambot.integration.client.CatalogApiClient;
import com.onlinestore.telegrambot.integration.client.OrdersApiClient;
import com.onlinestore.telegrambot.integration.client.SearchApiClient;
import com.onlinestore.telegrambot.integration.dto.PageResponse;
import com.onlinestore.telegrambot.integration.service.AiAssistantService;
import com.onlinestore.telegrambot.integration.service.ManagerOrdersIntegrationService;
import com.onlinestore.telegrambot.integration.dto.address.AddressDto;
import com.onlinestore.telegrambot.integration.dto.cart.CartDto;
import com.onlinestore.telegrambot.integration.dto.catalog.CategoryDto;
import com.onlinestore.telegrambot.integration.dto.catalog.CategoryWithProductsDto;
import com.onlinestore.telegrambot.integration.dto.catalog.ProductDto;
import com.onlinestore.telegrambot.integration.dto.catalog.ProductFilter;
import com.onlinestore.telegrambot.integration.dto.catalog.VariantDto;
import com.onlinestore.telegrambot.integration.service.AddressIntegrationService;
import com.onlinestore.telegrambot.integration.service.CartIntegrationService;
import com.onlinestore.telegrambot.integration.service.CatalogIntegrationService;
import com.onlinestore.telegrambot.integration.service.CustomerAccessTokenResolver;
import com.onlinestore.telegrambot.integration.service.OrdersIntegrationService;
import com.onlinestore.telegrambot.integration.service.SessionCustomerAccessTokenResolver;
import com.onlinestore.telegrambot.integration.service.SearchIntegrationService;
import com.onlinestore.telegrambot.notifications.ManagerActionHandler;
import com.onlinestore.telegrambot.session.UserSession;
import com.onlinestore.telegrambot.session.UserSessionService;
import com.onlinestore.telegrambot.session.UserSessionStore;
import com.onlinestore.telegrambot.session.UserState;
import com.onlinestore.telegrambot.support.PendingWriteGuardService;
import com.onlinestore.telegrambot.support.TelegramApiExecutor;
import com.onlinestore.telegrambot.support.TelegramMessageFactory;
import com.onlinestore.telegrambot.support.UserInteractionLockService;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

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
    private AddressApiClient addressApiClient;

    @Mock
    private CustomerAccessTokenResolver customerAccessTokenResolver;
    @Mock
    private ManagerOrdersIntegrationService managerOrdersIntegrationService;
    @Mock
    private TelegramApiExecutor telegramApiExecutor;

    private InMemoryUserSessionStore inMemoryUserSessionStore;
    private UserSessionService userSessionService;
    private BotUpdateDispatcher botUpdateDispatcher;

    @BeforeEach
    void setUp() {
        inMemoryUserSessionStore = new InMemoryUserSessionStore();
        BotProperties botProperties = createBotProperties();
        userSessionService = new UserSessionService(inMemoryUserSessionStore, botProperties);

        botUpdateDispatcher = createDispatcher(
            userSessionService,
            inMemoryUserSessionStore,
            customerAccessTokenResolver,
            botProperties
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
        assertThat(((EditMessageText) response).getText()).contains("Catalog categories").contains("Tea");
        assertThat(inMemoryUserSessionStore.findByUserId(10L).orElseThrow().getState()).isEqualTo(UserState.BROWSING_CATALOG);
    }

    @Test
    void assistantRouteCallbackOpensAssistantPrompt() {
        BotApiMethod<?> response = botUpdateDispatcher.dispatch(callbackUpdate(10L, 20L, 7, "cb-assistant", "route:assistant"));

        assertThat(response).isInstanceOf(EditMessageText.class);
        assertThat(((EditMessageText) response).getText()).contains("Assistant mode is active");
        assertThat(inMemoryUserSessionStore.findByUserId(10L).orElseThrow().getState()).isEqualTo(UserState.CHATTING_WITH_AI);
    }

    @Test
    void textInputStoresStateSpecificAttributeAndReturnsSearchResults() {
        when(catalogApiClient.getProducts(any(ProductFilter.class), eq(0), eq(5))).thenReturn(new PageResponse<>(
            List.of(product(101L, "Green Tea", "green-tea", "Loose leaf tea", "Tea", "tea", new BigDecimal("4.50"))),
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
        assertThat(((SendMessage) response).getText()).contains("Search results for").contains("Green Tea");
        assertThat(inMemoryUserSessionStore.findByUserId(10L).orElseThrow().getAttributes())
            .containsEntry("searchQuery", "green tea");
    }

    @Test
    void catalogCategoryCallbackShowsInlineProductCards() {
        when(catalogApiClient.getCategories()).thenReturn(List.of(
            new CategoryDto(1L, "Tea", "tea", "Tea products"),
            new CategoryDto(2L, "Coffee", "coffee", "Coffee products")
        ));
        when(catalogApiClient.getCategoryBySlug("tea", 0, 3)).thenReturn(new CategoryWithProductsDto(
            new CategoryDto(1L, "Tea", "tea", "Tea products"),
            new PageResponse<>(
                List.of(product(101L, "Green Tea", "green-tea", "Loose leaf tea", "Tea", "tea", new BigDecimal("4.50"))),
                0,
                3,
                1,
                1,
                true
            )
        ));

        botUpdateDispatcher.dispatch(callbackUpdate(10L, 20L, 7, "cb-1", "route:catalog"));
        BotApiMethod<?> response = botUpdateDispatcher.dispatch(callbackUpdate(10L, 20L, 7, "cb-2", "catalog:category:tea:0"));

        assertThat(response).isInstanceOf(EditMessageText.class);
        assertThat(((EditMessageText) response).getText()).contains("Tea products").contains("Green Tea");
        assertThat(inMemoryUserSessionStore.findByUserId(10L).orElseThrow().getAttributes())
            .containsEntry("catalogCategorySlug", "tea");
    }

    @Test
    void searchResultDetailCallbackUsesBackNavigationKeyboard() {
        when(catalogApiClient.getProducts(any(ProductFilter.class), eq(0), eq(5))).thenReturn(new PageResponse<>(
            List.of(product(101L, "Green Tea", "green-tea", "Loose leaf tea", "Tea", "tea", new BigDecimal("4.50"))),
            0,
            5,
            1,
            1,
            true
        ));
        when(catalogApiClient.getProductBySlug("green-tea")).thenReturn(
            product(101L, "Green Tea", "green-tea", "Loose leaf tea", "Tea", "tea", new BigDecimal("4.50"))
        );

        UserSession initialSession = userSessionService.getOrCreate(10L, 20L);
        userSessionService.transitionTo(initialSession, 20L, UserState.SEARCHING, "/search");
        botUpdateDispatcher.dispatch(textUpdate(10L, 20L, 2, "green tea"));
        BotApiMethod<?> response = botUpdateDispatcher.dispatch(callbackUpdate(10L, 20L, 2, "cb-3", "search:product:green-tea"));

        assertThat(response).isInstanceOf(EditMessageText.class);
        EditMessageText editMessageText = (EditMessageText) response;
        assertThat(editMessageText.getText()).contains("Search result details").contains("Green Tea");
        assertThat(firstCallbackData(editMessageText)).isEqualTo("search:back");
    }

    @Test
    void protectedCartCommandPreservesLinkedCustomerTokenAcrossTransition() {
        BotProperties botProperties = createBotProperties();
        InMemoryUserSessionStore localStore = new InMemoryUserSessionStore();
        UserSessionService localUserSessionService = new UserSessionService(localStore, botProperties);
        SessionCustomerAccessTokenResolver sessionCustomerAccessTokenResolver =
            new SessionCustomerAccessTokenResolver(localStore, botProperties);
        BotUpdateDispatcher localDispatcher = createDispatcher(
            localUserSessionService,
            localStore,
            sessionCustomerAccessTokenResolver,
            botProperties
        );

        UserSession initialSession = localUserSessionService.getOrCreate(10L, 20L);
        localUserSessionService.rememberInput(initialSession, 20L, "backendAccessToken", "customer-token");
        when(cartApiClient.getCart("customer-token")).thenReturn(new CartDto(new BigDecimal("12.50"), "USD", List.of()));

        BotApiMethod<?> response = localDispatcher.dispatch(textUpdate(10L, 20L, 3, "/cart"));

        assertThat(response).isInstanceOf(SendMessage.class);
        assertThat(((SendMessage) response).getText()).contains("Your cart").contains("currently empty");
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

    @Test
    void checkoutCallbackRoutesToAddressEntryFlow() {
        when(customerAccessTokenResolver.resolveAccessToken(10L)).thenReturn(Optional.of("customer-token"));
        when(cartApiClient.getCart("customer-token")).thenReturn(new CartDto(
            new BigDecimal("9.00"),
            "USD",
            List.of(new com.onlinestore.telegrambot.integration.dto.cart.CartItemDto(
                11L,
                500L,
                "Green Tea",
                "Default",
                "SKU-500",
                2,
                new BigDecimal("4.50"),
                "USD",
                new BigDecimal("9.00")
            ))
        ));
        when(addressApiClient.getAddresses("customer-token")).thenReturn(List.of());

        botUpdateDispatcher.dispatch(textUpdate(10L, 20L, 3, "/cart"));
        BotApiMethod<?> response = botUpdateDispatcher.dispatch(callbackUpdate(10L, 20L, 3, "cb-4", "checkout:start"));

        assertThat(response).isInstanceOf(EditMessageText.class);
        assertThat(((EditMessageText) response).getText()).contains("country code");
        assertThat(inMemoryUserSessionStore.findByUserId(10L).orElseThrow().getState()).isEqualTo(UserState.ENTERING_ADDRESS);
    }

    @Test
    void managerCallbackRoutesThroughHandler() {
        when(managerOrdersIntegrationService.confirmOrder(55L, "Accepted from Telegram by manager 10")).thenReturn(new com.onlinestore.telegrambot.integration.dto.orders.OrderDto(
            55L,
            99L,
            "PROCESSING",
            new BigDecimal("19.99"),
            "USD",
            List.of(),
            java.time.Instant.parse("2026-03-17T18:00:00Z")
        ));

        BotApiMethod<?> response = botUpdateDispatcher.dispatch(callbackUpdate(10L, 20L, 9, "cb-manager", "manager:order:accept:55"));

        assertThat(response).isInstanceOf(AnswerCallbackQuery.class);
        assertThat(((AnswerCallbackQuery) response).getText()).isEqualTo("Order accepted.");
        verify(telegramApiExecutor).execute(any(SendMessage.class));
    }

    private BotUpdateDispatcher createDispatcher(
        UserSessionService sessionService,
        UserSessionStore sessionStore,
        CustomerAccessTokenResolver accessTokenResolver,
        BotProperties botProperties
    ) {
        UserStateMachine userStateMachine = new UserStateMachine();
        TelegramMessageFactory telegramMessageFactory = new TelegramMessageFactory();

        CatalogIntegrationService catalogIntegrationService = new CatalogIntegrationService(catalogApiClient);
        SearchIntegrationService searchIntegrationService = new SearchIntegrationService(searchApiClient, botProperties);
        CartIntegrationService cartIntegrationService = new CartIntegrationService(cartApiClient, accessTokenResolver);
        AddressIntegrationService addressIntegrationService =
            new AddressIntegrationService(addressApiClient, accessTokenResolver);
        OrdersIntegrationService ordersIntegrationService =
            new OrdersIntegrationService(ordersApiClient, accessTokenResolver, botProperties);
        CatalogBrowserService catalogBrowserService = new CatalogBrowserService(
            catalogIntegrationService,
            sessionService,
            telegramMessageFactory,
            botProperties
        );
        SearchFlowService searchFlowService = new SearchFlowService(
            catalogIntegrationService,
            searchIntegrationService,
            sessionService,
            telegramMessageFactory,
            botProperties
        );
        CartFlowService cartFlowService = new CartFlowService(
            cartIntegrationService,
            sessionService,
            telegramMessageFactory
        );
        CheckoutFlowService checkoutFlowService = new CheckoutFlowService(
            cartIntegrationService,
            addressIntegrationService,
            ordersIntegrationService,
            sessionService,
            telegramMessageFactory,
            new PendingWriteGuardService()
        );
        MainMenuRouteResponseService mainMenuRouteResponseService =
            new MainMenuRouteResponseService(
                catalogIntegrationService,
                cartIntegrationService,
                ordersIntegrationService,
                botProperties
            );
        AiAssistantService aiAssistantService = mock(AiAssistantService.class);
        lenient().when(aiAssistantService.answer(any(UserSession.class), any(String.class))).thenReturn(
            new AiAssistantService.AiAssistantReply(
                "AI assistant response",
                Map.of("assistantConversationHistory", "[]")
            )
        );
        AiAssistantFlowService aiAssistantFlowService = new AiAssistantFlowService(
            aiAssistantService,
            sessionService,
            telegramMessageFactory,
            botProperties
        );

        return new BotUpdateDispatcher(
            sessionService,
            new CoreCommandRouter(
                sessionService,
                telegramMessageFactory,
                mainMenuRouteResponseService,
                catalogBrowserService,
                searchFlowService,
                cartFlowService,
                aiAssistantFlowService
            ),
            new CallbackQueryRouter(
                userStateMachine,
                sessionService,
                telegramMessageFactory,
                mainMenuRouteResponseService,
                catalogBrowserService,
                searchFlowService,
                cartFlowService,
                checkoutFlowService,
                aiAssistantFlowService,
                new ManagerActionHandler(
                    botProperties,
                    managerOrdersIntegrationService,
                    telegramMessageFactory,
                    telegramApiExecutor
                )
            ),
            new TextMessageRouter(
                userStateMachine,
                sessionService,
                telegramMessageFactory,
                ordersIntegrationService,
                searchFlowService,
                checkoutFlowService,
                aiAssistantFlowService
            ),
            telegramMessageFactory,
            createUserInteractionLockService(botProperties)
        );
    }

    @SuppressWarnings("unchecked")
    private UserInteractionLockService createUserInteractionLockService(BotProperties botProperties) {
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.setIfAbsent(any(), any(), any())).thenReturn(true);
        lenient().doReturn(1L).when(stringRedisTemplate).execute(any(), any(), any());
        return new UserInteractionLockService(stringRedisTemplate, botProperties);
    }

    private BotProperties createBotProperties() {
        BotProperties botProperties = new BotProperties();
        botProperties.setToken("test-token");
        botProperties.getAiAssistant().setEnabled(true);
        botProperties.getBackendApi().setCatalogPageSize(3);
        botProperties.getBackendApi().setSearchPageSize(5);
        botProperties.getBackendApi().setRecentOrdersPageSize(3);
        botProperties.getManagerNotifications().setChatId("20");
        botProperties.getManagerNotifications().setUserId("10");
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

    private ProductDto product(
        Long id,
        String name,
        String slug,
        String description,
        String categoryName,
        String categorySlug,
        BigDecimal price
    ) {
        return new ProductDto(
            id,
            name,
            slug,
            description,
            1L,
            categoryName,
            categorySlug,
            "ACTIVE",
            false,
            List.of(new VariantDto(1L, "sku-" + id, name + " default", price, "USD", null, 5, Map.of(), true)),
            List.of(),
            List.of()
        );
    }

    private String firstCallbackData(EditMessageText editMessageText) {
        InlineKeyboardMarkup keyboard = (InlineKeyboardMarkup) editMessageText.getReplyMarkup();
        return keyboard.getKeyboard().get(0).get(0).getCallbackData();
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
