package com.onlinestore.telegrambot.integration.dto.assistant;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record OpenAiChatRequest(
    String model,
    List<OpenAiChatMessage> messages,
    @JsonProperty("max_completion_tokens") Integer maxCompletionTokens,
    Double temperature
) {
}
