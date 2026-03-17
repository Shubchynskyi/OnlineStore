package com.onlinestore.telegrambot.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.integration.dto.PageResponse;
import com.onlinestore.telegrambot.integration.dto.catalog.ImageDto;
import com.onlinestore.telegrambot.integration.dto.catalog.ProductAttributeDto;
import com.onlinestore.telegrambot.integration.dto.catalog.ProductDto;
import com.onlinestore.telegrambot.integration.dto.catalog.ProductFilter;
import com.onlinestore.telegrambot.integration.dto.catalog.VariantDto;
import com.onlinestore.telegrambot.integration.service.CatalogIntegrationService;
import com.onlinestore.telegrambot.integration.service.SearchIntegrationService;
import com.onlinestore.telegrambot.session.UserSession;
import com.onlinestore.telegrambot.session.UserSessionService;
import com.onlinestore.telegrambot.session.UserSessionStore;
import com.onlinestore.telegrambot.session.UserState;
import com.onlinestore.telegrambot.support.TelegramMessageFactory;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
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
class SearchFlowServiceTests {

    @Mock
    private CatalogIntegrationService catalogIntegrationService;

    @Mock
    private SearchIntegrationService searchIntegrationService;

    private InMemoryUserSessionStore userSessionStore;
    private UserSessionService userSessionService;
    private SearchFlowService searchFlowService;

    @BeforeEach
    void setUp() {
        userSessionStore = new InMemoryUserSessionStore();

        BotProperties botProperties = new BotProperties();
        botProperties.setToken("test-token");
        botProperties.getBackendApi().setSearchPageSize(5);

        userSessionService = new UserSessionService(userSessionStore, botProperties);
        searchFlowService = new SearchFlowService(
            catalogIntegrationService,
            searchIntegrationService,
            userSessionService,
            new TelegramMessageFactory(),
            botProperties
        );
    }

    @Test
    void blankSearchInputPromptsForAtLeastOneKeyword() {
        UserSession userSession = userSessionService.getOrCreate(10L, 20L);

        BotApiMethod<?> response = searchFlowService.handleSearchInput(context(textUpdate(10L, 20L, 1, "   ")), userSession);

        assertThat(response).isInstanceOf(SendMessage.class);
        assertThat(((SendMessage) response).getText()).contains("Please send at least one search keyword.");
    }

    @Test
    void emptySearchResultsIncludeSuggestionsAndRememberQueryState() {
        when(catalogIntegrationService.getProducts(eq(new ProductFilter(null, "oolong", null, null)), eq(0), eq(5)))
            .thenReturn(new PageResponse<>(List.of(), 0, 5, 0, 1, true));
        when(searchIntegrationService.suggest("oolong")).thenReturn(List.of("green tea", "black tea"));

        UserSession userSession = userSessionService.getOrCreate(10L, 20L);
        BotApiMethod<?> response = searchFlowService.handleSearchInput(context(textUpdate(10L, 20L, 1, "oolong")), userSession);

        assertThat(response).isInstanceOf(SendMessage.class);
        SendMessage sendMessage = (SendMessage) response;
        assertThat(sendMessage.getText())
            .contains("No products matched \"oolong\".")
            .contains("Suggestions: green tea, black tea");
        assertThat(callbackData(sendMessage)).contains("search:prompt", "route:main-menu");
        assertThat(userSessionStore.findByUserId(10L).orElseThrow().getAttributes())
            .containsEntry("searchQuery", "oolong")
            .containsEntry("searchPage", "0");
    }

    @Test
    void backCallbackWithoutStoredQueryReopensSearchPrompt() {
        UserSession userSession = userSessionService.getOrCreate(10L, 20L);

        BotApiMethod<?> response = searchFlowService.handleCallback(
            context(callbackUpdate(10L, 20L, 5, "cb-search-back", "search:back")),
            userSession
        );

        assertThat(response).isInstanceOf(EditMessageText.class);
        assertThat(((EditMessageText) response).getText()).contains("Search mode is active.");
        assertThat(userSessionStore.findByUserId(10L).orElseThrow().getState()).isEqualTo(UserState.SEARCHING);
    }

    @Test
    void productCallbackShowsDetailViewAndStoresSelectedProductSlug() {
        when(catalogIntegrationService.getProductBySlug("green-tea")).thenReturn(product("green-tea", "Green Tea"));

        UserSession userSession = userSessionService.getOrCreate(10L, 20L);
        BotApiMethod<?> response = searchFlowService.handleCallback(
            context(callbackUpdate(10L, 20L, 6, "cb-search-product", "search:product:green-tea")),
            userSession
        );

        assertThat(response).isInstanceOf(EditMessageText.class);
        EditMessageText editMessageText = (EditMessageText) response;
        assertThat(editMessageText.getText())
            .contains("Search result details")
            .contains("Green Tea")
            .contains("In stock");
        assertThat(callbackData(editMessageText)).contains("search:back", "cart:add:501", "route:cart");
        assertThat(userSessionStore.findByUserId(10L).orElseThrow().getAttributes())
            .containsEntry("searchProductSlug", "green-tea");
    }

    private BotUpdateContext context(Update update) {
        return BotUpdateContext.from(update).orElseThrow();
    }

    private ProductDto product(String slug, String name) {
        return new ProductDto(
            101L,
            name,
            slug,
            "Loose leaf tea for daily brewing.",
            1L,
            "Tea",
            "tea",
            "ACTIVE",
            false,
            List.of(new VariantDto(
                501L,
                "SKU-501",
                "Default",
                new BigDecimal("4.50"),
                "USD",
                null,
                8,
                Map.of(),
                true
            )),
            List.of(new ImageDto(1L, "https://example.com/tea.jpg", "Green tea", 0, true)),
            List.of(new ProductAttributeDto(1L, "Origin", Map.of("country", "China")))
        );
    }

    private List<String> callbackData(SendMessage sendMessage) {
        InlineKeyboardMarkup keyboard = (InlineKeyboardMarkup) sendMessage.getReplyMarkup();
        return keyboard.getKeyboard().stream()
            .flatMap(List::stream)
            .map(button -> button.getCallbackData())
            .toList();
    }

    private List<String> callbackData(EditMessageText editMessageText) {
        return editMessageText.getReplyMarkup().getKeyboard().stream()
            .flatMap(List::stream)
            .map(button -> button.getCallbackData())
            .toList();
    }

    private Update textUpdate(Long userId, Long chatId, Integer messageId, String text) {
        Message message = Message.builder()
            .messageId(messageId)
            .chat(Chat.builder().id(chatId).type("private").build())
            .from(User.builder().id(userId).firstName("Tester").isBot(false).build())
            .text(text)
            .build();
        Update update = new Update();
        update.setMessage(message);
        return update;
    }

    private Update callbackUpdate(Long userId, Long chatId, Integer messageId, String callbackId, String callbackData) {
        Update update = new Update();
        CallbackQuery callbackQuery = new CallbackQuery();
        callbackQuery.setId(callbackId);
        callbackQuery.setData(callbackData);
        callbackQuery.setFrom(User.builder().id(userId).firstName("Tester").isBot(false).build());
        callbackQuery.setMessage(Message.builder().messageId(messageId).chat(Chat.builder().id(chatId).type("private").build()).build());
        update.setCallbackQuery(callbackQuery);
        return update;
    }

    private static final class InMemoryUserSessionStore implements UserSessionStore {

        private final Map<Long, UserSession> sessions = new ConcurrentHashMap<>();

        @Override
        public Optional<UserSession> findByUserId(Long userId) {
            return Optional.ofNullable(sessions.get(userId));
        }

        @Override
        public UserSession save(UserSession userSession) {
            sessions.put(userSession.getUserId(), userSession);
            return userSession;
        }

        @Override
        public void deleteByUserId(Long userId) {
            sessions.remove(userId);
        }
    }
}
