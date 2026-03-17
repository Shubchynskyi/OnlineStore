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
public class CoreCommandRouter {

    private final UserSessionService userSessionService;
    private final TelegramMessageFactory telegramMessageFactory;
    private final MainMenuRouteResponseService mainMenuRouteResponseService;

    public BotApiMethod<?> route(BotUpdateContext updateContext, UserSession userSession) {
        String command = updateContext.command()
            .orElseThrow(() -> new IllegalArgumentException("Command routing requires a normalized command"));

        UserState targetState = switch (command) {
            case "start" -> UserState.MAIN_MENU;
            case "catalog" -> UserState.BROWSING_CATALOG;
            case "search" -> UserState.SEARCHING;
            case "cart" -> UserState.VIEWING_CART;
            case "order" -> UserState.TRACKING_ORDER;
            default -> null;
        };

        if (targetState == null) {
            return telegramMessageFactory.menuMessage(
                updateContext.getChatId(),
                "Unknown command. Supported commands are /start, /catalog, /search, /cart, and /order."
            );
        }

        userSessionService.transitionTo(
            userSession,
            updateContext.getChatId(),
            targetState,
            "/" + command
        );

        String responseText = mainMenuRouteResponseService.responseForRoute(command, updateContext.getUserId());
        return telegramMessageFactory.menuMessage(updateContext.getChatId(), responseText);
    }
}
