package com.onlinestore.common.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private OutboxPublisher outboxPublisher;

    @BeforeEach
    void setUp() {
        var transactionTemplate = new TransactionTemplate(new NoOpTransactionManager());
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        outboxPublisher = new OutboxPublisher(
            outboxEventRepository,
            rabbitTemplate,
            new ObjectMapper(),
            transactionTemplate
        );
        ReflectionTestUtils.setField(outboxPublisher, "batchSize", 20);
        ReflectionTestUtils.setField(outboxPublisher, "maxAttempts", 5);
        ReflectionTestUtils.setField(outboxPublisher, "initialBackoffSeconds", 2L);
        ReflectionTestUtils.setField(outboxPublisher, "maxBackoffSeconds", 60L);
        ReflectionTestUtils.setField(outboxPublisher, "processingTimeoutSeconds", 120L);
    }

    @Test
    void claimBatchShouldMarkClaimedEventsAsProcessing() {
        var pendingEvent = new OutboxEvent();
        pendingEvent.setId(1L);
        pendingEvent.setStatus(OutboxEventStatus.PENDING);
        pendingEvent.setAttemptCount(0);
        var staleProcessingEvent = new OutboxEvent();
        staleProcessingEvent.setId(2L);
        staleProcessingEvent.setStatus(OutboxEventStatus.PROCESSING);
        staleProcessingEvent.setAttemptCount(3);
        when(outboxEventRepository.claimBatchForUpdate(
            eq(OutboxEventStatus.PENDING.name()),
            any(),
            eq(OutboxEventStatus.PROCESSING.name()),
            any(),
            eq(20)
        )).thenReturn(List.of(pendingEvent, staleProcessingEvent));

        List<OutboxEvent> claimedEvents = outboxPublisher.claimBatch();

        assertEquals(2, claimedEvents.size());
        assertEquals(OutboxEventStatus.PROCESSING, pendingEvent.getStatus());
        assertEquals(1, pendingEvent.getAttemptCount());
        assertNotNull(pendingEvent.getLastAttemptAt());
        assertNotNull(pendingEvent.getNextAttemptAt());
        assertEquals(OutboxEventStatus.PROCESSING, staleProcessingEvent.getStatus());
        assertEquals(4, staleProcessingEvent.getAttemptCount());
    }

    @Test
    void publishClaimedEventShouldMarkEventAsPublished() {
        var claimedEvent = createClaimedEvent(10L, 1);
        when(outboxEventRepository.markPublishedIfOwned(
            eq(10L),
            eq(OutboxEventStatus.PROCESSING),
            eq(1),
            eq(claimedEvent.getLastAttemptAt()),
            eq(OutboxEventStatus.PUBLISHED),
            any(Instant.class)
        )).thenReturn(1);

        outboxPublisher.publishClaimedEvent(claimedEvent);

        verify(rabbitTemplate).convertAndSend(
            eq("order.events"),
            eq("order.created"),
            org.mockito.ArgumentMatchers.any(Object.class)
        );
        verify(outboxEventRepository).markPublishedIfOwned(
            eq(10L),
            eq(OutboxEventStatus.PROCESSING),
            eq(1),
            eq(claimedEvent.getLastAttemptAt()),
            eq(OutboxEventStatus.PUBLISHED),
            any(Instant.class)
        );
    }

    @Test
    void publishClaimedEventShouldRequeueOnFailureBeforeMaxAttempts() {
        var claimedEvent = createClaimedEvent(20L, 2);
        when(outboxEventRepository.markRetryPendingIfOwned(
            eq(20L),
            eq(OutboxEventStatus.PROCESSING),
            eq(2),
            eq(claimedEvent.getLastAttemptAt()),
            eq(OutboxEventStatus.PENDING),
            any(Instant.class),
            any(String.class)
        )).thenReturn(1);
        org.mockito.Mockito.doThrow(new RuntimeException("rabbit down"))
            .when(rabbitTemplate)
            .convertAndSend(
                any(String.class),
                any(String.class),
                org.mockito.ArgumentMatchers.any(Object.class)
            );

        outboxPublisher.publishClaimedEvent(claimedEvent);

        verify(outboxEventRepository).markRetryPendingIfOwned(
            eq(20L),
            eq(OutboxEventStatus.PROCESSING),
            eq(2),
            eq(claimedEvent.getLastAttemptAt()),
            eq(OutboxEventStatus.PENDING),
            any(Instant.class),
            any(String.class)
        );
    }

    @Test
    void publishClaimedEventShouldMarkFailedAfterMaxAttempts() {
        var claimedEvent = createClaimedEvent(30L, 5);
        when(outboxEventRepository.markFailedIfOwned(
            eq(30L),
            eq(OutboxEventStatus.PROCESSING),
            eq(5),
            eq(claimedEvent.getLastAttemptAt()),
            eq(OutboxEventStatus.FAILED),
            any(String.class)
        )).thenReturn(1);
        org.mockito.Mockito.doThrow(new RuntimeException("rabbit down"))
            .when(rabbitTemplate)
            .convertAndSend(
                any(String.class),
                any(String.class),
                org.mockito.ArgumentMatchers.any(Object.class)
            );

        outboxPublisher.publishClaimedEvent(claimedEvent);

        verify(outboxEventRepository).markFailedIfOwned(
            eq(30L),
            eq(OutboxEventStatus.PROCESSING),
            eq(5),
            eq(claimedEvent.getLastAttemptAt()),
            eq(OutboxEventStatus.FAILED),
            any(String.class)
        );
    }

    private OutboxEvent createClaimedEvent(Long id, int attemptCount) {
        var event = new OutboxEvent();
        event.setId(id);
        event.setStatus(OutboxEventStatus.PROCESSING);
        event.setAttemptCount(attemptCount);
        event.setExchangeName("order.events");
        event.setRoutingKey("order.created");
        event.setPayload("{\"orderId\":100}");
        event.setNextAttemptAt(Instant.now());
        return event;
    }

    private static final class NoOpTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            // no-op for unit tests
        }

        @Override
        public void rollback(TransactionStatus status) {
            // no-op for unit tests
        }
    }
}
