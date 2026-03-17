package com.onlinestore.telegrambot.integration.service;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.integration.BackendApiException;
import com.onlinestore.telegrambot.integration.dto.PageResponse;
import com.onlinestore.telegrambot.integration.dto.catalog.ProductDto;
import com.onlinestore.telegrambot.integration.dto.catalog.ProductFilter;
import com.onlinestore.telegrambot.integration.dto.catalog.VariantDto;
import com.onlinestore.telegrambot.integration.dto.search.ProductSearchResult;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiStoreContextService {

    private final CatalogIntegrationService catalogIntegrationService;
    private final SearchIntegrationService searchIntegrationService;
    private final BotProperties botProperties;

    public String buildContext(String userMessage) {
        StringBuilder context = new StringBuilder(
            "Live OnlineStore context for this Telegram request.\n"
                + "Use only verified details below when discussing products, prices, stock, or categories."
        );

        appendSearchMatches(context, userMessage);
        appendCatalogMatches(context, userMessage);

        return context.toString();
    }

    private void appendSearchMatches(StringBuilder context, String userMessage) {
        context.append("\n\nSearch matches:");
        try {
            PageResponse<ProductSearchResult> searchPage = searchIntegrationService.searchProducts(userMessage);
            List<ProductSearchResult> matches = searchPage.content() == null ? List.of() : searchPage.content().stream()
                .limit(botProperties.getAiAssistant().getMaxRetrievedProducts())
                .toList();
            if (matches.isEmpty()) {
                context.append("\n- none");
                return;
            }

            for (ProductSearchResult match : matches) {
                context.append("\n- ")
                    .append(valueOrUnknown(match.name()))
                    .append(" | category: ").append(valueOrUnknown(match.category()))
                    .append(" | price: ").append(priceRange(match.minPrice(), match.maxPrice(), ""))
                    .append(" | in stock: ").append(match.inStock() ? "yes" : "no");
                if (StringUtils.hasText(match.description())) {
                    context.append(" | note: ").append(truncate(match.description()));
                }
            }
        } catch (BackendApiException ex) {
            log.warn("Assistant search context retrieval failed. operation={}", ex.getOperation(), ex);
            context.append("\n- unavailable right now due to a temporary store search issue");
        }
    }

    private void appendCatalogMatches(StringBuilder context, String userMessage) {
        context.append("\n\nCatalog matches:");
        try {
            PageResponse<ProductDto> catalogPage = catalogIntegrationService.getProducts(
                new ProductFilter(null, userMessage, null, null),
                0,
                botProperties.getAiAssistant().getMaxRetrievedProducts()
            );
            List<ProductDto> matches = catalogPage.content() == null ? List.of() : catalogPage.content().stream()
                .limit(botProperties.getAiAssistant().getMaxRetrievedProducts())
                .toList();
            if (matches.isEmpty()) {
                context.append("\n- none");
                return;
            }

            for (ProductDto product : matches) {
                context.append("\n- ")
                    .append(valueOrUnknown(product.name()))
                    .append(" | slug: ").append(valueOrUnknown(product.slug()))
                    .append(" | category: ").append(valueOrUnknown(product.categoryName()))
                    .append(" | price: ").append(productPriceSummary(product))
                    .append(" | in stock: ").append(isInStock(product) ? "yes" : "no");
                if (StringUtils.hasText(product.description())) {
                    context.append(" | note: ").append(truncate(product.description()));
                }
            }
        } catch (BackendApiException ex) {
            log.warn("Assistant catalog context retrieval failed. operation={}", ex.getOperation(), ex);
            context.append("\n- unavailable right now due to a temporary catalog issue");
        }
    }

    private String productPriceSummary(ProductDto product) {
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

        return priceRange(minPrice, maxPrice, currency);
    }

    private String priceRange(BigDecimal minPrice, BigDecimal maxPrice, String currency) {
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

    private String truncate(String value) {
        int maxLength = botProperties.getAiAssistant().getMaxProductDescriptionCharacters();
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "-";
        }
        return amount.stripTrailingZeros().toPlainString();
    }

    private String appendCurrency(String currency) {
        return StringUtils.hasText(currency) ? " " + currency : "";
    }

    private String valueOrUnknown(String value) {
        return StringUtils.hasText(value) ? value : "unknown";
    }
}
