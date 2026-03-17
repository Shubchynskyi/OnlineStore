package com.onlinestore.telegrambot.routing;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.integration.AiAssistantException;
import com.onlinestore.telegrambot.integration.service.AiAssistantService;
import com.onlinestore.telegrambot.session.UserSession;
import com.onlinestore.telegrambot.session.UserSessionService;
import com.onlinestore.telegrambot.session.UserState;
import com.onlinestore.telegrambot.support.TelegramMessageFactory;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

@Service
@RequiredArgsConstructor
public class AiAssistantFlowService {

    private static final String CALLBACK_PREFIX = "assistant:";
    private static final String CLEAR_CALLBACK = CALLBACK_PREFIX + "clear";

    private final AiAssistantService aiAssistantService;
    private final UserSessionService userSessionService;
    private final TelegramMessageFactory telegramMessageFactory;
    private final BotProperties botProperties;

    public BotApiMethod<?> openPrompt(BotUpdateContext updateContext, UserSession userSession, String source) {
        if (!botProperties.getAiAssistant().isEnabled()) {
            return telegramMessageFactory.menuMessage(
                updateContext.getChatId(),
                botProperties.getAiAssistant().getFallbackMessage()
            );
        }

        userSessionService.transitionTo(userSession, updateContext.getChatId(), UserState.CHATTING_WITH_AI, source);
        return sendOrEdit(updateContext, promptView(aiAssistantService.hasConversation(userSession), null));
    }

    public BotApiMethod<?> handleAssistantInput(BotUpdateContext updateContext, UserSession userSession) {
        try {
            AiAssistantService.AiAssistantReply reply = aiAssistantService.answer(
                userSession,
                updateContext.messageText().orElse("")
            );
            boolean hasConversation = aiAssistantService.hasConversation(userSession) || !reply.sessionAttributes().isEmpty();
            if (!reply.sessionAttributes().isEmpty()) {
                userSessionService.rememberInputs(userSession, updateContext.getChatId(), reply.sessionAttributes());
            }
            return telegramMessageFactory.message(updateContext.getChatId(), new BotView(reply.message(), assistantKeyboard(hasConversation)));
        } catch (AiAssistantException ex) {
            return telegramMessageFactory.message(
                updateContext.getChatId(),
                new BotView(ex.getMessage(), assistantKeyboard(aiAssistantService.hasConversation(userSession)))
            );
        }
    }

    public BotApiMethod<?> handleCallback(BotUpdateContext updateContext, UserSession userSession) {
        String callbackData = updateContext.callbackData().orElse("");
        if (CLEAR_CALLBACK.equals(callbackData)) {
            userSessionService.rememberInputs(
                userSession,
                updateContext.getChatId(),
                aiAssistantService.clearConversationAttributes()
            );
            return sendOrEdit(updateContext, promptView(false, "Conversation cleared. Send a new product question."));
        }

        return telegramMessageFactory.callbackNotice(
            updateContext.callbackQueryId().orElseThrow(),
            "Unknown assistant action."
        );
    }

    private BotView promptView(boolean hasHistory, String hint) {
        StringBuilder text = new StringBuilder(
            "Assistant mode is active.\nAsk for product recommendations, comparisons, or help choosing something from the store."
        );
        if (hasHistory) {
            text.append("\nYour previous assistant conversation is still available.");
        }
        text.append("\nThe assistant uses live catalog and search context when available.");
        if (hint != null && !hint.isBlank()) {
            text.append("\n\n").append(hint);
        }
        return new BotView(text.toString(), assistantKeyboard(hasHistory));
    }

    private InlineKeyboardMarkup assistantKeyboard(boolean hasHistory) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        if (hasHistory) {
            rows.add(new InlineKeyboardRow(
                telegramMessageFactory.callbackButton("Clear chat", CLEAR_CALLBACK)
            ));
        }
        rows.add(new InlineKeyboardRow(
            telegramMessageFactory.callbackButton("Main menu", "route:main-menu")
        ));
        return telegramMessageFactory.keyboard(rows);
    }

    private BotApiMethod<?> sendOrEdit(BotUpdateContext updateContext, BotView botView) {
        Integer messageId = updateContext.messageId().orElse(null);
        if (updateContext.callbackQueryId().isPresent() && messageId != null) {
            return telegramMessageFactory.editMessage(updateContext.getChatId(), messageId, botView);
        }
        return telegramMessageFactory.message(updateContext.getChatId(), botView);
    }
}
