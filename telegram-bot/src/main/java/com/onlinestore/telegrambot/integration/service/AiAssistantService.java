package com.onlinestore.telegrambot.integration.service;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.integration.AiAssistantException;
import com.onlinestore.telegrambot.integration.client.OpenAiApiClient;
import com.onlinestore.telegrambot.integration.dto.assistant.OpenAiChatMessage;
import com.onlinestore.telegrambot.integration.dto.assistant.OpenAiChatResponse;
import com.onlinestore.telegrambot.session.UserSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AiAssistantService {

    private final OpenAiApiClient openAiApiClient;
    private final AiStoreContextService aiStoreContextService;
    private final AiConversationHistoryService aiConversationHistoryService;
    private final BotProperties botProperties;

    public AiAssistantReply answer(UserSession userSession, String userMessage) {
        String normalizedMessage = userMessage == null ? "" : userMessage.trim();
        if (!StringUtils.hasText(normalizedMessage)) {
            return new AiAssistantReply("Please send a product question or recommendation request.", Map.of());
        }

        if (normalizedMessage.length() > botProperties.getAiAssistant().getMaxUserMessageCharacters()) {
            return new AiAssistantReply(
                "Please keep the assistant message under "
                    + botProperties.getAiAssistant().getMaxUserMessageCharacters()
                    + " characters so the bot can build a focused product context.",
                Map.of()
            );
        }

        if (!botProperties.getAiAssistant().isEnabled()) {
            return new AiAssistantReply(botProperties.getAiAssistant().getFallbackMessage(), Map.of());
        }

        if (aiConversationHistoryService.totalTokens(userSession) >= botProperties.getAiAssistant().getMaxSessionTokens()) {
            return new AiAssistantReply(botProperties.getAiAssistant().getTokenBudgetMessage(), Map.of());
        }

        OpenAiChatResponse response = openAiApiClient.completeChat(buildRequestMessages(userSession, normalizedMessage));
        String assistantMessage = extractAssistantMessage(response);

        return new AiAssistantReply(
            assistantMessage,
            aiConversationHistoryService.appendExchange(userSession, normalizedMessage, assistantMessage, response.usage())
        );
    }

    public boolean hasConversation(UserSession userSession) {
        return !aiConversationHistoryService.readHistory(userSession).isEmpty();
    }

    public Map<String, String> clearConversationAttributes() {
        return aiConversationHistoryService.clearConversation();
    }

    private List<OpenAiChatMessage> buildRequestMessages(UserSession userSession, String userMessage) {
        List<OpenAiChatMessage> messages = new ArrayList<>();
        messages.add(OpenAiChatMessage.system(systemPrompt()));
        messages.add(OpenAiChatMessage.system(aiStoreContextService.buildContext(userMessage)));
        messages.addAll(aiConversationHistoryService.readHistory(userSession));
        messages.add(OpenAiChatMessage.user(userMessage));
        return messages;
    }

    private String systemPrompt() {
        return """
            You are the OnlineStore Telegram assistant.
            Help customers discover products sold by OnlineStore and keep replies concise for Telegram.
            Only use verified information from the provided live store context when you mention products, prices, stock, categories, or availability.
            If the live context does not answer the question, say so clearly and suggest using /catalog or /search.
            Do not invent discounts, product specs, order states, delivery policies, or payment results.
            When relevant, guide the customer to /cart for checkout actions or /order for order lookup instead of claiming to perform those actions yourself.
            """;
    }

    private String extractAssistantMessage(OpenAiChatResponse response) {
        if (response == null || response.choices() == null) {
            throw emptyAssistantResponse();
        }

        return response.choices().stream()
            .map(OpenAiChatResponse.Choice::message)
            .filter(message -> message != null && StringUtils.hasText(message.content()))
            .map(OpenAiChatMessage::content)
            .findFirst()
            .orElseThrow(this::emptyAssistantResponse);
    }

    private AiAssistantException emptyAssistantResponse() {
        return new AiAssistantException(
            "assistant.emptyResponse",
            botProperties.getAiAssistant().getFallbackMessage(),
            null,
            new IllegalStateException("OpenAI response did not contain an assistant message.")
        );
    }

    public record AiAssistantReply(
        String message,
        Map<String, String> sessionAttributes
    ) {
    }
}
