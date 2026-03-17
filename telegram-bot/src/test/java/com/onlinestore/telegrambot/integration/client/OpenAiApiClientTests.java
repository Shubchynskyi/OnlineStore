package com.onlinestore.telegrambot.integration.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.integration.dto.assistant.OpenAiChatMessage;
import com.onlinestore.telegrambot.integration.dto.assistant.OpenAiChatResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiApiClientTests {

    private MockRestServiceServer mockRestServiceServer;
    private OpenAiApiClient openAiApiClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        mockRestServiceServer = MockRestServiceServer.bindTo(restClientBuilder).build();

        BotProperties botProperties = new BotProperties();
        botProperties.setToken("test-token");
        botProperties.getAiAssistant().setEnabled(true);
        botProperties.getAiAssistant().setApiKey("openai-key");
        botProperties.getAiAssistant().setBaseUrl("https://api.openai.com/v1");
        botProperties.getAiAssistant().setModel("gpt-4o-mini");
        botProperties.getAiAssistant().setMaxCompletionTokens(320);
        botProperties.getAiAssistant().setTemperature(0.2d);
        botProperties.getAiAssistant().getRetry().setMaxAttempts(1);
        botProperties.getAiAssistant().getRetry().setBackoff(Duration.ZERO);

        OpenAiApiClientSupport support = new OpenAiApiClientSupport(botProperties);
        openAiApiClient = new OpenAiApiClient(
            restClientBuilder.baseUrl(botProperties.getAiAssistant().getBaseUrl()).build(),
            support,
            botProperties
        );
    }

    @Test
    void completeChatPostsAuthorizedRequestAndParsesResponse() {
        mockRestServiceServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer openai-key"))
            .andExpect(content().string(containsString("\"model\":\"gpt-4o-mini\"")))
            .andExpect(content().string(containsString("\"max_completion_tokens\":320")))
            .andExpect(content().string(containsString("\"temperature\":0.2")))
            .andExpect(content().string(containsString("\"role\":\"user\"")))
            .andRespond(withSuccess(
                """
                {
                  "choices":[
                    {
                      "message":{"role":"assistant","content":"Try Green Tea for a lighter option."},
                      "finish_reason":"stop"
                    }
                  ],
                  "usage":{"prompt_tokens":72,"completion_tokens":21,"total_tokens":93}
                }
                """,
                MediaType.APPLICATION_JSON
            ));

        OpenAiChatResponse response = openAiApiClient.completeChat(List.of(
            new OpenAiChatMessage("system", "Store context"),
            new OpenAiChatMessage("user", "Recommend a light tea")
        ));

        assertThat(response.choices()).hasSize(1);
        assertThat(response.choices().getFirst().message().content()).isEqualTo("Try Green Tea for a lighter option.");
        assertThat(response.usage().totalTokens()).isEqualTo(93);
        mockRestServiceServer.verify();
    }

    @Test
    void completeChatRejectsResponsesWithoutUsageMetadata() {
        mockRestServiceServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(
                """
                {
                  "choices":[
                    {
                      "message":{"role":"assistant","content":"Here is a suggestion."},
                      "finish_reason":"stop"
                    }
                  ]
                }
                """,
                MediaType.APPLICATION_JSON
            ));

        assertThatThrownBy(() -> openAiApiClient.completeChat(List.of(
            new OpenAiChatMessage("system", "Store context"),
            new OpenAiChatMessage("user", "Recommend tea")
        )))
            .isInstanceOf(com.onlinestore.telegrambot.integration.AiAssistantException.class)
            .hasMessage("The AI assistant is temporarily unavailable. Please use /search or /catalog and try again later.");

        mockRestServiceServer.verify();
    }
}
