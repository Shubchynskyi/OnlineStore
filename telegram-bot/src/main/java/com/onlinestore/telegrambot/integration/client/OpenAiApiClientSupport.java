package com.onlinestore.telegrambot.integration.client;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.integration.AiAssistantException;
import java.time.Duration;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiApiClientSupport {

    private final BotProperties botProperties;

    public <T> T execute(String operation, Supplier<T> requestSupplier) {
        return executeInternal(operation, requestSupplier, botProperties.getAiAssistant().getRetry().getMaxAttempts());
    }

    private <T> T executeInternal(String operation, Supplier<T> requestSupplier, int maxAttempts) {
        BotProperties.Retry retrySettings = botProperties.getAiAssistant().getRetry();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return requestSupplier.get();
            } catch (RestClientResponseException ex) {
                if (!isRetryable(ex) || attempt == maxAttempts) {
                    throw toAiAssistantException(operation, ex);
                }
                backoff(retrySettings.getBackoff(), attempt);
            } catch (ResourceAccessException ex) {
                if (attempt == maxAttempts) {
                    log.warn(
                        "OpenAI API connectivity failure. operation={}, attempt={}, maxAttempts={}, cause={}",
                        operation,
                        attempt,
                        maxAttempts,
                        mostSpecificMessage(ex),
                        ex
                    );
                    throw new AiAssistantException(
                        operation,
                        botProperties.getAiAssistant().getFallbackMessage(),
                        null,
                        ex
                    );
                }
                backoff(retrySettings.getBackoff(), attempt);
            } catch (RestClientException ex) {
                log.warn(
                    "OpenAI API client failure. operation={}, cause={}",
                    operation,
                    mostSpecificMessage(ex),
                    ex
                );
                throw new AiAssistantException(
                    operation,
                    botProperties.getAiAssistant().getFallbackMessage(),
                    null,
                    ex
                );
            }
        }

        throw new IllegalStateException("OpenAI retry loop exited unexpectedly.");
    }

    private boolean isRetryable(RestClientResponseException ex) {
        int statusCode = ex.getStatusCode().value();
        return statusCode == 408
            || statusCode == 429
            || statusCode == 500
            || statusCode == 502
            || statusCode == 503
            || statusCode == 504;
    }

    private AiAssistantException toAiAssistantException(String operation, RestClientResponseException ex) {
        String responseBody = ex.getResponseBodyAsString();
        log.warn(
            "OpenAI API response failure. operation={}, statusCode={}, responseBody={}",
            operation,
            ex.getStatusCode().value(),
            truncate(responseBody),
            ex
        );
        return new AiAssistantException(
            operation,
            botProperties.getAiAssistant().getFallbackMessage(),
            ex.getStatusCode().value(),
            ex
        );
    }

    private void backoff(Duration backoff, int attempt) {
        long delayMillis = Math.max(0L, backoff.toMillis()) * attempt;
        if (delayMillis == 0L) {
            return;
        }
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiAssistantException(
                "assistant.retry",
                botProperties.getAiAssistant().getFallbackMessage(),
                null,
                ex
            );
        }
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.length() <= 300 ? value : value.substring(0, 300) + "...";
    }

    private String mostSpecificMessage(Exception ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : ex.getMessage();
    }
}
