package com.onlinestore.telegrambot.routing;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.integration.BackendApiException;
import com.onlinestore.telegrambot.integration.dto.PageResponse;
import com.onlinestore.telegrambot.integration.dto.catalog.CategoryDto;
import com.onlinestore.telegrambot.integration.dto.catalog.CategoryWithProductsDto;
import com.onlinestore.telegrambot.integration.dto.catalog.ProductAttributeDto;
import com.onlinestore.telegrambot.integration.dto.catalog.ProductDto;
import com.onlinestore.telegrambot.integration.dto.catalog.VariantDto;
import com.onlinestore.telegrambot.integration.service.CatalogIntegrationService;
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
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

@Service
@RequiredArgsConstructor
public class CatalogBrowserService {

    private static final String CALLBACK_PREFIX = "catalog:";
    private static final String CATEGORIES_CALLBACK = CALLBACK_PREFIX + "categories";
    private static final String CATEGORIES_BACK_CALLBACK = CATEGORIES_CALLBACK + ":back";
    private static final String CATEGORY_BACK_CALLBACK = CALLBACK_PREFIX + "category:back";
    private static final String ATTRIBUTE_CATEGORIES_PAGE = "catalogCategoriesPage";
    private static final String ATTRIBUTE_CATEGORY_SLUG = "catalogCategorySlug";
    private static final String ATTRIBUTE_CATEGORY_NAME = "catalogCategoryName";
    private static final String ATTRIBUTE_CATEGORY_PAGE = "catalogCategoryPage";
    private static final String ATTRIBUTE_PRODUCT_SLUG = "catalogProductSlug";

    private final CatalogIntegrationService catalogIntegrationService;
    private final UserSessionService userSessionService;
    private final TelegramMessageFactory telegramMessageFactory;
    private final BotProperties botProperties;

    public BotApiMethod<?> openCatalog(BotUpdateContext updateContext, UserSession userSession, String source) {
        try {
            UserSession catalogSession = userSessionService.transitionTo(
                userSession,
                updateContext.getChatId(),
                UserState.BROWSING_CATALOG,
                source
            );
            return showCategories(updateContext, catalogSession, 0);
        } catch (BackendApiException ex) {
            return sendOrEdit(updateContext, new BotView(ex.getMessage(), telegramMessageFactory.mainMenuKeyboard()));
        }
    }

    public BotApiMethod<?> handleCallback(BotUpdateContext updateContext, UserSession userSession) {
        String callbackData = updateContext.callbackData().orElse("");
        try {
            if (CATEGORIES_BACK_CALLBACK.equals(callbackData)) {
                return showCategories(updateContext, userSession, storedPage(userSession, ATTRIBUTE_CATEGORIES_PAGE));
            }
            if (callbackData.startsWith(CATEGORIES_CALLBACK + ":")) {
                return showCategories(updateContext, userSession, parsePage(callbackData, 2));
            }
            if (CATEGORY_BACK_CALLBACK.equals(callbackData)) {
                String categorySlug = userSession.getAttributes().get(ATTRIBUTE_CATEGORY_SLUG);
                if (categorySlug == null || categorySlug.isBlank()) {
                    return showCategories(updateContext, userSession, storedPage(userSession, ATTRIBUTE_CATEGORIES_PAGE));
                }
                return showCategory(updateContext, userSession, categorySlug, storedPage(userSession, ATTRIBUTE_CATEGORY_PAGE));
            }
            if (callbackData.startsWith(CALLBACK_PREFIX + "category:")) {
                String[] tokens = callbackData.split(":");
                if (tokens.length < 4) {
                    return unavailableAction(updateContext, "Unknown catalog category action.");
                }
                return showCategory(updateContext, userSession, tokens[2], safeParseInt(tokens[3]));
            }
            if (callbackData.startsWith(CALLBACK_PREFIX + "product:")) {
                String[] tokens = callbackData.split(":");
                if (tokens.length < 3) {
                    return unavailableAction(updateContext, "Unknown product action.");
                }
                return showProductDetail(updateContext, userSession, tokens[2]);
            }
            return unavailableAction(updateContext, "Unknown catalog action.");
        } catch (BackendApiException ex) {
            return sendOrEdit(updateContext, new BotView(ex.getMessage(), telegramMessageFactory.mainMenuKeyboard()));
        }
    }

    private BotApiMethod<?> showCategories(BotUpdateContext updateContext, UserSession userSession, int requestedPage) {
        List<CategoryDto> categories = catalogIntegrationService.getCategories();
        if (categories == null || categories.isEmpty()) {
            return sendOrEdit(updateContext, new BotView(
                "Catalog integration is active, but no categories are available right now.",
                fallbackKeyboard()
            ));
        }

        int pageSize = botProperties.getBackendApi().getCatalogPageSize();
        int pageCount = Math.max(1, (int) Math.ceil((double) categories.size() / pageSize));
        int page = clampPage(requestedPage, pageCount);
        int fromIndex = page * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, categories.size());
        List<CategoryDto> pageContent = categories.subList(fromIndex, toIndex);

        userSessionService.rememberInputs(userSession, updateContext.getChatId(), attributes(
            ATTRIBUTE_CATEGORIES_PAGE, Integer.toString(page),
            ATTRIBUTE_CATEGORY_SLUG, null,
            ATTRIBUTE_CATEGORY_NAME, null,
            ATTRIBUTE_CATEGORY_PAGE, null,
            ATTRIBUTE_PRODUCT_SLUG, null
        ));

        StringBuilder text = new StringBuilder("Catalog categories (page ")
            .append(page + 1)
            .append("/")
            .append(pageCount)
            .append(")\nChoose a category to browse products.");

        for (CategoryDto category : pageContent) {
            text.append("\n\n- ").append(valueOrDash(category.name()));
            if (hasText(category.description())) {
                text.append("\n  ").append(category.description());
            }
        }

        return sendOrEdit(updateContext, new BotView(text.toString(), categoryKeyboard(pageContent, page, pageCount)));
    }

    private BotApiMethod<?> showCategory(
        BotUpdateContext updateContext,
        UserSession userSession,
        String categorySlug,
        int requestedPage
    ) {
        CategoryWithProductsDto categoryWithProducts = catalogIntegrationService.getCategoryBySlug(
            categorySlug,
            requestedPage,
            botProperties.getBackendApi().getCatalogPageSize()
        );
        CategoryDto category = categoryWithProducts.category();
        PageResponse<ProductDto> productPage = categoryWithProducts.products();
        List<ProductDto> products = productPage == null || productPage.content() == null
            ? List.of()
            : productPage.content();

        int page = productPage == null ? 0 : productPage.page();
        int pageCount = productPage == null ? 1 : Math.max(1, productPage.totalPages());

        userSessionService.rememberInputs(userSession, updateContext.getChatId(), attributes(
            ATTRIBUTE_CATEGORY_SLUG, category.slug(),
            ATTRIBUTE_CATEGORY_NAME, valueOrDash(category.name()),
            ATTRIBUTE_CATEGORY_PAGE, Integer.toString(page),
            ATTRIBUTE_PRODUCT_SLUG, null
        ));

        StringBuilder text = new StringBuilder(valueOrDash(category.name()));
        if (hasText(category.description())) {
            text.append("\n").append(category.description());
        }
        text.append("\n\nProducts page ").append(page + 1).append("/").append(pageCount);

        if (products.isEmpty()) {
            text.append("\nNo products are available in this category right now.");
        } else {
            for (ProductDto product : products) {
                text.append("\n\n- ").append(valueOrDash(product.name()))
                    .append("\n  ").append(priceSummary(product));
                if (hasText(product.description())) {
                    text.append("\n  ").append(truncate(product.description(), 110));
                }
            }
        }

        return sendOrEdit(updateContext, new BotView(
            text.toString(),
            categoryProductsKeyboard(products, category.slug(), page, pageCount)
        ));
    }

    private BotApiMethod<?> showProductDetail(BotUpdateContext updateContext, UserSession userSession, String productSlug) {
        ProductDto product = catalogIntegrationService.getProductBySlug(productSlug);
        userSessionService.rememberInputs(userSession, updateContext.getChatId(), attributes(
            ATTRIBUTE_PRODUCT_SLUG, product.slug()
        ));

        return sendOrEdit(updateContext, new BotView(buildProductDetail(product), categoryProductDetailKeyboard()));
    }

    private InlineKeyboardMarkup categoryKeyboard(List<CategoryDto> categories, int page, int pageCount) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (CategoryDto category : categories) {
            rows.add(new InlineKeyboardRow(
                telegramMessageFactory.callbackButton(category.name(), CALLBACK_PREFIX + "category:" + category.slug() + ":0")
            ));
        }
        addPaginationRow(rows, page, pageCount, CATEGORIES_CALLBACK);
        rows.add(new InlineKeyboardRow(
            telegramMessageFactory.callbackButton("Main menu", "route:main-menu")
        ));
        return telegramMessageFactory.keyboard(rows);
    }

    private InlineKeyboardMarkup categoryProductsKeyboard(
        List<ProductDto> products,
        String categorySlug,
        int page,
        int pageCount
    ) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (ProductDto product : products) {
            rows.add(new InlineKeyboardRow(
                telegramMessageFactory.callbackButton(product.name(), CALLBACK_PREFIX + "product:" + product.slug())
            ));
        }
        addPaginationRow(rows, page, pageCount, CALLBACK_PREFIX + "category:" + categorySlug);
        rows.add(new InlineKeyboardRow(
            telegramMessageFactory.callbackButton("All categories", CATEGORIES_BACK_CALLBACK),
            telegramMessageFactory.callbackButton("Main menu", "route:main-menu")
        ));
        return telegramMessageFactory.keyboard(rows);
    }

    private InlineKeyboardMarkup categoryProductDetailKeyboard() {
        return telegramMessageFactory.keyboard(List.of(
            new InlineKeyboardRow(
                telegramMessageFactory.callbackButton("Back to products", CATEGORY_BACK_CALLBACK)
            ),
            new InlineKeyboardRow(
                telegramMessageFactory.callbackButton("All categories", CATEGORIES_BACK_CALLBACK),
                telegramMessageFactory.callbackButton("Main menu", "route:main-menu")
            )
        ));
    }

    private InlineKeyboardMarkup fallbackKeyboard() {
        return telegramMessageFactory.keyboard(List.of(
            new InlineKeyboardRow(
                telegramMessageFactory.callbackButton("All categories", CATEGORIES_BACK_CALLBACK),
                telegramMessageFactory.callbackButton("Main menu", "route:main-menu")
            )
        ));
    }

    private void addPaginationRow(List<InlineKeyboardRow> rows, int page, int pageCount, String prefix) {
        if (pageCount <= 1) {
            return;
        }

        List<InlineKeyboardButton> buttons = new ArrayList<>();
        if (page > 0) {
            buttons.add(telegramMessageFactory.callbackButton("◀ Prev", prefix + ":" + (page - 1)));
        }
        if (page < pageCount - 1) {
            buttons.add(telegramMessageFactory.callbackButton("Next ▶", prefix + ":" + (page + 1)));
        }
        if (!buttons.isEmpty()) {
            rows.add(new InlineKeyboardRow(buttons));
        }
    }

    private String buildProductDetail(ProductDto product) {
        StringBuilder text = new StringBuilder("Product details")
            .append("\nName: ").append(valueOrDash(product.name()))
            .append("\nCategory: ").append(valueOrDash(product.categoryName()))
            .append("\nPrice: ").append(priceSummary(product))
            .append("\nAvailability: ").append(isInStock(product) ? "In stock" : "Out of stock");

        if (hasText(product.description())) {
            text.append("\n\n").append(product.description());
        }

        String mainImageUrl = mainImageUrl(product);
        if (hasText(mainImageUrl)) {
            text.append("\n\nMain image: ").append(mainImageUrl);
        }

        List<String> attributeLines = attributeLines(product);
        if (!attributeLines.isEmpty()) {
            text.append("\n\nAttributes:");
            for (String attributeLine : attributeLines) {
                text.append("\n- ").append(attributeLine);
            }
        }

        return text.toString();
    }

    private List<String> attributeLines(ProductDto product) {
        if (product.attributes() == null) {
            return List.of();
        }
        return product.attributes().stream()
            .limit(3)
            .map(this::attributeLine)
            .toList();
    }

    private String attributeLine(ProductAttributeDto attribute) {
        if (attribute.value() == null || attribute.value().isEmpty()) {
            return valueOrDash(attribute.name());
        }
        return valueOrDash(attribute.name()) + ": " + attribute.value();
    }

    private String mainImageUrl(ProductDto product) {
        if (product.images() == null || product.images().isEmpty()) {
            return null;
        }
        return product.images().stream()
            .filter(image -> image.isMain())
            .map(image -> image.url())
            .findFirst()
            .orElse(product.images().get(0).url());
    }

    private boolean isInStock(ProductDto product) {
        if (product.variants() == null) {
            return false;
        }
        return product.variants().stream()
            .map(VariantDto::stock)
            .anyMatch(stock -> stock != null && stock > 0);
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
            .filter(this::hasText)
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

    private String appendCurrency(String currency) {
        return hasText(currency) ? " " + currency : "";
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

    private int storedPage(UserSession userSession, String attributeKey) {
        return safeParseInt(userSession.getAttributes().get(attributeKey));
    }

    private int parsePage(String callbackData, int index) {
        String[] tokens = callbackData.split(":");
        if (tokens.length <= index) {
            return 0;
        }
        return safeParseInt(tokens[index]);
    }

    private int safeParseInt(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private int clampPage(int requestedPage, int totalPages) {
        if (totalPages <= 1) {
            return 0;
        }
        return Math.max(0, Math.min(requestedPage, totalPages - 1));
    }

    private Map<String, String> attributes(String... keyValues) {
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            attributes.put(keyValues[index], keyValues[index + 1]);
        }
        return attributes;
    }

    private String truncate(String value, int maxLength) {
        if (!hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String valueOrDash(String value) {
        return hasText(value) ? value : "-";
    }
}
