package com.onlinestore.telegrambot.integration.client;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.integration.BackendApiException;
import com.onlinestore.telegrambot.integration.auth.BackendServiceAccessTokenProvider;
import com.onlinestore.telegrambot.integration.dto.BackendApiError;
import java.time.Duration;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class BackendApiClientSupport {

    private static final String TEMPORARY_UNAVAILABLE_MESSAGE =
        "The store service is temporarily unavailable. Please try again later.";
    private static final String REQUEST_REJECTED_MESSAGE =
        "The store could not process that request right now.";
    private static final String AUTHORIZATION_REQUIRED_MESSAGE =
        "The linked store account is not authorized for this action right now.";
    private static final String RATE_LIMITED_MESSAGE =
        "The store is receiving too many requests right now. Please try again shortly.";
    private static final String OUT_OF_STOCK_MESSAGE =
        "The requested item is out of stock right now.";

    private final BotProperties botProperties;
    private final ObjectMapper objectMapper;
    private final BackendServiceAccessTokenProvider backendServiceAccessTokenProvider;

    public void applyOptionalServiceAuthentication(HttpHeaders headers) {
        if (backendServiceAccessTokenProvider.isEnabled()) {
            headers.setBearerAuth(backendServiceAccessTokenProvider.getAccessToken());
        }
    }

    public <T> T execute(String operation, Supplier<T> requestSupplier) {
        return executeInternal(operation, requestSupplier, botProperties.getBackendApi().getRetry().getMaxAttempts());
    }

    public <T> T executeWithoutRetry(String operation, Supplier<T> requestSupplier) {
        return executeInternal(operation, requestSupplier, 1);
    }

    private <T> T executeInternal(String operation, Supplier<T> requestSupplier, int maxAttempts) {
        BotProperties.Retry retrySettings = botProperties.getBackendApi().getRetry();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return requestSupplier.get();
            } catch (RestClientResponseException ex) {
                if (!isRetryable(ex) || attempt == maxAttempts) {
                    throw toBackendApiException(operation, ex);
                }
                backoff(retrySettings.getBackoff(), attempt);
            } catch (ResourceAccessException ex) {
                if (attempt == maxAttempts) {
                    log.warn(
                        "Backend API connectivity failure. operation={}, attempt={}, maxAttempts={}, cause={}",
                        operation,
                        attempt,
                        maxAttempts,
                        mostSpecificMessage(ex),
                        ex
                    );
                    throw new BackendApiException(
                        operation,
                        TEMPORARY_UNAVAILABLE_MESSAGE,
                        null,
                        null,
                        null,
                        ex
                    );
                }
                backoff(retrySettings.getBackoff(), attempt);
            } catch (RestClientException ex) {
                log.warn(
                    "Backend API client failure. operation={}, cause={}",
                    operation,
                    mostSpecificMessage(ex),
                    ex
                );
                throw new BackendApiException(
                    operation,
                    TEMPORARY_UNAVAILABLE_MESSAGE,
                    null,
                    null,
                    null,
                    ex
                );
            }
        }

        throw new IllegalStateException("Backend API retry loop exited unexpectedly.");
    }

    private boolean isRetryable(RestClientResponseException ex) {
        int statusCode = ex.getStatusCode().value();
        return statusCode == 408
            || statusCode == 429
            || statusCode == 502
            || statusCode == 503
            || statusCode == 504;
    }

    private BackendApiException toBackendApiException(String operation, RestClientResponseException ex) {
        BackendApiError backendApiError = parseError(ex.getResponseBodyAsByteArray());
        String errorCode = backendApiError != null ? backendApiError.error() : null;
        String backendMessage = backendApiError != null && StringUtils.hasText(backendApiError.message())
            ? backendApiError.message()
            : mostSpecificMessage(ex);
        String sanitizedMessage = sanitizedMessage(ex.getStatusCode().value(), errorCode);

        log.warn(
            "Backend API response failure. operation={}, statusCode={}, errorCode={}, path={}, backendMessage={}",
            operation,
            ex.getStatusCode().value(),
            errorCode,
            backendApiError != null ? backendApiError.path() : null,
            backendMessage,
            ex
        );

        return new BackendApiException(
            operation,
            sanitizedMessage,
            ex.getStatusCode().value(),
            errorCode,
            backendApiError,
            ex
        );
    }

    private String sanitizedMessage(int statusCode, String errorCode) {
        if ("OUT_OF_STOCK".equals(errorCode)) {
            return OUT_OF_STOCK_MESSAGE;
        }
        return switch (statusCode) {
            case 401, 403 -> AUTHORIZATION_REQUIRED_MESSAGE;
            case 429 -> RATE_LIMITED_MESSAGE;
            case 400, 404, 409, 422 -> REQUEST_REJECTED_MESSAGE;
            case 408, 502, 503, 504 -> TEMPORARY_UNAVAILABLE_MESSAGE;
            default -> statusCode >= 500 ? TEMPORARY_UNAVAILABLE_MESSAGE : REQUEST_REJECTED_MESSAGE;
        };
    }

    private BackendApiError parseError(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            return objectMapper.readValue(body, BackendApiError.class);
        } catch (RuntimeException ignored) {
            return null;
        }
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
            log.warn("Backend API retry backoff interrupted. attempt={}, delayMillis={}", attempt, delayMillis, ex);
            throw new BackendApiException(
                "retry",
                TEMPORARY_UNAVAILABLE_MESSAGE,
                null,
                null,
                null,
                ex
            );
        }
    }

    private String mostSpecificMessage(Exception ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : ex.getMessage();
    }
}
