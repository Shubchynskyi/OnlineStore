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
public class CoreCommandRouter {

    private static final Map<String, CommandDefinition> COMMANDS = Map.of(
        "start", new CommandDefinition(
            UserState.MAIN_MENU,
            "Welcome to the OnlineStore bot. The transport, routing, and dialog state core is active."
        ),
        "catalog", new CommandDefinition(
            UserState.BROWSING_CATALOG,
            "Catalog routing is active. Product catalog integration lands in T-003/T-004."
        ),
        "search", new CommandDefinition(
            UserState.SEARCHING,
            "Search mode is active. Send a product name or keywords."
        ),
        "cart", new CommandDefinition(
            UserState.VIEWING_CART,
            "Cart routing is active. Cart integration lands in T-003/T-005."
        ),
        "order", new CommandDefinition(
            UserState.TRACKING_ORDER,
            "Order status mode is active. Send the order number or tracking reference."
        )
    );

    private final UserSessionService userSessionService;
    private final TelegramMessageFactory telegramMessageFactory;

    public BotApiMethod<?> route(BotUpdateContext updateContext, UserSession userSession) {
        String command = updateContext.command()
            .orElseThrow(() -> new IllegalArgumentException("Command routing requires a normalized command"));

        CommandDefinition commandDefinition = COMMANDS.get(command);
        if (commandDefinition == null) {
            return telegramMessageFactory.menuMessage(
                updateContext.getChatId(),
                "Unknown command. Supported commands are /start, /catalog, /search, /cart, and /order."
            );
        }

        userSessionService.transitionTo(
            userSession,
            updateContext.getChatId(),
            commandDefinition.targetState(),
            "/" + command
        );

        return telegramMessageFactory.menuMessage(updateContext.getChatId(), commandDefinition.responseText());
    }

    private record CommandDefinition(UserState targetState, String responseText) {
    }
}
