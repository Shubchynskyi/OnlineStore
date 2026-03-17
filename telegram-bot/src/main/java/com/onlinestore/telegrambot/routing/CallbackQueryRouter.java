package com.onlinestore.telegrambot.routing;

import com.onlinestore.telegrambot.session.UserSession;
import com.onlinestore.telegrambot.session.UserSessionService;
import com.onlinestore.telegrambot.session.UserState;
import com.onlinestore.telegrambot.support.TelegramMessageFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;

@Component
@RequiredArgsConstructor
public class CallbackQueryRouter {

    private static final String ROUTE_PREFIX = "route:";

    private final UserStateMachine userStateMachine;
    private final UserSessionService userSessionService;
    private final TelegramMessageFactory telegramMessageFactory;
    private final MainMenuRouteResponseService mainMenuRouteResponseService;

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

        String responseText = mainMenuRouteResponseService.responseForRoute(route, updateContext.getUserId());
        Integer messageId = updateContext.messageId().orElse(null);
        if (messageId == null) {
            return telegramMessageFactory.callbackNotice(
                updateContext.callbackQueryId().orElseThrow(),
                responseText
            );
        }

        return telegramMessageFactory.editMenuMessage(
            updateContext.getChatId(),
            messageId,
            responseText
        );
    }
}
