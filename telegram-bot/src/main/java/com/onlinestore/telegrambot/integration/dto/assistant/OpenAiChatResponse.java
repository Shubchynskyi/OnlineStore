package com.onlinestore.telegrambot.integration.dto.assistant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiChatResponse(
    List<Choice> choices,
    Usage usage
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
        OpenAiChatMessage message,
        @JsonProperty("finish_reason") String finishReason
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
        @JsonProperty("prompt_tokens") Integer promptTokens,
        @JsonProperty("completion_tokens") Integer completionTokens,
        @JsonProperty("total_tokens") Integer totalTokens
    ) {
    }
}
