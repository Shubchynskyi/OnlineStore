package com.onlinestore.telegrambot.routing;

import com.onlinestore.telegrambot.integration.BackendApiException;
import com.onlinestore.telegrambot.integration.BackendAuthenticationRequiredException;
import com.onlinestore.telegrambot.integration.dto.cart.AddCartItemRequest;
import com.onlinestore.telegrambot.integration.dto.cart.CartDto;
import com.onlinestore.telegrambot.integration.dto.cart.CartItemDto;
import com.onlinestore.telegrambot.integration.dto.cart.UpdateCartItemQuantityRequest;
import com.onlinestore.telegrambot.integration.service.CartIntegrationService;
import com.onlinestore.telegrambot.session.UserSession;
import com.onlinestore.telegrambot.session.UserSessionService;
import com.onlinestore.telegrambot.session.UserState;
import com.onlinestore.telegrambot.support.InteractionThrottlingService;
import com.onlinestore.telegrambot.support.SecurityAuditService;
import com.onlinestore.telegrambot.support.TelegramMessageFactory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

@Service
@RequiredArgsConstructor
public class CartFlowService {

    private static final String CALLBACK_PREFIX = "cart:";
    private static final String ADD_PREFIX = CALLBACK_PREFIX + "add:";
    private static final String ITEM_PREFIX = CALLBACK_PREFIX + "item:";
    private static final String REFRESH_CALLBACK = CALLBACK_PREFIX + "refresh";
    private static final String RECOVERY_MESSAGE =
        "Your cart was updated in the store while you were chatting. Review the latest version below.";
    private static final String CART_ACTION_IN_PROGRESS_MESSAGE =
        "This cart action is already being processed. Please wait a moment and refresh if needed.";

    private final CartIntegrationService cartIntegrationService;
    private final UserSessionService userSessionService;
    private final TelegramMessageFactory telegramMessageFactory;
    private final InteractionThrottlingService interactionThrottlingService;
    private final SecurityAuditService securityAuditService;

    public static String addVariantCallback(Long variantId) {
        return ADD_PREFIX + variantId;
    }

    public BotApiMethod<?> openCart(BotUpdateContext updateContext, UserSession userSession, String source) {
        try {
            UserSession cartSession = userSessionService.transitionTo(
                userSession,
                updateContext.getChatId(),
                UserState.VIEWING_CART,
                source
            );
            CartDto cart = cartIntegrationService.getCart(updateContext.getUserId());
            return showCart(updateContext, cartSession, cart, null);
        } catch (BackendAuthenticationRequiredException | BackendApiException ex) {
            return sendOrEdit(updateContext, new BotView(ex.getMessage(), telegramMessageFactory.mainMenuKeyboard()));
        }
    }

    public BotApiMethod<?> handleCallback(BotUpdateContext updateContext, UserSession userSession) {
        String callbackData = updateContext.callbackData().orElse("");
        try {
            if (callbackData.startsWith(ADD_PREFIX)) {
                return addVariant(updateContext, userSession, callbackData);
            }
            if (REFRESH_CALLBACK.equals(callbackData)) {
                return openCart(updateContext, userSession, "callback:cart:refresh");
            }
            if (callbackData.startsWith(ITEM_PREFIX)) {
                return handleItemAction(updateContext, userSession, callbackData);
            }
            return unavailableAction(updateContext, "Unknown cart action.");
        } catch (BackendAuthenticationRequiredException ex) {
            return sendOrEdit(updateContext, new BotView(ex.getMessage(), telegramMessageFactory.mainMenuKeyboard()));
        } catch (BackendApiException ex) {
            return sendOrEdit(updateContext, new BotView(ex.getMessage(), telegramMessageFactory.mainMenuKeyboard()));
        }
    }

    private BotApiMethod<?> addVariant(BotUpdateContext updateContext, UserSession userSession, String callbackData) {
        Long variantId = safeParseLong(callbackData.substring(ADD_PREFIX.length()));
        if (variantId == null) {
            return unavailableAction(updateContext, "Unknown product variant.");
        }
        if (!tryAcquireCartMutation(updateContext, callbackData)) {
            return unavailableAction(updateContext, CART_ACTION_IN_PROGRESS_MESSAGE);
        }

        try {
            CartDto cart = cartIntegrationService.addItem(updateContext.getUserId(), new AddCartItemRequest(variantId, 1));
            UserSession cartSession = userSessionService.transitionTo(
                userSession,
                updateContext.getChatId(),
                UserState.VIEWING_CART,
                "callback:cart:add"
            );
            return showCart(updateContext, cartSession, cart, "Added the selected variant to your cart.");
        } catch (BackendApiException ex) {
            if (isRecoverableMutationFailure(ex)) {
                return recoverLatestCart(updateContext, userSession, "callback:cart:add");
            }
            return sendOrEdit(updateContext, new BotView(ex.getMessage(), telegramMessageFactory.mainMenuKeyboard()));
        }
    }

    private BotApiMethod<?> handleItemAction(BotUpdateContext updateContext, UserSession userSession, String callbackData) {
        String[] tokens = callbackData.split(":");
        if (tokens.length < 4) {
            return unavailableAction(updateContext, "Unknown cart item action.");
        }

        Long itemId = safeParseLong(tokens[2]);
        if (itemId == null) {
            return unavailableAction(updateContext, "Unknown cart item.");
        }

        if (!"info".equals(tokens[3]) && !tryAcquireCartMutation(updateContext, callbackData)) {
            return unavailableAction(updateContext, CART_ACTION_IN_PROGRESS_MESSAGE);
        }

        CartDto currentCart = cartIntegrationService.getCart(updateContext.getUserId());
        CartItemDto cartItem = findItem(currentCart, itemId);
        if (cartItem == null) {
            return recoverLatestCart(updateContext, userSession, "callback:cart:item");
        }

        return switch (tokens[3]) {
            case "info" -> telegramMessageFactory.callbackNotice(
                updateContext.callbackQueryId().orElseThrow(),
                cartItem.productName() + " x" + cartItem.quantity()
            );
            case "inc" -> mutateCart(updateContext, userSession, itemId, cartItem.quantity() + 1, "Updated your cart.");
            case "dec" -> cartItem.quantity() != null && cartItem.quantity() <= 1
                ? removeItem(updateContext, userSession, itemId)
                : mutateCart(updateContext, userSession, itemId, cartItem.quantity() - 1, "Updated your cart.");
            case "remove" -> removeItem(updateContext, userSession, itemId);
            default -> unavailableAction(updateContext, "Unknown cart item action.");
        };
    }

    private BotApiMethod<?> mutateCart(
        BotUpdateContext updateContext,
        UserSession userSession,
        Long itemId,
        Integer quantity,
        String successHint
    ) {
        try {
            CartDto updatedCart = cartIntegrationService.updateItemQuantity(
                updateContext.getUserId(),
                itemId,
                new UpdateCartItemQuantityRequest(quantity)
            );
            UserSession cartSession = userSessionService.transitionTo(
                userSession,
                updateContext.getChatId(),
                UserState.VIEWING_CART,
                "callback:cart:update"
            );
            return showCart(updateContext, cartSession, updatedCart, successHint);
        } catch (BackendApiException ex) {
            if (isRecoverableMutationFailure(ex)) {
                return recoverLatestCart(updateContext, userSession, "callback:cart:update");
            }
            return sendOrEdit(updateContext, new BotView(ex.getMessage(), telegramMessageFactory.mainMenuKeyboard()));
        }
    }

    private BotApiMethod<?> removeItem(BotUpdateContext updateContext, UserSession userSession, Long itemId) {
        try {
            CartDto updatedCart = cartIntegrationService.removeItem(updateContext.getUserId(), itemId);
            UserSession cartSession = userSessionService.transitionTo(
                userSession,
                updateContext.getChatId(),
                UserState.VIEWING_CART,
                "callback:cart:remove"
            );
            return showCart(updateContext, cartSession, updatedCart, "Removed the item from your cart.");
        } catch (BackendApiException ex) {
            if (isRecoverableMutationFailure(ex)) {
                return recoverLatestCart(updateContext, userSession, "callback:cart:remove");
            }
            return sendOrEdit(updateContext, new BotView(ex.getMessage(), telegramMessageFactory.mainMenuKeyboard()));
        }
    }

    private BotApiMethod<?> recoverLatestCart(BotUpdateContext updateContext, UserSession userSession, String source) {
        try {
            UserSession cartSession = userSessionService.transitionTo(
                userSession,
                updateContext.getChatId(),
                UserState.VIEWING_CART,
                source
            );
            CartDto latestCart = cartIntegrationService.getCart(updateContext.getUserId());
            return showCart(updateContext, cartSession, latestCart, RECOVERY_MESSAGE);
        } catch (BackendAuthenticationRequiredException | BackendApiException refreshEx) {
            return sendOrEdit(updateContext, new BotView(refreshEx.getMessage(), telegramMessageFactory.mainMenuKeyboard()));
        }
    }

    private BotApiMethod<?> showCart(
        BotUpdateContext updateContext,
        UserSession userSession,
        CartDto cart,
        String hint
    ) {
        BotView cartView = isEmpty(cart)
            ? emptyCartView(hint)
            : new BotView(buildCartText(cart, hint), cartKeyboard(cart));
        return sendOrEdit(updateContext, cartView);
    }

    private BotView emptyCartView(String hint) {
        StringBuilder text = new StringBuilder("Your cart is currently empty.");
        if (StringUtils.hasText(hint)) {
            text.append("\n\n").append(hint);
        } else {
            text.append("\nBrowse the catalog or search to add products.");
        }
        return new BotView(text.toString(), telegramMessageFactory.keyboard(List.of(
            new InlineKeyboardRow(
                telegramMessageFactory.callbackButton("Catalog", "route:catalog"),
                telegramMessageFactory.callbackButton("Search", "route:search")
            ),
            new InlineKeyboardRow(
                telegramMessageFactory.callbackButton("Main menu", "route:main-menu")
            )
        )));
    }

    private String buildCartText(CartDto cart, String hint) {
        StringBuilder text = new StringBuilder("Your cart");
        if (StringUtils.hasText(hint)) {
            text.append("\n").append(hint);
        }

        List<CartItemDto> items = cart.items() == null ? List.of() : cart.items();
        for (CartItemDto item : items) {
            text.append("\n\n- ")
                .append(valueOrDash(item.productName()));
            if (StringUtils.hasText(item.variantName())) {
                text.append(" / ").append(item.variantName());
            }
            text.append(" x").append(item.quantity() == null ? 0 : item.quantity());
            if (StringUtils.hasText(item.sku())) {
                text.append("\n  SKU: ").append(item.sku());
            }
            text.append("\n  Line total: ")
                .append(formatAmount(item.totalAmount()))
                .append(appendCurrency(item.unitPriceCurrency()));
        }

        text.append("\n\nTotal: ")
            .append(formatAmount(cart.totalAmount()))
            .append(appendCurrency(cart.totalCurrency()))
            .append("\nUse the buttons below to adjust quantities or continue to checkout.");

        return text.toString();
    }

    private InlineKeyboardMarkup cartKeyboard(CartDto cart) {
        List<CartItemDto> items = cart.items() == null ? List.of() : cart.items();
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (CartItemDto item : items) {
            rows.add(new InlineKeyboardRow(
                telegramMessageFactory.callbackButton(
                    valueOrDash(item.productName()) + " x" + (item.quantity() == null ? 0 : item.quantity()),
                    ITEM_PREFIX + item.id() + ":info"
                )
            ));
            rows.add(new InlineKeyboardRow(List.of(
                telegramMessageFactory.callbackButton("➖", ITEM_PREFIX + item.id() + ":dec"),
                telegramMessageFactory.callbackButton("➕", ITEM_PREFIX + item.id() + ":inc"),
                telegramMessageFactory.callbackButton("Remove", ITEM_PREFIX + item.id() + ":remove")
            )));
        }
        rows.add(new InlineKeyboardRow(
            telegramMessageFactory.callbackButton("Checkout", "checkout:start"),
            telegramMessageFactory.callbackButton("Refresh", REFRESH_CALLBACK)
        ));
        rows.add(new InlineKeyboardRow(
            telegramMessageFactory.callbackButton("Main menu", "route:main-menu")
        ));
        return telegramMessageFactory.keyboard(rows);
    }

    private boolean isRecoverableMutationFailure(BackendApiException ex) {
        Integer statusCode = ex.getStatusCode();
        return "INSUFFICIENT_STOCK".equals(ex.getErrorCode())
            || "OUT_OF_STOCK".equals(ex.getErrorCode())
            || statusCode != null && (statusCode == 404 || statusCode == 409 || statusCode == 422 || statusCode >= 500);
    }

    private boolean isEmpty(CartDto cart) {
        return cart == null || cart.items() == null || cart.items().isEmpty();
    }

    private CartItemDto findItem(CartDto cart, Long itemId) {
        if (cart == null || cart.items() == null) {
            return null;
        }
        return cart.items().stream()
            .filter(item -> itemId.equals(item.id()))
            .findFirst()
            .orElse(null);
    }

    private BotApiMethod<?> unavailableAction(BotUpdateContext updateContext, String message) {
        return telegramMessageFactory.callbackNotice(updateContext.callbackQueryId().orElseThrow(), message);
    }

    private boolean tryAcquireCartMutation(BotUpdateContext updateContext, String callbackData) {
        boolean acquired = interactionThrottlingService.tryAcquireCartMutation(updateContext.getUserId(), callbackData);
        if (!acquired) {
            securityAuditService.logReplayRejected("cart-mutation", updateContext.getUserId(), updateContext.getChatId());
        }
        return acquired;
    }

    private BotApiMethod<?> sendOrEdit(BotUpdateContext updateContext, BotView botView) {
        Integer messageId = updateContext.messageId().orElse(null);
        if (updateContext.callbackQueryId().isPresent() && messageId != null) {
            return telegramMessageFactory.editMessage(updateContext.getChatId(), messageId, botView);
        }
        return telegramMessageFactory.message(updateContext.getChatId(), botView);
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

    private String valueOrDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    private Long safeParseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
