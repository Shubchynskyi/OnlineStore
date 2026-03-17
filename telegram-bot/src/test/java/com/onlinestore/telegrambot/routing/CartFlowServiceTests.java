package com.onlinestore.telegrambot.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.integration.BackendApiException;
import com.onlinestore.telegrambot.integration.dto.cart.AddCartItemRequest;
import com.onlinestore.telegrambot.integration.dto.cart.CartDto;
import com.onlinestore.telegrambot.integration.dto.cart.CartItemDto;
import com.onlinestore.telegrambot.integration.dto.cart.UpdateCartItemQuantityRequest;
import com.onlinestore.telegrambot.integration.service.CartIntegrationService;
import com.onlinestore.telegrambot.session.UserSession;
import com.onlinestore.telegrambot.session.UserSessionService;
import com.onlinestore.telegrambot.session.UserSessionStore;
import com.onlinestore.telegrambot.session.UserState;
import com.onlinestore.telegrambot.support.InteractionThrottlingService;
import com.onlinestore.telegrambot.support.SecurityAuditService;
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
class CartFlowServiceTests {

    @Mock
    private CartIntegrationService cartIntegrationService;
    @Mock
    private SecurityAuditService securityAuditService;

    private InMemoryUserSessionStore userSessionStore;
    private UserSessionService userSessionService;
    private CartFlowService cartFlowService;

    @BeforeEach
    void setUp() {
        userSessionStore = new InMemoryUserSessionStore();

        BotProperties botProperties = new BotProperties();
        botProperties.setToken("test-token");
        userSessionService = new UserSessionService(userSessionStore, botProperties);
        InteractionThrottlingService interactionThrottlingService = new InteractionThrottlingService(botProperties, null);

        cartFlowService = new CartFlowService(
            cartIntegrationService,
            userSessionService,
            new TelegramMessageFactory(),
            interactionThrottlingService,
            securityAuditService
        );
    }

    @Test
    void openCartShowsItemsAndTransitionsToViewingCart() {
        when(cartIntegrationService.getCart(10L)).thenReturn(cartDto(
            new CartItemDto(11L, 500L, "Green Tea", "Default", "SKU-500", 2, new BigDecimal("4.50"), "USD", new BigDecimal("9.00"))
        ));

        UserSession userSession = userSessionService.getOrCreate(10L, 20L);
        BotApiMethod<?> response = cartFlowService.openCart(context(textUpdate(10L, 20L, 1, "/cart")), userSession, "/cart");

        assertThat(response).isInstanceOf(SendMessage.class);
        SendMessage sendMessage = (SendMessage) response;
        assertThat(sendMessage.getText()).contains("Your cart").contains("Green Tea").contains("Total: 9 USD");
        assertThat(callbackData(sendMessage)).contains("checkout:start");
        assertThat(userSessionStore.findByUserId(10L).orElseThrow().getState()).isEqualTo(UserState.VIEWING_CART);
    }

    @Test
    void addVariantCallbackAddsItemAndShowsUpdatedCart() {
        when(cartIntegrationService.addItem(10L, new AddCartItemRequest(500L, 1))).thenReturn(cartDto(
            new CartItemDto(11L, 500L, "Green Tea", "Default", "SKU-500", 1, new BigDecimal("4.50"), "USD", new BigDecimal("4.50"))
        ));

        UserSession userSession = userSessionService.getOrCreate(10L, 20L);
        BotApiMethod<?> response = cartFlowService.handleCallback(
            context(callbackUpdate(10L, 20L, 7, "cb-1", CartFlowService.addVariantCallback(500L))),
            userSession
        );

        assertThat(response).isInstanceOf(EditMessageText.class);
        assertThat(((EditMessageText) response).getText())
            .contains("Added the selected variant to your cart.")
            .contains("Green Tea");
        assertThat(userSessionStore.findByUserId(10L).orElseThrow().getState()).isEqualTo(UserState.VIEWING_CART);
    }

    @Test
    void incrementCallbackUpdatesQuantityUsingLatestCartSnapshot() {
        when(cartIntegrationService.getCart(10L)).thenReturn(cartDto(
            new CartItemDto(11L, 500L, "Green Tea", "Default", "SKU-500", 2, new BigDecimal("4.50"), "USD", new BigDecimal("9.00"))
        ));
        when(cartIntegrationService.updateItemQuantity(10L, 11L, new UpdateCartItemQuantityRequest(3))).thenReturn(cartDto(
            new CartItemDto(11L, 500L, "Green Tea", "Default", "SKU-500", 3, new BigDecimal("4.50"), "USD", new BigDecimal("13.50"))
        ));

        UserSession userSession = userSessionService.getOrCreate(10L, 20L);
        BotApiMethod<?> response = cartFlowService.handleCallback(
            context(callbackUpdate(10L, 20L, 7, "cb-2", "cart:item:11:inc")),
            userSession
        );

        assertThat(response).isInstanceOf(EditMessageText.class);
        assertThat(((EditMessageText) response).getText())
            .contains("Updated your cart.")
            .contains("x3")
            .contains("13.5 USD");
    }

    @Test
    void staleCartMutationShowsRecoveredCartView() {
        when(cartIntegrationService.getCart(10L))
            .thenReturn(cartDto(
                new CartItemDto(11L, 500L, "Green Tea", "Default", "SKU-500", 2, new BigDecimal("4.50"), "USD", new BigDecimal("9.00"))
            ))
            .thenReturn(cartDto(
                new CartItemDto(12L, 500L, "Green Tea", "Default", "SKU-500", 1, new BigDecimal("4.50"), "USD", new BigDecimal("4.50"))
            ));
        when(cartIntegrationService.updateItemQuantity(10L, 11L, new UpdateCartItemQuantityRequest(3))).thenThrow(new BackendApiException(
            "cart.updateItemQuantity",
            "The store could not process that request right now.",
            409,
            "CART_CHANGED",
            null,
            new RuntimeException("Concurrent update")
        ));

        UserSession userSession = userSessionService.getOrCreate(10L, 20L);
        BotApiMethod<?> response = cartFlowService.handleCallback(
            context(callbackUpdate(10L, 20L, 7, "cb-3", "cart:item:11:inc")),
            userSession
        );

        assertThat(response).isInstanceOf(EditMessageText.class);
        assertThat(((EditMessageText) response).getText())
            .contains("updated in the store")
            .contains("x1");
    }

    @Test
    void serverSideMutationFailureAlsoRefreshesLatestCartState() {
        when(cartIntegrationService.getCart(10L))
            .thenReturn(cartDto(
                new CartItemDto(11L, 500L, "Green Tea", "Default", "SKU-500", 2, new BigDecimal("4.50"), "USD", new BigDecimal("9.00"))
            ))
            .thenReturn(cartDto(
                new CartItemDto(12L, 500L, "Green Tea", "Default", "SKU-500", 1, new BigDecimal("4.50"), "USD", new BigDecimal("4.50"))
            ));
        when(cartIntegrationService.updateItemQuantity(10L, 11L, new UpdateCartItemQuantityRequest(3))).thenThrow(new BackendApiException(
            "cart.updateItemQuantity",
            "The store service is temporarily unavailable. Please try again later.",
            500,
            null,
            null,
            new RuntimeException("Optimistic locking failure")
        ));

        UserSession userSession = userSessionService.getOrCreate(10L, 20L);
        BotApiMethod<?> response = cartFlowService.handleCallback(
            context(callbackUpdate(10L, 20L, 7, "cb-4", "cart:item:11:inc")),
            userSession
        );

        assertThat(response).isInstanceOf(EditMessageText.class);
        assertThat(((EditMessageText) response).getText())
            .contains("updated in the store")
            .contains("x1");
    }

    @Test
    void duplicateAddVariantCallbackIsRejectedBeforeRepeatingBackendMutation() {
        when(cartIntegrationService.addItem(10L, new AddCartItemRequest(500L, 1))).thenReturn(cartDto(
            new CartItemDto(11L, 500L, "Green Tea", "Default", "SKU-500", 1, new BigDecimal("4.50"), "USD", new BigDecimal("4.50"))
        ));

        UserSession userSession = userSessionService.getOrCreate(10L, 20L);
        cartFlowService.handleCallback(
            context(callbackUpdate(10L, 20L, 7, "cb-1", CartFlowService.addVariantCallback(500L))),
            userSession
        );
        BotApiMethod<?> duplicateResponse = cartFlowService.handleCallback(
            context(callbackUpdate(10L, 20L, 7, "cb-2", CartFlowService.addVariantCallback(500L))),
            userSession
        );

        assertThat(duplicateResponse).isInstanceOf(AnswerCallbackQuery.class);
        assertThat(((AnswerCallbackQuery) duplicateResponse).getText()).contains("already being processed");
        verify(cartIntegrationService, times(1)).addItem(10L, new AddCartItemRequest(500L, 1));
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

    private List<String> callbackData(SendMessage sendMessage) {
        InlineKeyboardMarkup keyboard = (InlineKeyboardMarkup) sendMessage.getReplyMarkup();
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
