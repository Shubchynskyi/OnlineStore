package com.onlinestore.telegrambot.routing;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.integration.BackendApiException;
import com.onlinestore.telegrambot.integration.dto.PageResponse;
import com.onlinestore.telegrambot.integration.dto.catalog.ProductDto;
import com.onlinestore.telegrambot.integration.dto.catalog.ProductFilter;
import com.onlinestore.telegrambot.integration.dto.catalog.VariantDto;
import com.onlinestore.telegrambot.integration.service.CatalogIntegrationService;
import com.onlinestore.telegrambot.integration.service.SearchIntegrationService;
import com.onlinestore.telegrambot.session.UserSession;
import com.onlinestore.telegrambot.session.UserSessionService;
import com.onlinestore.telegrambot.session.UserState;
import com.onlinestore.telegrambot.support.TelegramMessageFactory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

@Service
@RequiredArgsConstructor
public class SearchFlowService {

    private static final String CALLBACK_PREFIX = "search:";
    private static final String PROMPT_CALLBACK = CALLBACK_PREFIX + "prompt";
    private static final String RESULTS_BACK_CALLBACK = CALLBACK_PREFIX + "back";
    private static final String ATTRIBUTE_QUERY = "searchQuery";
    private static final String ATTRIBUTE_PAGE = "searchPage";
    private static final String ATTRIBUTE_PRODUCT_SLUG = "searchProductSlug";

    private final CatalogIntegrationService catalogIntegrationService;
    private final SearchIntegrationService searchIntegrationService;
    private final UserSessionService userSessionService;
    private final TelegramMessageFactory telegramMessageFactory;
    private final BotProperties botProperties;

    public BotApiMethod<?> openPrompt(BotUpdateContext updateContext, UserSession userSession, String source) {
        userSessionService.transitionTo(userSession, updateContext.getChatId(), UserState.SEARCHING, source);
        return sendOrEdit(updateContext, promptView(null));
    }

    public BotApiMethod<?> handleSearchInput(BotUpdateContext updateContext, UserSession userSession) {
        String query = updateContext.messageText().orElse("").trim();
        if (!StringUtils.hasText(query)) {
            return telegramMessageFactory.message(updateContext.getChatId(), promptView("Please send at least one search keyword."));
        }

        try {
            return showSearchResults(updateContext, userSession, query, 0);
        } catch (BackendApiException ex) {
            return telegramMessageFactory.menuMessage(updateContext.getChatId(), ex.getMessage());
        }
    }

    public BotApiMethod<?> handleCallback(BotUpdateContext updateContext, UserSession userSession) {
        String callbackData = updateContext.callbackData().orElse("");
        try {
            if (PROMPT_CALLBACK.equals(callbackData)) {
                return openPrompt(updateContext, userSession, "callback:search:prompt");
            }
            if (RESULTS_BACK_CALLBACK.equals(callbackData)) {
                String query = userSession.getAttributes().get(ATTRIBUTE_QUERY);
                if (!StringUtils.hasText(query)) {
                    return openPrompt(updateContext, userSession, "callback:search:back");
                }
                return showSearchResults(updateContext, userSession, query, storedPage(userSession));
            }
            if (callbackData.startsWith(CALLBACK_PREFIX + "page:")) {
                String query = userSession.getAttributes().get(ATTRIBUTE_QUERY);
                if (!StringUtils.hasText(query)) {
                    return openPrompt(updateContext, userSession, "callback:search:page");
                }
                return showSearchResults(updateContext, userSession, query, parsePage(callbackData));
            }
            if (callbackData.startsWith(CALLBACK_PREFIX + "product:")) {
                String[] tokens = callbackData.split(":");
                if (tokens.length < 3) {
                    return unavailableAction(updateContext, "Unknown search product action.");
                }
                return showProductDetail(updateContext, userSession, tokens[2]);
            }
            return unavailableAction(updateContext, "Unknown search action.");
        } catch (BackendApiException ex) {
            return sendOrEdit(updateContext, new BotView(ex.getMessage(), telegramMessageFactory.mainMenuKeyboard()));
        }
    }

    private BotApiMethod<?> showSearchResults(
        BotUpdateContext updateContext,
        UserSession userSession,
        String query,
        int requestedPage
    ) {
        PageResponse<ProductDto> searchResults = catalogIntegrationService.getProducts(
            new ProductFilter(null, query, null, null),
            requestedPage,
            botProperties.getBackendApi().getSearchPageSize()
        );

        List<ProductDto> products = searchResults.content() == null ? List.of() : searchResults.content();
        int page = Math.max(0, searchResults.page());
        int pageCount = Math.max(1, searchResults.totalPages());

        userSessionService.rememberInputs(userSession, updateContext.getChatId(), attributes(
            ATTRIBUTE_QUERY, query,
            ATTRIBUTE_PAGE, Integer.toString(page),
            ATTRIBUTE_PRODUCT_SLUG, null
        ));

        if (products.isEmpty()) {
            return sendOrEdit(updateContext, new BotView(
                emptyResultsText(query),
                emptyResultsKeyboard()
            ));
        }

        StringBuilder text = new StringBuilder("Search results for \"")
            .append(query)
            .append("\" (page ")
            .append(page + 1)
            .append("/")
            .append(pageCount)
            .append(")\nTap a product button to open the detail view.");

        for (ProductDto product : products) {
            text.append("\n\n- ").append(product.name())
                .append("\n  ").append(priceSummary(product));
            if (StringUtils.hasText(product.description())) {
                text.append("\n  ").append(truncate(product.description(), 110));
            }
        }

        return sendOrEdit(updateContext, new BotView(
            text.toString(),
            searchResultsKeyboard(products, page, pageCount)
        ));
    }

    private BotApiMethod<?> showProductDetail(BotUpdateContext updateContext, UserSession userSession, String productSlug) {
        ProductDto product = catalogIntegrationService.getProductBySlug(productSlug);
        userSessionService.rememberInputs(userSession, updateContext.getChatId(), attributes(
            ATTRIBUTE_PRODUCT_SLUG, product.slug()
        ));

        StringBuilder text = new StringBuilder("Search result details")
            .append("\nName: ").append(product.name())
            .append("\nCategory: ").append(valueOrDash(product.categoryName()))
            .append("\nPrice: ").append(priceSummary(product))
            .append("\nAvailability: ").append(isInStock(product) ? "In stock" : "Out of stock");

        if (StringUtils.hasText(product.description())) {
            text.append("\n\n").append(product.description());
        }

        return sendOrEdit(updateContext, new BotView(text.toString(), productDetailKeyboard(product)));
    }

    private BotView promptView(String hint) {
        StringBuilder text = new StringBuilder("Search mode is active.\nSend a product name, keywords, or part of a title.");
        if (StringUtils.hasText(hint)) {
            text.append("\n\n").append(hint);
        }
        return new BotView(text.toString(), telegramMessageFactory.keyboard(List.of(
            new InlineKeyboardRow(
                telegramMessageFactory.callbackButton("Main menu", "route:main-menu")
            )
        )));
    }

    private String emptyResultsText(String query) {
        List<String> suggestions = searchIntegrationService.suggest(query);
        StringBuilder text = new StringBuilder("No products matched \"").append(query).append("\".");
        if (suggestions != null && !suggestions.isEmpty()) {
            text.append("\nSuggestions: ").append(String.join(", ", suggestions.stream().limit(3).toList()));
        }
        text.append("\nTry a different keyword or start a new search.");
        return text.toString();
    }

    private InlineKeyboardMarkup emptyResultsKeyboard() {
        return telegramMessageFactory.keyboard(List.of(
            new InlineKeyboardRow(
                telegramMessageFactory.callbackButton("New search", PROMPT_CALLBACK),
                telegramMessageFactory.callbackButton("Main menu", "route:main-menu")
            )
        ));
    }

    private InlineKeyboardMarkup searchResultsKeyboard(List<ProductDto> products, int page, int pageCount) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (ProductDto product : products) {
            rows.add(new InlineKeyboardRow(
                telegramMessageFactory.callbackButton(product.name(), CALLBACK_PREFIX + "product:" + product.slug())
            ));
        }
        addPaginationRow(rows, page, pageCount);
        rows.add(new InlineKeyboardRow(
            telegramMessageFactory.callbackButton("New search", PROMPT_CALLBACK),
            telegramMessageFactory.callbackButton("Main menu", "route:main-menu")
        ));
        return telegramMessageFactory.keyboard(rows);
    }

    private InlineKeyboardMarkup productDetailKeyboard(ProductDto product) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(
            telegramMessageFactory.callbackButton("Back to results", RESULTS_BACK_CALLBACK)
        ));
        for (VariantDto variant : availableVariants(product)) {
            String buttonText = product.variants() != null && product.variants().size() > 1
                ? "Add " + valueOrDash(variant.name())
                : "Add to cart";
            rows.add(new InlineKeyboardRow(
                telegramMessageFactory.callbackButton(buttonText, CartFlowService.addVariantCallback(variant.id()))
            ));
        }
        rows.add(new InlineKeyboardRow(
            telegramMessageFactory.callbackButton("Open cart", "route:cart"),
            telegramMessageFactory.callbackButton("Main menu", "route:main-menu")
        ));
        return telegramMessageFactory.keyboard(rows);
    }

    private void addPaginationRow(List<InlineKeyboardRow> rows, int page, int pageCount) {
        if (pageCount <= 1) {
            return;
        }

        List<InlineKeyboardButton> buttons = new ArrayList<>();
        if (page > 0) {
            buttons.add(telegramMessageFactory.callbackButton("◀ Prev", CALLBACK_PREFIX + "page:" + (page - 1)));
        }
        if (page < pageCount - 1) {
            buttons.add(telegramMessageFactory.callbackButton("Next ▶", CALLBACK_PREFIX + "page:" + (page + 1)));
        }
        if (!buttons.isEmpty()) {
            rows.add(new InlineKeyboardRow(buttons));
        }
    }

    private String priceSummary(ProductDto product) {
        if (product.variants() == null || product.variants().isEmpty()) {
            return "-";
        }

        BigDecimal minPrice = product.variants().stream()
            .map(VariantDto::price)
            .filter(price -> price != null)
            .min(BigDecimal::compareTo)
            .orElse(null);
        BigDecimal maxPrice = product.variants().stream()
            .map(VariantDto::price)
            .filter(price -> price != null)
            .max(BigDecimal::compareTo)
            .orElse(null);
        String currency = product.variants().stream()
            .map(VariantDto::currency)
            .filter(StringUtils::hasText)
            .findFirst()
            .orElse("");

        if (minPrice == null && maxPrice == null) {
            return "-";
        }
        if (minPrice != null && minPrice.equals(maxPrice)) {
            return formatAmount(minPrice) + appendCurrency(currency);
        }
        return formatAmount(minPrice) + " - " + formatAmount(maxPrice) + appendCurrency(currency);
    }

    private boolean isInStock(ProductDto product) {
        if (product.variants() == null) {
            return false;
        }
        return product.variants().stream()
            .map(VariantDto::stock)
            .anyMatch(stock -> stock != null && stock > 0);
    }

    private String appendCurrency(String currency) {
        return StringUtils.hasText(currency) ? " " + currency : "";
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "-";
        }
        return amount.stripTrailingZeros().toPlainString();
    }

    private BotApiMethod<?> unavailableAction(BotUpdateContext updateContext, String message) {
        return telegramMessageFactory.callbackNotice(updateContext.callbackQueryId().orElseThrow(), message);
    }

    private BotApiMethod<?> sendOrEdit(BotUpdateContext updateContext, BotView botView) {
        Integer messageId = updateContext.messageId().orElse(null);
        if (updateContext.callbackQueryId().isPresent() && messageId != null) {
            return telegramMessageFactory.editMessage(updateContext.getChatId(), messageId, botView);
        }
        return telegramMessageFactory.message(updateContext.getChatId(), botView);
    }

    private int parsePage(String callbackData) {
        String[] tokens = callbackData.split(":");
        if (tokens.length < 3) {
            return 0;
        }
        return safeParseInt(tokens[2]);
    }

    private int storedPage(UserSession userSession) {
        return safeParseInt(userSession.getAttributes().get(ATTRIBUTE_PAGE));
    }

    private List<VariantDto> availableVariants(ProductDto product) {
        if (product.variants() == null) {
            return List.of();
        }
        return product.variants().stream()
            .filter(variant -> variant.active() && variant.stock() != null && variant.stock() > 0)
            .toList();
    }

    private int safeParseInt(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private Map<String, String> attributes(String... keyValues) {
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            attributes.put(keyValues[index], keyValues[index + 1]);
        }
        return attributes;
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private String valueOrDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }
}
