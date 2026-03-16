package com.onlinestore.telegrambot.support;

import java.util.List;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

@Component
public class TelegramMessageFactory {

    public SendMessage menuMessage(Long chatId, String text) {
        return SendMessage.builder()
            .chatId(chatId.toString())
            .text(text)
            .replyMarkup(mainMenuKeyboard())
            .build();
    }

    public EditMessageText editMenuMessage(Long chatId, Integer messageId, String text) {
        return EditMessageText.builder()
            .chatId(chatId.toString())
            .messageId(messageId)
            .text(text)
            .replyMarkup(mainMenuKeyboard())
            .build();
    }

    public AnswerCallbackQuery callbackNotice(String callbackQueryId, String text) {
        return AnswerCallbackQuery.builder()
            .callbackQueryId(callbackQueryId)
            .text(text)
            .showAlert(false)
            .build();
    }

    public BotApiMethod<?> processingFailure(Long chatId) {
        if (chatId == null) {
            return null;
        }
        return menuMessage(chatId, "Something went wrong while handling your request. Please try again.");
    }

    public BotApiMethod<?> callbackProcessingFailure(String callbackQueryId) {
        if (callbackQueryId == null) {
            return null;
        }
        return callbackNotice(callbackQueryId, "The action failed. Please try again.");
    }

    private InlineKeyboardMarkup mainMenuKeyboard() {
        return InlineKeyboardMarkup.builder()
            .keyboard(List.of(
                new InlineKeyboardRow(
                    callbackButton("Catalog", "route:catalog"),
                    callbackButton("Search", "route:search")
                ),
                new InlineKeyboardRow(
                    callbackButton("Cart", "route:cart"),
                    callbackButton("Orders", "route:order")
                ),
                new InlineKeyboardRow(
                    callbackButton("Main menu", "route:main-menu")
                )
            ))
            .build();
    }

    private InlineKeyboardButton callbackButton(String text, String callbackData) {
        return InlineKeyboardButton.builder()
            .text(text)
            .callbackData(callbackData)
            .build();
    }
}
