package com.onlinestore.common.event;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query(
        value = "SELECT * FROM outbox_events "
            + "WHERE ((status = :pendingStatus AND next_attempt_at <= :now) "
            + "OR (status = :processingStatus AND last_attempt_at IS NOT NULL "
            + "AND last_attempt_at <= :processingStaleBefore)) "
            + "ORDER BY id ASC "
            + "LIMIT :batchSize "
            + "FOR UPDATE SKIP LOCKED",
        nativeQuery = true
    )
    List<OutboxEvent> claimBatchForUpdate(
        @Param("pendingStatus") String pendingStatus,
        @Param("now") Instant now,
        @Param("processingStatus") String processingStatus,
        @Param("processingStaleBefore") Instant processingStaleBefore,
        @Param("batchSize") int batchSize
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from OutboxEvent event where event.id = :id")
    Optional<OutboxEvent> findByIdForUpdate(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update OutboxEvent event
        set event.status = :publishedStatus,
            event.publishedAt = :publishedAt,
            event.lastError = null
        where event.id = :eventId
          and event.status = :processingStatus
          and event.attemptCount = :claimedAttemptCount
          and event.lastAttemptAt = :claimedLastAttemptAt
        """)
    int markPublishedIfOwned(
        @Param("eventId") Long eventId,
        @Param("processingStatus") OutboxEventStatus processingStatus,
        @Param("claimedAttemptCount") int claimedAttemptCount,
        @Param("claimedLastAttemptAt") Instant claimedLastAttemptAt,
        @Param("publishedStatus") OutboxEventStatus publishedStatus,
        @Param("publishedAt") Instant publishedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update OutboxEvent event
        set event.status = :pendingStatus,
            event.nextAttemptAt = :nextAttemptAt,
            event.lastError = :lastError
        where event.id = :eventId
          and event.status = :processingStatus
          and event.attemptCount = :claimedAttemptCount
          and event.lastAttemptAt = :claimedLastAttemptAt
        """)
    int markRetryPendingIfOwned(
        @Param("eventId") Long eventId,
        @Param("processingStatus") OutboxEventStatus processingStatus,
        @Param("claimedAttemptCount") int claimedAttemptCount,
        @Param("claimedLastAttemptAt") Instant claimedLastAttemptAt,
        @Param("pendingStatus") OutboxEventStatus pendingStatus,
        @Param("nextAttemptAt") Instant nextAttemptAt,
        @Param("lastError") String lastError
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update OutboxEvent event
        set event.status = :failedStatus,
            event.lastError = :lastError
        where event.id = :eventId
          and event.status = :processingStatus
          and event.attemptCount = :claimedAttemptCount
          and event.lastAttemptAt = :claimedLastAttemptAt
        """)
    int markFailedIfOwned(
        @Param("eventId") Long eventId,
        @Param("processingStatus") OutboxEventStatus processingStatus,
        @Param("claimedAttemptCount") int claimedAttemptCount,
        @Param("claimedLastAttemptAt") Instant claimedLastAttemptAt,
        @Param("failedStatus") OutboxEventStatus failedStatus,
        @Param("lastError") String lastError
    );
}
