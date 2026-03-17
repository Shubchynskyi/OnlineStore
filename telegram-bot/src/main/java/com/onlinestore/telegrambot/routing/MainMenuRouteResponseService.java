package com.onlinestore.telegrambot.routing;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.integration.BackendApiException;
import com.onlinestore.telegrambot.integration.BackendAuthenticationRequiredException;
import com.onlinestore.telegrambot.integration.dto.PageResponse;
import com.onlinestore.telegrambot.integration.dto.cart.CartDto;
import com.onlinestore.telegrambot.integration.dto.cart.CartItemDto;
import com.onlinestore.telegrambot.integration.dto.catalog.CategoryDto;
import com.onlinestore.telegrambot.integration.dto.orders.OrderDto;
import com.onlinestore.telegrambot.integration.service.CartIntegrationService;
import com.onlinestore.telegrambot.integration.service.CatalogIntegrationService;
import com.onlinestore.telegrambot.integration.service.OrdersIntegrationService;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MainMenuRouteResponseService {

    private final CatalogIntegrationService catalogIntegrationService;
    private final CartIntegrationService cartIntegrationService;
    private final OrdersIntegrationService ordersIntegrationService;
    private final BotProperties botProperties;

    public String responseForRoute(String route, Long telegramUserId) {
        return switch (route) {
            case "start" -> "Welcome to the OnlineStore bot.\nUse the inline menu to browse the catalog, search products, open your cart, or track an order.";
            case "main-menu" -> "Main menu is ready.\nChoose what you want to do next.";
            case "catalog" -> catalogResponse();
            case "search" -> "Search mode is active. Send a product name or keywords.";
            case "cart" -> cartResponse(telegramUserId);
            case "order" -> ordersResponse(telegramUserId);
            default -> "Unknown action.";
        };
    }

    private String catalogResponse() {
        try {
            List<CategoryDto> categories = catalogIntegrationService.getCategories();
            if (categories == null || categories.isEmpty()) {
                return "Catalog integration is active, but no categories are available right now.";
            }

            int limit = Math.min(botProperties.getBackendApi().getCatalogPageSize(), categories.size());
            String categoryLines = categories.stream()
                .limit(limit)
                .map(category -> "- " + category.name() + " (" + category.slug() + ")")
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

            String overflowNotice = categories.size() > limit
                ? "\nMore categories are available in the backend catalog."
                : "";

            return "Catalog integration is active.\nAvailable categories:\n"
                + categoryLines
                + overflowNotice
                + "\nTap Catalog to open inline category navigation.";
        } catch (BackendApiException ex) {
            return ex.getMessage();
        }
    }

    private String cartResponse(Long telegramUserId) {
        try {
            CartDto cart = cartIntegrationService.getCart(telegramUserId);
            List<CartItemDto> items = cart.items() == null ? List.of() : cart.items();
            if (items.isEmpty()) {
                return "Cart integration is active. Your cart is currently empty.";
            }

            String itemLines = items.stream()
                .limit(3)
                .map(this::cartItemLine)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

            return "Cart integration is active.\n"
                + itemLines
                + "\nTotal: " + formatAmount(cart.totalAmount()) + " " + valueOrDash(cart.totalCurrency())
                + "\nInline cart actions arrive in T-005.";
        } catch (BackendAuthenticationRequiredException ex) {
            return ex.getMessage();
        } catch (BackendApiException ex) {
            return ex.getMessage();
        }
    }

    private String ordersResponse(Long telegramUserId) {
        try {
            PageResponse<OrderDto> ordersPage = ordersIntegrationService.getOrders(telegramUserId);
            List<OrderDto> orders = ordersPage.content() == null ? List.of() : ordersPage.content();
            if (orders.isEmpty()) {
                return "Order integration is active, but there are no orders for the linked account yet.";
            }

            String orderLines = orders.stream()
                .limit(botProperties.getBackendApi().getRecentOrdersPageSize())
                .map(this::orderLine)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

            return "Order integration is active. Recent orders:\n"
                + orderLines
                + "\nSend a numeric order id to look up a specific order.";
        } catch (BackendAuthenticationRequiredException ex) {
            return ex.getMessage();
        } catch (BackendApiException ex) {
            return ex.getMessage();
        }
    }

    private String cartItemLine(CartItemDto item) {
        return "- " + valueOrDash(item.productName())
            + " x" + item.quantity()
            + " = " + formatAmount(item.totalAmount())
            + " " + valueOrDash(item.unitPriceCurrency());
    }

    private String orderLine(OrderDto order) {
        return "- #" + order.id()
            + " " + valueOrDash(order.status())
            + " - " + formatAmount(order.totalAmount())
            + " " + valueOrDash(order.totalCurrency());
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "-";
        }
        return amount.stripTrailingZeros().toPlainString();
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
