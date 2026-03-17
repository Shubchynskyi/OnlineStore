package com.onlinestore.telegrambot.integration.client;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.integration.AiAssistantException;
import com.onlinestore.telegrambot.integration.dto.assistant.OpenAiChatMessage;
import com.onlinestore.telegrambot.integration.dto.assistant.OpenAiChatRequest;
import com.onlinestore.telegrambot.integration.dto.assistant.OpenAiChatResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class OpenAiApiClient {

    private final RestClient openAiRestClient;
    private final OpenAiApiClientSupport openAiApiClientSupport;
    private final BotProperties botProperties;

    public OpenAiChatResponse completeChat(List<OpenAiChatMessage> messages) {
        return openAiApiClientSupport.execute("assistant.chatCompletion", () -> {
            OpenAiChatResponse response = openAiRestClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + botProperties.getAiAssistant().getApiKey())
                .body(new OpenAiChatRequest(
                    botProperties.getAiAssistant().getModel(),
                    messages,
                    botProperties.getAiAssistant().getMaxCompletionTokens(),
                    botProperties.getAiAssistant().getTemperature()
                ))
                .retrieve()
                .body(OpenAiChatResponse.class);

            if (response == null) {
                throw new AiAssistantException(
                    "assistant.chatCompletion",
                    botProperties.getAiAssistant().getFallbackMessage(),
                    null,
                    new IllegalStateException("OpenAI chat completion response body was empty.")
                );
            }
            validateUsage(response);
            return response;
        });
    }

    private void validateUsage(OpenAiChatResponse response) {
        OpenAiChatResponse.Usage usage = response.usage();
        if (usage == null
            || usage.promptTokens() == null
            || usage.completionTokens() == null
            || usage.totalTokens() == null
            || usage.promptTokens() < 0
            || usage.completionTokens() < 0
            || usage.totalTokens() <= 0) {
            throw new AiAssistantException(
                "assistant.chatCompletion",
                botProperties.getAiAssistant().getFallbackMessage(),
                null,
                new IllegalStateException("OpenAI chat completion response omitted valid usage metadata.")
            );
        }
    }
}
