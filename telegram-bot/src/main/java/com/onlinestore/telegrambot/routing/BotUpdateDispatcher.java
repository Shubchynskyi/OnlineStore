package com.onlinestore.telegrambot.routing;

import com.onlinestore.telegrambot.session.UserSession;
import com.onlinestore.telegrambot.session.UserSessionService;
import com.onlinestore.telegrambot.support.TelegramMessageFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class BotUpdateDispatcher {

    private final UserSessionService userSessionService;
    private final CoreCommandRouter coreCommandRouter;
    private final CallbackQueryRouter callbackQueryRouter;
    private final TextMessageRouter textMessageRouter;
    private final TelegramMessageFactory telegramMessageFactory;

    public BotApiMethod<?> dispatch(Update update) {
        BotUpdateContext updateContext = BotUpdateContext.from(update).orElse(null);
        if (updateContext == null) {
            return null;
        }

        UserSession userSession = userSessionService.getOrCreate(updateContext.getUserId(), updateContext.getChatId());

        if (updateContext.command().isPresent()) {
            return coreCommandRouter.route(updateContext, userSession);
        }

        if (updateContext.callbackQueryId().isPresent()) {
            return callbackQueryRouter.route(updateContext, userSession);
        }

        if (updateContext.messageText().isPresent()) {
            return textMessageRouter.route(updateContext, userSession);
        }

        return telegramMessageFactory.menuMessage(
            updateContext.getChatId(),
            "Only text commands, callback actions, and stateful text input are supported right now."
        );
    }
}
