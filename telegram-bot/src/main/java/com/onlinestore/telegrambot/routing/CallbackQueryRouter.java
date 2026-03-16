package com.onlinestore.telegrambot.routing;

import com.onlinestore.telegrambot.session.UserSession;
import com.onlinestore.telegrambot.session.UserSessionService;
import com.onlinestore.telegrambot.session.UserState;
import com.onlinestore.telegrambot.support.TelegramMessageFactory;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;

@Component
@RequiredArgsConstructor
public class CallbackQueryRouter {

    private static final String ROUTE_PREFIX = "route:";

    private static final Map<String, String> ROUTE_MESSAGES = Map.of(
        "main-menu", "Back at the main menu.",
        "catalog", "Catalog routing is active. Product catalog integration lands in T-003/T-004.",
        "search", "Search mode is active. Send a product name or keywords.",
        "cart", "Cart routing is active. Cart integration lands in T-003/T-005.",
        "order", "Order status mode is active. Send the order number or tracking reference."
    );

    private final UserStateMachine userStateMachine;
    private final UserSessionService userSessionService;
    private final TelegramMessageFactory telegramMessageFactory;

    public BotApiMethod<?> route(BotUpdateContext updateContext, UserSession userSession) {
        String callbackData = updateContext.callbackData().orElse("");
        if (!callbackData.startsWith(ROUTE_PREFIX)) {
            return telegramMessageFactory.callbackNotice(
                updateContext.callbackQueryId().orElseThrow(),
                "This action is not available yet."
            );
        }

        String route = callbackData.substring(ROUTE_PREFIX.length());
        UserState nextState = userStateMachine.resolveRoute(route).orElse(null);
        if (nextState == null) {
            return telegramMessageFactory.callbackNotice(
                updateContext.callbackQueryId().orElseThrow(),
                "Unknown action."
            );
        }

        userSessionService.transitionTo(
            userSession,
            updateContext.getChatId(),
            nextState,
            "callback:" + route
        );

        Integer messageId = updateContext.messageId().orElse(null);
        if (messageId == null) {
            return telegramMessageFactory.callbackNotice(
                updateContext.callbackQueryId().orElseThrow(),
                ROUTE_MESSAGES.get(route)
            );
        }

        return telegramMessageFactory.editMenuMessage(
            updateContext.getChatId(),
            messageId,
            ROUTE_MESSAGES.get(route)
        );
    }
}
