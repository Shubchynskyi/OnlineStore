package com.onlinestore.telegrambot.routing;

import com.onlinestore.telegrambot.session.UserSession;
import com.onlinestore.telegrambot.session.UserSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;

@Component
@RequiredArgsConstructor
public class TextMessageRouter {

    private final UserStateMachine userStateMachine;
    private final UserSessionService userSessionService;
    private final com.onlinestore.telegrambot.support.TelegramMessageFactory telegramMessageFactory;

    public BotApiMethod<?> route(BotUpdateContext updateContext, UserSession userSession) {
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
                "Free-text input is supported only after /search, /order, and future checkout/AI flows."
            ));
    }
}
