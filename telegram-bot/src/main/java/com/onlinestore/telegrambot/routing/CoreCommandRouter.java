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
    private final CatalogBrowserService catalogBrowserService;
    private final SearchFlowService searchFlowService;

    public BotApiMethod<?> route(BotUpdateContext updateContext, UserSession userSession) {
        String command = updateContext.command()
            .orElseThrow(() -> new IllegalArgumentException("Command routing requires a normalized command"));

        return switch (command) {
            case "start" -> routeToMenu(updateContext, userSession, UserState.MAIN_MENU, "/" + command, command);
            case "catalog" -> catalogBrowserService.openCatalog(updateContext, userSession, "/" + command);
            case "search" -> searchFlowService.openPrompt(updateContext, userSession, "/" + command);
            case "cart" -> routeToMenu(updateContext, userSession, UserState.VIEWING_CART, "/" + command, command);
            case "order" -> routeToMenu(updateContext, userSession, UserState.TRACKING_ORDER, "/" + command, command);
            default -> telegramMessageFactory.menuMessage(
                updateContext.getChatId(),
                "Unknown command. Supported commands are /start, /catalog, /search, /cart, and /order."
            );
        };
    }

    private BotApiMethod<?> routeToMenu(
        BotUpdateContext updateContext,
        UserSession userSession,
        UserState targetState,
        String lastCommand,
        String route
    ) {
        userSessionService.transitionTo(userSession, updateContext.getChatId(), targetState, lastCommand);
        String responseText = mainMenuRouteResponseService.responseForRoute(route, updateContext.getUserId());
        return telegramMessageFactory.menuMessage(updateContext.getChatId(), responseText);
    }
}
