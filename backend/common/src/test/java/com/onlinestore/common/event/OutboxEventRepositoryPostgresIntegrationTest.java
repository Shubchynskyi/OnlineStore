package com.onlinestore.common.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    classes = OutboxEventRepositoryPostgresIntegrationTest.TestApplication.class,
    properties = "spring.jpa.hibernate.ddl-auto=create-drop"
)
class OutboxEventRepositoryPostgresIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void claimBatchShouldIncludePendingAndStaleProcessingEvents() {
        Instant now = Instant.now();

        OutboxEvent pending = saveEvent(OutboxEventStatus.PENDING, now.minusSeconds(5), null, 0);
        saveEvent(OutboxEventStatus.PENDING, now.plusSeconds(120), null, 0);
        OutboxEvent staleProcessing = saveEvent(
            OutboxEventStatus.PROCESSING,
            now.minusSeconds(10),
            now.minusSeconds(600),
            3
        );
        saveEvent(
            OutboxEventStatus.PROCESSING,
            now.minusSeconds(10),
            now.minusSeconds(10),
            1
        );

        List<OutboxEvent> claimed = outboxEventRepository.claimBatchForUpdate(
            OutboxEventStatus.PENDING.name(),
            now,
            OutboxEventStatus.PROCESSING.name(),
            now.minusSeconds(120),
            10
        );

        Set<Long> claimedIds = claimed.stream().map(OutboxEvent::getId).collect(java.util.stream.Collectors.toSet());
        assertEquals(2, claimed.size());
        assertTrue(claimedIds.contains(pending.getId()));
        assertTrue(claimedIds.contains(staleProcessing.getId()));
    }

    @Test
    void markPublishedIfOwnedShouldRequireClaimOwnership() {
        Instant claimTimestamp = Instant.now().minusSeconds(60);
        OutboxEvent processingEvent = saveEvent(
            OutboxEventStatus.PROCESSING,
            Instant.now().minusSeconds(10),
            claimTimestamp,
            3
        );

        int updatedWithWrongAttempt = outboxEventRepository.markPublishedIfOwned(
            processingEvent.getId(),
            OutboxEventStatus.PROCESSING,
            2,
            claimTimestamp,
            OutboxEventStatus.PUBLISHED,
            Instant.now()
        );
        int updatedWithWrongTimestamp = outboxEventRepository.markPublishedIfOwned(
            processingEvent.getId(),
            OutboxEventStatus.PROCESSING,
            3,
            claimTimestamp.minusSeconds(30),
            OutboxEventStatus.PUBLISHED,
            Instant.now()
        );
        int updatedWithCorrectClaim = outboxEventRepository.markPublishedIfOwned(
            processingEvent.getId(),
            OutboxEventStatus.PROCESSING,
            3,
            claimTimestamp,
            OutboxEventStatus.PUBLISHED,
            Instant.now()
        );

        assertEquals(0, updatedWithWrongAttempt);
        assertEquals(0, updatedWithWrongTimestamp);
        assertEquals(1, updatedWithCorrectClaim);
        OutboxEvent updatedEvent = outboxEventRepository.findById(processingEvent.getId()).orElseThrow();
        assertEquals(OutboxEventStatus.PUBLISHED, updatedEvent.getStatus());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void claimBatchShouldSkipRowsLockedByAnotherTransaction() throws Exception {
        Long lockedEventId = saveCommittedEvent(OutboxEventStatus.PENDING, Instant.now().minusSeconds(5), null, 0);
        Long unlockedEventId = saveCommittedEvent(OutboxEventStatus.PENDING, Instant.now().minusSeconds(5), null, 0);

        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        Future<?> lockFuture = executorService.submit(() -> {
            var entityManager = entityManagerFactory.createEntityManager();
            var tx = entityManager.getTransaction();
            tx.begin();
            try {
                entityManager.createNativeQuery("SELECT id FROM outbox_events WHERE id = :id FOR UPDATE")
                    .setParameter("id", lockedEventId)
                    .getSingleResult();
                lockAcquired.countDown();
                if (!releaseLock.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release lock");
                }
                tx.commit();
            } catch (Exception ex) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw new RuntimeException(ex);
            } finally {
                entityManager.close();
            }
        });

        assertTrue(lockAcquired.await(5, TimeUnit.SECONDS));

        var claimTemplate = new TransactionTemplate(transactionManager);
        List<OutboxEvent> claimed = claimTemplate.execute(status -> outboxEventRepository.claimBatchForUpdate(
            OutboxEventStatus.PENDING.name(),
            Instant.now(),
            OutboxEventStatus.PROCESSING.name(),
            Instant.now().minusSeconds(120),
            2
        ));

        assertNotNull(claimed);
        Set<Long> claimedIds = claimed.stream().map(OutboxEvent::getId).collect(java.util.stream.Collectors.toSet());
        assertTrue(claimedIds.contains(unlockedEventId));
        assertFalse(claimedIds.contains(lockedEventId));

        releaseLock.countDown();
        lockFuture.get(5, TimeUnit.SECONDS);
        executorService.shutdownNow();
    }

    private OutboxEvent saveEvent(
        OutboxEventStatus status,
        Instant nextAttemptAt,
        Instant lastAttemptAt,
        int attemptCount
    ) {
        OutboxEvent event = new OutboxEvent();
        Instant now = Instant.now();
        event.setEventType("order.created");
        event.setExchangeName("order.events");
        event.setRoutingKey("order.created");
        event.setPayload("{\"orderId\":100}");
        event.setStatus(status);
        event.setAttemptCount(attemptCount);
        event.setNextAttemptAt(nextAttemptAt);
        event.setLastAttemptAt(lastAttemptAt);
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        event.setVersion(0L);
        return outboxEventRepository.saveAndFlush(event);
    }

    private Long saveCommittedEvent(
        OutboxEventStatus status,
        Instant nextAttemptAt,
        Instant lastAttemptAt,
        int attemptCount
    ) {
        var template = new TransactionTemplate(transactionManager);
        return template.execute(transactionStatus -> saveEvent(status, nextAttemptAt, lastAttemptAt, attemptCount).getId());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableJpaRepositories(basePackageClasses = OutboxEventRepository.class)
    static class TestApplication {
    }
}
