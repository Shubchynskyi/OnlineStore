package com.onlinestore.telegrambot.integration.dto.assistant;

public record OpenAiChatMessage(
    String role,
    String content
) {

    public static OpenAiChatMessage system(String content) {
        return new OpenAiChatMessage("system", content);
    }

    public static OpenAiChatMessage user(String content) {
        return new OpenAiChatMessage("user", content);
    }

    public static OpenAiChatMessage assistant(String content) {
        return new OpenAiChatMessage("assistant", content);
    }
}
