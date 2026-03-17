package com.onlinestore.telegrambot.routing;

import com.onlinestore.telegrambot.integration.BackendAuthenticationRequiredException;
import com.onlinestore.telegrambot.integration.BackendApiException;
import com.onlinestore.telegrambot.integration.dto.orders.OrderDto;
import com.onlinestore.telegrambot.integration.service.OrdersIntegrationService;
import com.onlinestore.telegrambot.session.UserSession;
import com.onlinestore.telegrambot.session.UserSessionService;
import com.onlinestore.telegrambot.session.UserState;
import com.onlinestore.telegrambot.support.TelegramMessageFactory;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;

@Component
@RequiredArgsConstructor
public class TextMessageRouter {

    private final UserStateMachine userStateMachine;
    private final UserSessionService userSessionService;
    private final TelegramMessageFactory telegramMessageFactory;
    private final OrdersIntegrationService ordersIntegrationService;
    private final SearchFlowService searchFlowService;
    private final CheckoutFlowService checkoutFlowService;
    private final AiAssistantFlowService aiAssistantFlowService;

    public BotApiMethod<?> route(BotUpdateContext updateContext, UserSession userSession) {
        if (userSession.getState() == UserState.SEARCHING) {
            return searchFlowService.handleSearchInput(updateContext, userSession);
        }

        if (userSession.getState() == UserState.TRACKING_ORDER) {
            return handleOrderLookup(updateContext, userSession);
        }

        if (userSession.getState() == UserState.ENTERING_ADDRESS) {
            return checkoutFlowService.handleAddressInput(updateContext, userSession);
        }

        if (userSession.getState() == UserState.CONFIRMING_ORDER) {
            return telegramMessageFactory.menuMessage(
                updateContext.getChatId(),
                "Use the inline confirmation buttons to place the order, change the address, or reopen the cart."
            );
        }

        if (userSession.getState() == UserState.CHATTING_WITH_AI) {
            return aiAssistantFlowService.handleAssistantInput(updateContext, userSession);
        }

        return userStateMachine.resolveTextInputKey(userSession.getState())
            .map(attributeKey -> {
                userSessionService.rememberInput(
                    userSession,
                    updateContext.getChatId(),
                    attributeKey,
                    updateContext.messageText().orElse("")
                );
                return telegramMessageFactory.menuMessage(
                    updateContext.getChatId(),
                    userStateMachine.acknowledgmentFor(userSession.getState())
                );
            })
            .orElseGet(() -> telegramMessageFactory.menuMessage(
                updateContext.getChatId(),
                "Free-text input is supported after /search, /order, /assistant, or during checkout address entry."
            ));
    }

    private BotApiMethod<?> handleOrderLookup(BotUpdateContext updateContext, UserSession userSession) {
        String orderReference = updateContext.messageText().orElse("");
        userSessionService.rememberInput(userSession, updateContext.getChatId(), "orderReference", orderReference);

        try {
            OrderDto order = ordersIntegrationService.lookupOrder(updateContext.getUserId(), orderReference);
            return telegramMessageFactory.menuMessage(
                updateContext.getChatId(),
                buildOrderLookupMessage(order)
            );
        } catch (BackendAuthenticationRequiredException ex) {
            return telegramMessageFactory.menuMessage(updateContext.getChatId(), ex.getMessage());
        } catch (BackendApiException ex) {
            return telegramMessageFactory.menuMessage(updateContext.getChatId(), ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return telegramMessageFactory.menuMessage(
                updateContext.getChatId(),
                "Order lookup expects a numeric backend order id, for example 123."
            );
        }
    }

    private String buildOrderLookupMessage(OrderDto order) {
        return "Order #" + order.id()
            + "\nStatus: " + valueOrDash(order.status())
            + "\nTotal: " + formatAmount(order.totalAmount()) + " " + valueOrDash(order.totalCurrency())
            + "\nCreated at: " + order.createdAt();
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
