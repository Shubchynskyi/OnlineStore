package com.onlinestore.telegrambot.routing;

import java.util.Optional;
import lombok.Getter;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.MaybeInaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@Getter
public final class BotUpdateContext {

    private final Update update;
    private final Long userId;
    private final Long chatId;
    private final Integer messageId;
    private final String messageText;
    private final String callbackQueryId;
    private final String callbackData;
    private final String command;

    private BotUpdateContext(
        Update update,
        Long userId,
        Long chatId,
        Integer messageId,
        String messageText,
        String callbackQueryId,
        String callbackData,
        String command
    ) {
        this.update = update;
        this.userId = userId;
        this.chatId = chatId;
        this.messageId = messageId;
        this.messageText = messageText;
        this.callbackQueryId = callbackQueryId;
        this.callbackData = callbackData;
        this.command = command;
    }

    public static Optional<BotUpdateContext> from(Update update) {
        if (update == null) {
            return Optional.empty();
        }

        if (update.hasCallbackQuery()) {
            return fromCallbackQuery(update, update.getCallbackQuery());
        }

        if (update.hasMessage()) {
            return fromMessage(update, update.getMessage());
        }

        return Optional.empty();
    }

    public Optional<String> command() {
        return Optional.ofNullable(command);
    }

    public Optional<String> messageText() {
        return Optional.ofNullable(messageText);
    }

    public Optional<String> callbackQueryId() {
        return Optional.ofNullable(callbackQueryId);
    }

    public Optional<String> callbackData() {
        return Optional.ofNullable(callbackData);
    }

    public Optional<Integer> messageId() {
        return Optional.ofNullable(messageId);
    }

    private static Optional<BotUpdateContext> fromMessage(Update update, Message message) {
        User from = message.getFrom();
        if (from == null || message.getChat() == null || message.getChatId() == null) {
            return Optional.empty();
        }

        String text = normalizeText(message.getText());
        return Optional.of(new BotUpdateContext(
            update,
            from.getId(),
            message.getChatId(),
            message.getMessageId(),
            text,
            null,
            null,
            extractCommand(text)
        ));
    }

    private static Optional<BotUpdateContext> fromCallbackQuery(Update update, CallbackQuery callbackQuery) {
        MaybeInaccessibleMessage callbackMessage = callbackQuery.getMessage();
        User from = callbackQuery.getFrom();

        if (from == null || callbackMessage == null || callbackMessage.getChatId() == null) {
            return Optional.empty();
        }

        return Optional.of(new BotUpdateContext(
            update,
            from.getId(),
            callbackMessage.getChatId(),
            callbackMessage.getMessageId(),
            null,
            callbackQuery.getId(),
            normalizeText(callbackQuery.getData()),
            null
        ));
    }

    private static String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static String extractCommand(String text) {
        if (!StringUtils.hasText(text) || !text.startsWith("/")) {
            return null;
        }

        String commandToken = text.split("\\s+", 2)[0];
        String normalizedCommand = commandToken.substring(1);
        int botMentionSeparator = normalizedCommand.indexOf('@');
        if (botMentionSeparator >= 0) {
            normalizedCommand = normalizedCommand.substring(0, botMentionSeparator);
        }

        return normalizedCommand.toLowerCase();
    }
}
