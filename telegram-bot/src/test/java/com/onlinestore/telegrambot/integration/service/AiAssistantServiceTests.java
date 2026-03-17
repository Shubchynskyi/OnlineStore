package com.onlinestore.telegrambot.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.integration.client.OpenAiApiClient;
import com.onlinestore.telegrambot.integration.dto.PageResponse;
import com.onlinestore.telegrambot.integration.dto.assistant.OpenAiChatMessage;
import com.onlinestore.telegrambot.integration.dto.assistant.OpenAiChatResponse;
import com.onlinestore.telegrambot.integration.dto.catalog.ProductDto;
import com.onlinestore.telegrambot.integration.dto.catalog.VariantDto;
import com.onlinestore.telegrambot.integration.dto.search.ProductSearchResult;
import com.onlinestore.telegrambot.session.UserSession;
import com.onlinestore.telegrambot.session.UserState;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AiAssistantServiceTests {

    @Mock
    private SearchIntegrationService searchIntegrationService;

    @Mock
    private CatalogIntegrationService catalogIntegrationService;

    @Mock
    private OpenAiApiClient openAiApiClient;

    private BotProperties botProperties;
    private AiConversationHistoryService aiConversationHistoryService;
    private AiAssistantService aiAssistantService;

    @BeforeEach
    void setUp() {
        botProperties = new BotProperties();
        botProperties.setToken("test-token");
        botProperties.getAiAssistant().setEnabled(true);
        botProperties.getAiAssistant().setApiKey("openai-key");
        botProperties.getAiAssistant().setMaxRetrievedProducts(3);
        botProperties.getAiAssistant().setMaxHistoryMessages(6);
        botProperties.getAiAssistant().setMaxSessionTokens(300);

        aiConversationHistoryService = new AiConversationHistoryService(new ObjectMapper(), botProperties);
        AiStoreContextService aiStoreContextService = new AiStoreContextService(
            catalogIntegrationService,
            searchIntegrationService,
            botProperties
        );
        aiAssistantService = new AiAssistantService(
            openAiApiClient,
            aiStoreContextService,
            aiConversationHistoryService,
            botProperties
        );
    }

    @SuppressWarnings("unchecked")
    @Test
    void answerBuildsStoreContextAndUpdatesConversationHistory() {
        when(searchIntegrationService.searchProducts("tea under five dollars")).thenReturn(new PageResponse<>(
            List.of(new ProductSearchResult(
                "1",
                "Budget Green Tea",
                "Loose leaf tea",
                "Tea",
                new BigDecimal("3.90"),
                new BigDecimal("4.20"),
                true,
                List.of(),
                0.98f
            )),
            0,
            5,
            1,
            1,
            true
        ));
        when(catalogIntegrationService.getProducts(any(), eq(0), eq(3))).thenReturn(new PageResponse<>(
            List.of(product(101L, "Budget Green Tea", "budget-green-tea", "Loose leaf tea", "Tea", "tea", new BigDecimal("3.90"))),
            0,
            3,
            1,
            1,
            true
        ));

        ArgumentCaptor<List<OpenAiChatMessage>> requestCaptor = ArgumentCaptor.forClass(List.class);
        when(openAiApiClient.completeChat(requestCaptor.capture())).thenReturn(new OpenAiChatResponse(
            List.of(new OpenAiChatResponse.Choice(
                new OpenAiChatMessage("assistant", "Try Budget Green Tea for a light and affordable option."),
                "stop"
            )),
            new OpenAiChatResponse.Usage(120, 40, 160)
        ));

        UserSession userSession = UserSession.initial(100L, 200L);

        AiAssistantService.AiAssistantReply reply = aiAssistantService.answer(userSession, "tea under five dollars");

        assertThat(reply.message()).contains("Budget Green Tea");
        List<OpenAiChatMessage> requestMessages = requestCaptor.getValue();
        assertThat(requestMessages.getFirst().role()).isEqualTo("system");
        assertThat(requestMessages.get(1).content())
            .contains("Search matches")
            .contains("Catalog matches")
            .contains("Budget Green Tea");
        assertThat(requestMessages.getLast().content()).isEqualTo("tea under five dollars");

        UserSession updatedSession = userSession.toBuilder()
            .attributes(new LinkedHashMap<>(reply.sessionAttributes()))
            .build();

        assertThat(aiConversationHistoryService.readHistory(updatedSession)).hasSize(2);
        assertThat(aiConversationHistoryService.totalTokens(updatedSession)).isEqualTo(160);
    }

    @Test
    void answerReturnsBudgetWarningWithoutCallingOpenAi() {
        botProperties.getAiAssistant().setMaxSessionTokens(100);

        UserSession userSession = UserSession.builder()
            .userId(100L)
            .chatId(200L)
            .state(UserState.CHATTING_WITH_AI)
            .attributes(new LinkedHashMap<>(Map.of("assistantTotalTokens", "100")))
            .updatedAtEpochMillis(1L)
            .build();

        AiAssistantService.AiAssistantReply reply = aiAssistantService.answer(userSession, "one more question");

        assertThat(reply.message()).contains("session token budget");
        verifyNoInteractions(openAiApiClient);
    }

    private ProductDto product(
        Long id,
        String name,
        String slug,
        String description,
        String categoryName,
        String categorySlug,
        BigDecimal price
    ) {
        return new ProductDto(
            id,
            name,
            slug,
            description,
            1L,
            categoryName,
            categorySlug,
            "ACTIVE",
            false,
            List.of(new VariantDto(1L, "sku-" + id, name + " default", price, "USD", null, 5, Map.of(), true)),
            List.of(),
            List.of()
        );
    }
}
