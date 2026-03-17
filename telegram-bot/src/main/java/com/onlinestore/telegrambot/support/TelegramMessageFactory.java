package com.onlinestore.telegrambot.support;

import com.onlinestore.telegrambot.routing.BotView;
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

    public SendMessage message(Long chatId, BotView botView) {
        return message(chatId, botView.text(), botView.keyboard());
    }

    public SendMessage message(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        return SendMessage.builder()
            .chatId(chatId.toString())
            .text(text)
            .replyMarkup(keyboard)
            .build();
    }

    public SendMessage menuMessage(Long chatId, String text) {
        return message(chatId, text, mainMenuKeyboard());
    }

    public EditMessageText editMessage(Long chatId, Integer messageId, BotView botView) {
        return editMessage(chatId, messageId, botView.text(), botView.keyboard());
    }

    public EditMessageText editMessage(Long chatId, Integer messageId, String text, InlineKeyboardMarkup keyboard) {
        return EditMessageText.builder()
            .chatId(chatId.toString())
            .messageId(messageId)
            .text(text)
            .replyMarkup(keyboard)
            .build();
    }

    public EditMessageText editMenuMessage(Long chatId, Integer messageId, String text) {
        return editMessage(chatId, messageId, text, mainMenuKeyboard());
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

    public InlineKeyboardMarkup mainMenuKeyboard() {
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
                    callbackButton("Assistant", "route:assistant"),
                    callbackButton("Main menu", "route:main-menu")
                )
            ))
            .build();
    }

    public InlineKeyboardMarkup keyboard(List<InlineKeyboardRow> rows) {
        return InlineKeyboardMarkup.builder()
            .keyboard(rows)
            .build();
    }

    public InlineKeyboardButton callbackButton(String text, String callbackData) {
        return InlineKeyboardButton.builder()
            .text(text)
            .callbackData(callbackData)
            .build();
    }
}
