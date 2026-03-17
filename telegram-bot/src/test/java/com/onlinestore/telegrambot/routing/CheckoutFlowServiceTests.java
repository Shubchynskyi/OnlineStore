package com.onlinestore.telegrambot.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.integration.BackendApiException;
import com.onlinestore.telegrambot.integration.dto.address.AddressDto;
import com.onlinestore.telegrambot.integration.dto.address.CreateAddressRequest;
import com.onlinestore.telegrambot.integration.dto.cart.CartDto;
import com.onlinestore.telegrambot.integration.dto.cart.CartItemDto;
import com.onlinestore.telegrambot.integration.dto.orders.CreateOrderRequest;
import com.onlinestore.telegrambot.integration.dto.orders.OrderDto;
import com.onlinestore.telegrambot.integration.dto.orders.OrderItemRequest;
import com.onlinestore.telegrambot.integration.service.AddressIntegrationService;
import com.onlinestore.telegrambot.integration.service.CartIntegrationService;
import com.onlinestore.telegrambot.integration.service.OrdersIntegrationService;
import com.onlinestore.telegrambot.session.UserSession;
import com.onlinestore.telegrambot.session.UserSessionService;
import com.onlinestore.telegrambot.session.UserSessionStore;
import com.onlinestore.telegrambot.session.UserState;
import com.onlinestore.telegrambot.support.PendingWriteGuardService;
import com.onlinestore.telegrambot.support.TelegramMessageFactory;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

@ExtendWith(MockitoExtension.class)
class CheckoutFlowServiceTests {

    @Mock
    private CartIntegrationService cartIntegrationService;

    @Mock
    private AddressIntegrationService addressIntegrationService;

    @Mock
    private OrdersIntegrationService ordersIntegrationService;

    private InMemoryUserSessionStore userSessionStore;
    private UserSessionService userSessionService;
    private CheckoutFlowService checkoutFlowService;

    @BeforeEach
    void setUp() {
        userSessionStore = new InMemoryUserSessionStore();

        BotProperties botProperties = new BotProperties();
        botProperties.setToken("test-token");
        userSessionService = new UserSessionService(userSessionStore, botProperties);

        checkoutFlowService = new CheckoutFlowService(
            cartIntegrationService,
            addressIntegrationService,
            ordersIntegrationService,
            userSessionService,
            new TelegramMessageFactory(),
            new PendingWriteGuardService()
        );
    }

    @Test
    void startCheckoutWithoutSavedAddressesPromptsForCountryEntry() {
        when(cartIntegrationService.getCart(10L)).thenReturn(cartDto(
            new CartItemDto(11L, 500L, "Green Tea", "Default", "SKU-500", 2, new BigDecimal("4.50"), "USD", new BigDecimal("9.00"))
        ));
        when(addressIntegrationService.getAddresses(10L)).thenReturn(List.of());

        UserSession userSession = userSessionService.getOrCreate(10L, 20L);
        BotApiMethod<?> response = checkoutFlowService.startCheckout(
            context(callbackUpdate(10L, 20L, 7, "cb-1", "checkout:start")),
            userSession,
            "callback:checkout:start"
        );

        assertThat(response).isInstanceOf(EditMessageText.class);
        assertThat(((EditMessageText) response).getText()).contains("country code");
        UserSession persisted = userSessionStore.findByUserId(10L).orElseThrow();
        assertThat(persisted.getState()).isEqualTo(UserState.ENTERING_ADDRESS);
        assertThat(persisted.getAttributes()).containsEntry("checkoutAddressStep", "country");
        assertThat(persisted.getAttributes()).containsKey("checkoutCartSnapshot");
    }

    @Test
    void startCheckoutWithSavedAddressesShowsSelectionButtons() {
        when(cartIntegrationService.getCart(10L)).thenReturn(cartDto(
            new CartItemDto(11L, 500L, "Green Tea", "Default", "SKU-500", 2, new BigDecimal("4.50"), "USD", new BigDecimal("9.00"))
        ));
        when(addressIntegrationService.getAddresses(10L)).thenReturn(List.of(
            address(7L, "Home", "US", "New York", "Main Street", "10A", "5", "10001", true),
            address(8L, "Office", "US", "Brooklyn", "Second Street", null, null, "11201", false)
        ));

        UserSession userSession = userSessionService.getOrCreate(10L, 20L);
        BotApiMethod<?> response = checkoutFlowService.startCheckout(
            context(callbackUpdate(10L, 20L, 7, "cb-2", "checkout:start")),
            userSession,
            "callback:checkout:start"
        );

        assertThat(response).isInstanceOf(EditMessageText.class);
        EditMessageText editMessageText = (EditMessageText) response;
        assertThat(editMessageText.getText()).contains("Choose a shipping address").contains("Home").contains("Office");
        assertThat(allCallbackData(editMessageText)).contains("checkout:address:select:7", "checkout:address:new");
    }

    @Test
    void finalAddressInputCreatesAddressAndShowsConfirmation() {
        when(cartIntegrationService.getCart(10L)).thenReturn(cartDto(
            new CartItemDto(11L, 500L, "Green Tea", "Default", "SKU-500", 2, new BigDecimal("4.50"), "USD", new BigDecimal("9.00"))
        ));
        when(addressIntegrationService.createAddress(
            10L,
            new CreateAddressRequest(null, "US", "New York", "Main Street", "10A", null, "10001", false)
        )).thenReturn(address(7L, null, "US", "New York", "Main Street", "10A", null, "10001", false));

        UserSession session = userSessionService.getOrCreate(10L, 20L);
        UserSession addressSession = userSessionService.transitionTo(session, 20L, UserState.ENTERING_ADDRESS, "checkout:start");
        userSessionService.rememberInputs(addressSession, 20L, Map.of(
            "checkoutAddressStep", "postalCode",
            "checkoutCountry", "US",
            "checkoutCity", "New York",
            "checkoutStreet", "Main Street",
            "checkoutBuilding", "10A",
            "checkoutCartSnapshot", "snapshot"
        ));

        BotApiMethod<?> response = checkoutFlowService.handleAddressInput(
            context(textUpdate(10L, 20L, 8, "10001")),
            userSessionStore.findByUserId(10L).orElseThrow()
        );

        assertThat(response).isInstanceOf(SendMessage.class);
        assertThat(((SendMessage) response).getText()).contains("Review your order").contains("Main Street");
        UserSession persisted = userSessionStore.findByUserId(10L).orElseThrow();
        assertThat(persisted.getState()).isEqualTo(UserState.CONFIRMING_ORDER);
        assertThat(persisted.getAttributes()).containsEntry("checkoutSelectedAddressId", "7");
    }

    @Test
    void confirmCheckoutCreatesOrderWhenCartSnapshotMatches() {
        CartDto cart = cartDto(
            new CartItemDto(11L, 500L, "Green Tea", "Default", "SKU-500", 2, new BigDecimal("4.50"), "USD", new BigDecimal("9.00"))
        );
        String snapshot = "500:2:9.00|9.00|USD";
        when(cartIntegrationService.getCart(10L)).thenReturn(cart);
        when(addressIntegrationService.getAddresses(10L)).thenReturn(List.of(
            address(7L, "Home", "US", "New York", "Main Street", "10A", "5", "10001", true)
        ));
        when(ordersIntegrationService.createOrder(
            10L,
            new CreateOrderRequest(7L, List.of(new OrderItemRequest(500L, 2)), null)
        )).thenReturn(new OrderDto(
            15L,
            10L,
            "PENDING",
            new BigDecimal("9.00"),
            "USD",
            List.of(),
            Instant.parse("2026-03-17T12:00:00Z")
        ));

        UserSession session = userSessionService.getOrCreate(10L, 20L);
        UserSession confirmSession = userSessionService.transitionTo(session, 20L, UserState.CONFIRMING_ORDER, "checkout:confirm");
        userSessionService.rememberInputs(confirmSession, 20L, Map.of(
            "checkoutSelectedAddressId", "7",
            "checkoutCartSnapshot", snapshot
        ));

        BotApiMethod<?> response = checkoutFlowService.handleCallback(
            context(callbackUpdate(10L, 20L, 9, "cb-3", "checkout:confirm")),
            userSessionStore.findByUserId(10L).orElseThrow()
        );

        assertThat(response).isInstanceOf(EditMessageText.class);
        assertThat(((EditMessageText) response).getText()).contains("Order #15").contains("PENDING");
        UserSession persisted = userSessionStore.findByUserId(10L).orElseThrow();
        assertThat(persisted.getState()).isEqualTo(UserState.TRACKING_ORDER);
        assertThat(persisted.getAttributes())
            .doesNotContainKeys(
                "checkoutOrderSubmissionState",
                "checkoutOrderSubmissionKey",
                "checkoutOrderSubmissionStartedAt"
            );
    }

    @Test
    void confirmCheckoutRequestsReviewWhenCartSnapshotChanged() {
        when(cartIntegrationService.getCart(10L)).thenReturn(cartDto(
            new CartItemDto(11L, 500L, "Green Tea", "Default", "SKU-500", 1, new BigDecimal("4.50"), "USD", new BigDecimal("4.50"))
        ));
        when(addressIntegrationService.getAddresses(10L)).thenReturn(List.of(
            address(7L, "Home", "US", "New York", "Main Street", "10A", "5", "10001", true)
        ));

        UserSession session = userSessionService.getOrCreate(10L, 20L);
        UserSession confirmSession = userSessionService.transitionTo(session, 20L, UserState.CONFIRMING_ORDER, "checkout:confirm");
        userSessionService.rememberInputs(confirmSession, 20L, Map.of(
            "checkoutSelectedAddressId", "7",
            "checkoutCartSnapshot", "stale"
        ));

        BotApiMethod<?> response = checkoutFlowService.handleCallback(
            context(callbackUpdate(10L, 20L, 9, "cb-4", "checkout:confirm")),
            userSessionStore.findByUserId(10L).orElseThrow()
        );

        assertThat(response).isInstanceOf(EditMessageText.class);
        assertThat(((EditMessageText) response).getText())
            .contains("changed while you were checking out")
            .contains("Open cart");
        assertThat(allCallbackData((EditMessageText) response)).contains("route:cart");
    }

    @Test
    void confirmCheckoutDoesNotRecreateOrderAfterConfirmationStateIsClosed() {
        UserSession session = userSessionService.getOrCreate(10L, 20L);
        UserSession trackingSession = userSessionService.transitionTo(session, 20L, UserState.TRACKING_ORDER, "checkout:confirm");
        userSessionService.rememberInput(trackingSession, 20L, "orderReference", "15");

        BotApiMethod<?> response = checkoutFlowService.handleCallback(
            context(callbackUpdate(10L, 20L, 9, "cb-5", "checkout:confirm")),
            userSessionStore.findByUserId(10L).orElseThrow()
        );

        assertThat(response).isInstanceOf(EditMessageText.class);
        assertThat(((EditMessageText) response).getText()).contains("no longer active");
        verifyNoInteractions(ordersIntegrationService);
    }

    @Test
    void confirmCheckoutKeepsDurableSubmissionKeyWhenOrderOutcomeIsUncertain() {
        CartDto cart = cartDto(
            new CartItemDto(11L, 500L, "Green Tea", "Default", "SKU-500", 2, new BigDecimal("4.50"), "USD", new BigDecimal("9.00"))
        );
        String snapshot = "500:2:9.00|9.00|USD";
        when(cartIntegrationService.getCart(10L)).thenReturn(cart);
        when(addressIntegrationService.getAddresses(10L)).thenReturn(List.of(
            address(7L, "Home", "US", "New York", "Main Street", "10A", "5", "10001", true)
        ));
        when(ordersIntegrationService.createOrder(
            10L,
            new CreateOrderRequest(7L, List.of(new OrderItemRequest(500L, 2)), null)
        )).thenThrow(new BackendApiException(
            "orders.createOrder",
            "The store service is temporarily unavailable. Please try again later.",
            503,
            "SERVICE_UNAVAILABLE",
            null,
            new RuntimeException("timeout")
        ));

        UserSession session = userSessionService.getOrCreate(10L, 20L);
        UserSession confirmSession = userSessionService.transitionTo(session, 20L, UserState.CONFIRMING_ORDER, "checkout:confirm");
        userSessionService.rememberInputs(confirmSession, 20L, Map.of(
            "checkoutSelectedAddressId", "7",
            "checkoutCartSnapshot", snapshot
        ));

        BotApiMethod<?> response = checkoutFlowService.handleCallback(
            context(callbackUpdate(10L, 20L, 9, "cb-6", "checkout:confirm")),
            userSessionStore.findByUserId(10L).orElseThrow()
        );

        assertThat(response).isInstanceOf(EditMessageText.class);
        assertThat(((EditMessageText) response).getText()).contains("already being finalized");
        UserSession persisted = userSessionStore.findByUserId(10L).orElseThrow();
        assertThat(persisted.getAttributes())
            .containsEntry("checkoutOrderSubmissionState", "pending")
            .containsEntry("checkoutOrderSubmissionKey", snapshot + "|7")
            .containsKey("checkoutOrderSubmissionStartedAt");
    }

    @Test
    void uncertainAddressWriteDoesNotReopenManualEntryWhileSubmissionIsStillPending() {
        CartDto cart = cartDto(
            new CartItemDto(11L, 500L, "Green Tea", "Default", "SKU-500", 2, new BigDecimal("4.50"), "USD", new BigDecimal("9.00"))
        );
        when(cartIntegrationService.getCart(10L)).thenReturn(cart);
        when(addressIntegrationService.createAddress(
            10L,
            new CreateAddressRequest(null, "US", "New York", "Main Street", "10A", null, "10001", false)
        )).thenThrow(new BackendApiException(
            "users.createAddress",
            "The store service is temporarily unavailable. Please try again later.",
            503,
            "SERVICE_UNAVAILABLE",
            null,
            new RuntimeException("timeout")
        ));
        when(addressIntegrationService.getAddresses(10L)).thenReturn(List.of());

        UserSession session = userSessionService.getOrCreate(10L, 20L);
        UserSession addressSession = userSessionService.transitionTo(session, 20L, UserState.ENTERING_ADDRESS, "checkout:start");
        userSessionService.rememberInputs(addressSession, 20L, Map.of(
            "checkoutAddressStep", "postalCode",
            "checkoutCountry", "US",
            "checkoutCity", "New York",
            "checkoutStreet", "Main Street",
            "checkoutBuilding", "10A",
            "checkoutCartSnapshot", "snapshot"
        ));

        BotApiMethod<?> response = checkoutFlowService.handleAddressInput(
            context(textUpdate(10L, 20L, 10, "10001")),
            userSessionStore.findByUserId(10L).orElseThrow()
        );

        assertThat(response).isInstanceOf(SendMessage.class);
        assertThat(((SendMessage) response).getText()).contains("previous address submission is still being finalized");
        UserSession persisted = userSessionStore.findByUserId(10L).orElseThrow();
        assertThat(persisted.getAttributes())
            .containsEntry("checkoutAddressSubmissionState", "pending")
            .containsEntry("checkoutAddressSubmissionFingerprint", "US|New York|Main Street|10A|-|10001")
            .containsKey("checkoutAddressSubmissionStartedAt");
    }

    @Test
    void expiredOrderSubmissionMarkerDoesNotBlockCheckoutRestart() {
        when(cartIntegrationService.getCart(10L)).thenReturn(cartDto(
            new CartItemDto(11L, 500L, "Green Tea", "Default", "SKU-500", 2, new BigDecimal("4.50"), "USD", new BigDecimal("9.00"))
        ));
        when(addressIntegrationService.getAddresses(10L)).thenReturn(List.of());

        UserSession session = userSessionService.getOrCreate(10L, 20L);
        UserSession pendingSession = userSessionService.transitionTo(session, 20L, UserState.VIEWING_CART, "/cart");
        userSessionService.rememberInputs(pendingSession, 20L, Map.of(
            "checkoutOrderSubmissionState", "pending",
            "checkoutOrderSubmissionKey", "500:2:9.00|9.00|USD|7",
            "checkoutOrderSubmissionStartedAt", Long.toString(System.currentTimeMillis() - Duration.ofMinutes(6).toMillis())
        ));

        BotApiMethod<?> response = checkoutFlowService.handleCallback(
            context(callbackUpdate(10L, 20L, 11, "cb-7", "checkout:start")),
            userSessionStore.findByUserId(10L).orElseThrow()
        );

        assertThat(response).isInstanceOf(EditMessageText.class);
        assertThat(((EditMessageText) response).getText()).contains("country code");
    }

    @Test
    void deterministicOrderFailureClearsPendingGuardsForImmediateRetry() {
        CartDto cart = cartDto(
            new CartItemDto(11L, 500L, "Green Tea", "Default", "SKU-500", 2, new BigDecimal("4.50"), "USD", new BigDecimal("9.00"))
        );
        String snapshot = "500:2:9.00|9.00|USD";
        when(cartIntegrationService.getCart(10L)).thenReturn(cart);
        when(addressIntegrationService.getAddresses(10L)).thenReturn(List.of(
            address(7L, "Home", "US", "New York", "Main Street", "10A", "5", "10001", true)
        ));
        when(ordersIntegrationService.createOrder(
            10L,
            new CreateOrderRequest(7L, List.of(new OrderItemRequest(500L, 2)), null)
        )).thenThrow(new BackendApiException(
            "orders.createOrder",
            "The store could not process that request right now.",
            400,
            "BAD_REQUEST",
            null,
            new RuntimeException("validation")
        ));

        UserSession session = userSessionService.getOrCreate(10L, 20L);
        UserSession confirmSession = userSessionService.transitionTo(session, 20L, UserState.CONFIRMING_ORDER, "checkout:confirm");
        userSessionService.rememberInputs(confirmSession, 20L, Map.of(
            "checkoutSelectedAddressId", "7",
            "checkoutCartSnapshot", snapshot
        ));

        BotApiMethod<?> failureResponse = checkoutFlowService.handleCallback(
            context(callbackUpdate(10L, 20L, 12, "cb-8", "checkout:confirm")),
            userSessionStore.findByUserId(10L).orElseThrow()
        );

        assertThat(failureResponse).isInstanceOf(EditMessageText.class);
        assertThat(((EditMessageText) failureResponse).getText()).contains("could not process");

        BotApiMethod<?> retryResponse = checkoutFlowService.handleCallback(
            context(callbackUpdate(10L, 20L, 12, "cb-9", "checkout:start")),
            userSessionStore.findByUserId(10L).orElseThrow()
        );

        assertThat(retryResponse).isInstanceOf(EditMessageText.class);
        assertThat(((EditMessageText) retryResponse).getText()).contains("Choose a shipping address");
    }

    private BotUpdateContext context(Update update) {
        return BotUpdateContext.from(update).orElseThrow();
    }

    private CartDto cartDto(CartItemDto... items) {
        List<CartItemDto> itemList = List.of(items);
        BigDecimal total = itemList.stream()
            .map(CartItemDto::totalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartDto(total, "USD", itemList);
    }

    private AddressDto address(
        Long id,
        String label,
        String country,
        String city,
        String street,
        String building,
        String apartment,
        String postalCode,
        boolean isDefault
    ) {
        return new AddressDto(id, label, country, city, street, building, apartment, postalCode, isDefault);
    }

    private List<String> allCallbackData(EditMessageText editMessageText) {
        InlineKeyboardMarkup keyboard = (InlineKeyboardMarkup) editMessageText.getReplyMarkup();
        return keyboard.getKeyboard().stream()
            .flatMap(List::stream)
            .map(button -> button.getCallbackData())
            .toList();
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
