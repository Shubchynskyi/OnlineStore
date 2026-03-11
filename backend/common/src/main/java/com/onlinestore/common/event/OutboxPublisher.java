package com.onlinestore.common.event;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "onlinestore.outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final int MAX_ERROR_LENGTH = 1000;
    private static final int MAX_BACKOFF_MULTIPLIER = 8;

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    @Qualifier("outboxRequiresNewTransactionTemplate")
    private final TransactionTemplate outboxRequiresNewTransactionTemplate;

    @Value("${onlinestore.outbox.publisher.batch-size:50}")
    private int batchSize;

    @Value("${onlinestore.outbox.publisher.max-attempts:10}")
    private int maxAttempts;

    @Value("${onlinestore.outbox.publisher.initial-backoff-seconds:5}")
    private long initialBackoffSeconds;

    @Value("${onlinestore.outbox.publisher.max-backoff-seconds:300}")
    private long maxBackoffSeconds;

    @Value("${onlinestore.outbox.publisher.processing-timeout-seconds:120}")
    private long processingTimeoutSeconds;

    @Scheduled(fixedDelayString = "${onlinestore.outbox.publisher.fixed-delay-ms:2000}")
    public void publishPendingEvents() {
        while (true) {
            List<OutboxEvent> claimedEvents = claimBatch();
            if (claimedEvents.isEmpty()) {
                return;
            }

            for (OutboxEvent event : claimedEvents) {
                publishClaimedEvent(event);
            }

            if (claimedEvents.size() < resolveBatchSize()) {
                return;
            }
        }
    }

    List<OutboxEvent> claimBatch() {
        List<OutboxEvent> events = outboxRequiresNewTransactionTemplate.execute(status -> {
            Instant now = Instant.now();
            List<OutboxEvent> claimedEvents = outboxEventRepository.claimBatchForUpdate(
                OutboxEventStatus.PENDING.name(),
                now,
                OutboxEventStatus.PROCESSING.name(),
                now.minusSeconds(resolveProcessingTimeoutSeconds()),
                resolveBatchSize()
            );

            for (OutboxEvent event : claimedEvents) {
                event.setStatus(OutboxEventStatus.PROCESSING);
                event.setAttemptCount(event.getAttemptCount() + 1);
                event.setLastAttemptAt(now);
                event.setNextAttemptAt(now);
                event.setLastError(null);
            }
            return claimedEvents;
        });
        if (events == null) {
            return List.of();
        }
        return events;
    }

    void publishClaimedEvent(OutboxEvent event) {
        try {
            Object payload = deserializePayload(event.getPayload());
            rabbitTemplate.convertAndSend(event.getExchangeName(), event.getRoutingKey(), payload);
            markPublished(event);
        } catch (RuntimeException ex) {
            markForRetry(event, ex);
        }
    }

    void markPublished(OutboxEvent claimedEvent) {
        outboxRequiresNewTransactionTemplate.executeWithoutResult(status -> {
            int updatedRows = outboxEventRepository.markPublishedIfOwned(
                claimedEvent.getId(),
                OutboxEventStatus.PROCESSING,
                claimedEvent.getAttemptCount(),
                claimedEvent.getLastAttemptAt(),
                OutboxEventStatus.PUBLISHED,
                Instant.now()
            );
            if (updatedRows == 0) {
                log.info("Skip markPublished due claim ownership mismatch: eventId={}, attempt={}",
                    claimedEvent.getId(),
                    claimedEvent.getAttemptCount());
            }
        });
    }

    void markForRetry(OutboxEvent claimedEvent, RuntimeException exception) {
        outboxRequiresNewTransactionTemplate.executeWithoutResult(status -> {
            String errorMessage = compactErrorMessage(exception);
            int updatedRows;
            if (claimedEvent.getAttemptCount() >= resolveMaxAttempts()) {
                updatedRows = outboxEventRepository.markFailedIfOwned(
                    claimedEvent.getId(),
                    OutboxEventStatus.PROCESSING,
                    claimedEvent.getAttemptCount(),
                    claimedEvent.getLastAttemptAt(),
                    OutboxEventStatus.FAILED,
                    errorMessage
                );
            } else {
                updatedRows = outboxEventRepository.markRetryPendingIfOwned(
                    claimedEvent.getId(),
                    OutboxEventStatus.PROCESSING,
                    claimedEvent.getAttemptCount(),
                    claimedEvent.getLastAttemptAt(),
                    OutboxEventStatus.PENDING,
                    Instant.now().plusSeconds(calculateBackoffSeconds(claimedEvent.getAttemptCount())),
                    errorMessage
                );
            }
            if (updatedRows == 0) {
                log.info("Skip markForRetry due claim ownership mismatch: eventId={}, attempt={}",
                    claimedEvent.getId(),
                    claimedEvent.getAttemptCount());
                return;
            }

            log.warn("Outbox event publish failed, scheduled retry: eventId={}, attempt={}/{}",
                claimedEvent.getId(),
                claimedEvent.getAttemptCount(),
                resolveMaxAttempts(),
                exception);
        });
    }

    private Object deserializePayload(String payload) {
        try {
            return objectMapper.readValue(payload, Object.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to deserialize outbox payload", ex);
        }
    }

    private String compactErrorMessage(Throwable exception) {
        String message = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        if (message.length() <= MAX_ERROR_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_LENGTH);
    }

    private long calculateBackoffSeconds(int attemptCount) {
        long safeInitialBackoff = Math.max(1L, initialBackoffSeconds);
        int multiplierExponent = Math.max(0, Math.min(attemptCount - 1, MAX_BACKOFF_MULTIPLIER));
        long multiplier = 1L << multiplierExponent;
        long backoff;
        try {
            backoff = Math.multiplyExact(safeInitialBackoff, multiplier);
        } catch (ArithmeticException ex) {
            backoff = resolveMaxBackoffSeconds();
        }
        return Math.min(Math.max(backoff, safeInitialBackoff), resolveMaxBackoffSeconds());
    }

    private int resolveBatchSize() {
        return Math.max(batchSize, 1);
    }

    private int resolveMaxAttempts() {
        return Math.max(maxAttempts, 1);
    }

    private long resolveMaxBackoffSeconds() {
        return Math.max(maxBackoffSeconds, 1L);
    }

    private long resolveProcessingTimeoutSeconds() {
        return Math.max(processingTimeoutSeconds, 1L);
    }
}
