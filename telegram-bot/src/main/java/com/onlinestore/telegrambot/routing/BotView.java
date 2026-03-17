package com.onlinestore.telegrambot.routing;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

public record BotView(
    String text,
    InlineKeyboardMarkup keyboard
) {
}
