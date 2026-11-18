package com.korur.oba.connect.outbox.repository;

// 1. Framework-level
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.korur.oba.connect.outbox.entity.OutboxEvent;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Picks next batch of events ready for processing.
     */
    @Query("""
            SELECT o FROM OutboxEvent o
            WHERE o.outboxStatus = 'ENQUEUED'
                AND (o.nextAttemptAt IS NULL OR o.nextAttemptAt <= :now)
            ORDER BY o.createdAt ASC
            """)
    List<OutboxEvent> findTopEnqueued(@Param("now") Instant now, Pageable pageable);

    /**
     * Marks an outbox event as PROCESSING using optimistic locking.
     *
     * @return 1 if the event was successfully claimed, 0 if another worker claimed it first.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE OutboxEvent o
            SET o.outboxStatus = 'PROCESSING',
                o.attempts = o.attempts + 1,
                o.version = o.version + 1
            WHERE o.id = :id
              AND o.version = :version
              AND o.outboxStatus = 'ENQUEUED'
            """)
    int markAsProcessingOptimistically(
            @Param("id") UUID id,
            @Param("version") long version);
}
