package com.onlinestore.telegrambot.routing;

import com.onlinestore.telegrambot.session.UserSession;
import com.onlinestore.telegrambot.session.UserSessionService;
import com.onlinestore.telegrambot.support.InteractionThrottlingService;
import com.onlinestore.telegrambot.support.SecurityAuditService;
import com.onlinestore.telegrambot.support.TelegramMessageFactory;
import com.onlinestore.telegrambot.support.UserInteractionLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class BotUpdateDispatcher {

    private static final String UPDATE_RATE_LIMIT_MESSAGE =
        "You're sending requests too quickly. Please wait a moment and try again.";

    private final UserSessionService userSessionService;
    private final CoreCommandRouter coreCommandRouter;
    private final CallbackQueryRouter callbackQueryRouter;
    private final TextMessageRouter textMessageRouter;
    private final TelegramMessageFactory telegramMessageFactory;
    private final UserInteractionLockService userInteractionLockService;
    private final InteractionThrottlingService interactionThrottlingService;
    private final SecurityAuditService securityAuditService;

    public BotApiMethod<?> dispatch(Update update) {
        BotUpdateContext updateContext = BotUpdateContext.from(update).orElse(null);
        if (updateContext == null) {
            return null;
        }

        InteractionThrottlingService.ThrottleDecision throttleDecision =
            interactionThrottlingService.consumeUserUpdate(updateContext.getUserId());
        if (!throttleDecision.allowed()) {
            securityAuditService.logRateLimitExceeded(
                "user-updates",
                updateContext.getUserId(),
                updateContext.getChatId(),
                throttleDecision.retryAfter()
            );
            return rateLimitedResponse(updateContext);
        }

        return userInteractionLockService.withUserLock(
            updateContext.getUserId(),
            () -> dispatchWithinUserLock(updateContext)
        );
    }

    private BotApiMethod<?> dispatchWithinUserLock(BotUpdateContext updateContext) {
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

    private BotApiMethod<?> rateLimitedResponse(BotUpdateContext updateContext) {
        if (updateContext.callbackQueryId().isPresent()) {
            return telegramMessageFactory.callbackNotice(
                updateContext.callbackQueryId().orElseThrow(),
                UPDATE_RATE_LIMIT_MESSAGE
            );
        }
        return telegramMessageFactory.message(updateContext.getChatId(), UPDATE_RATE_LIMIT_MESSAGE);
    }
}
