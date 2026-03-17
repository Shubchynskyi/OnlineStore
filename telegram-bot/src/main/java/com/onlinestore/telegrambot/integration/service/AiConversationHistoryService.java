package com.onlinestore.telegrambot.integration.service;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.integration.dto.assistant.OpenAiChatMessage;
import com.onlinestore.telegrambot.integration.dto.assistant.OpenAiChatResponse;
import com.onlinestore.telegrambot.session.UserSession;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiConversationHistoryService {

    public static final String HISTORY_ATTRIBUTE = "assistantConversationHistory";
    public static final String TOTAL_TOKENS_ATTRIBUTE = "assistantTotalTokens";
    public static final String PROMPT_TOKENS_ATTRIBUTE = "assistantPromptTokens";
    public static final String COMPLETION_TOKENS_ATTRIBUTE = "assistantCompletionTokens";

    private static final TypeReference<List<OpenAiChatMessage>> HISTORY_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final BotProperties botProperties;

    public List<OpenAiChatMessage> readHistory(UserSession userSession) {
        String serializedHistory = userSession.getAttributes().get(HISTORY_ATTRIBUTE);
        if (!StringUtils.hasText(serializedHistory)) {
            return List.of();
        }

        try {
            return objectMapper.readValue(serializedHistory, HISTORY_TYPE);
        } catch (Exception ex) {
            log.warn(
                "Assistant conversation history could not be parsed. userId={}, attribute={}",
                userSession.getUserId(),
                HISTORY_ATTRIBUTE,
                ex
            );
            return List.of();
        }
    }

    public Map<String, String> appendExchange(
        UserSession userSession,
        String userMessage,
        String assistantMessage,
        OpenAiChatResponse.Usage usage
    ) {
        List<OpenAiChatMessage> updatedHistory = new ArrayList<>(readHistory(userSession));
        updatedHistory.add(OpenAiChatMessage.user(userMessage));
        updatedHistory.add(OpenAiChatMessage.assistant(assistantMessage));

        int maxHistoryMessages = botProperties.getAiAssistant().getMaxHistoryMessages();
        int fromIndex = Math.max(0, updatedHistory.size() - maxHistoryMessages);
        List<OpenAiChatMessage> trimmedHistory = updatedHistory.subList(fromIndex, updatedHistory.size());

        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        attributes.put(HISTORY_ATTRIBUTE, serializeHistory(trimmedHistory));
        attributes.put(TOTAL_TOKENS_ATTRIBUTE, Integer.toString(totalTokens(userSession) + usageValue(usage, UsageValue.TOTAL)));
        attributes.put(PROMPT_TOKENS_ATTRIBUTE, Integer.toString(promptTokens(userSession) + usageValue(usage, UsageValue.PROMPT)));
        attributes.put(
            COMPLETION_TOKENS_ATTRIBUTE,
            Integer.toString(completionTokens(userSession) + usageValue(usage, UsageValue.COMPLETION))
        );
        return attributes;
    }

    public Map<String, String> clearConversation() {
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        attributes.put(HISTORY_ATTRIBUTE, null);
        attributes.put(TOTAL_TOKENS_ATTRIBUTE, null);
        attributes.put(PROMPT_TOKENS_ATTRIBUTE, null);
        attributes.put(COMPLETION_TOKENS_ATTRIBUTE, null);
        return attributes;
    }

    public int totalTokens(UserSession userSession) {
        return parseIntegerAttribute(userSession, TOTAL_TOKENS_ATTRIBUTE);
    }

    public int promptTokens(UserSession userSession) {
        return parseIntegerAttribute(userSession, PROMPT_TOKENS_ATTRIBUTE);
    }

    public int completionTokens(UserSession userSession) {
        return parseIntegerAttribute(userSession, COMPLETION_TOKENS_ATTRIBUTE);
    }

    private int parseIntegerAttribute(UserSession userSession, String attributeKey) {
        String value = userSession.getAttributes().get(attributeKey);
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            log.warn(
                "Assistant numeric attribute could not be parsed. userId={}, attributeKey={}, value={}",
                userSession.getUserId(),
                attributeKey,
                value,
                ex
            );
            return 0;
        }
    }

    private String serializeHistory(List<OpenAiChatMessage> history) {
        try {
            return objectMapper.writeValueAsString(history);
        } catch (Exception ex) {
            throw new IllegalStateException("Assistant conversation history could not be serialized.", ex);
        }
    }

    private int usageValue(OpenAiChatResponse.Usage usage, UsageValue usageValue) {
        if (usage == null) {
            throw new IllegalArgumentException("Assistant usage metadata is required for conversation persistence.");
        }

        return switch (usageValue) {
            case PROMPT -> requireNonNegative("promptTokens", usage.promptTokens());
            case COMPLETION -> requireNonNegative("completionTokens", usage.completionTokens());
            case TOTAL -> requirePositive("totalTokens", usage.totalTokens());
        };
    }

    private int requireNonNegative(String fieldName, Integer value) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException("Assistant usage field '" + fieldName + "' must be present and non-negative.");
        }
        return value;
    }

    private int requirePositive(String fieldName, Integer value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("Assistant usage field '" + fieldName + "' must be present and positive.");
        }
        return value;
    }

    private enum UsageValue {
        PROMPT,
        COMPLETION,
        TOTAL
    }
}
