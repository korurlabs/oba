package com.korur.oba.connect.outbox.service;

// 1. Framework-level
import org.springframework.data.domain.Pageable;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.connect.outbox.entity.OutboxEvent;

/**
 * Read operations for outbox events.
 *
 * <p>Follows CQRS pattern — all read-only operations are in this interface,
 * all write operations are in {@link OutboxEventService}.</p>
 */
public interface OutboxEventQueryService {

    /**
     * Returns events ready for processing (ENQUEUED with expired or null nextAttemptAt).
     */
    List<OutboxEvent> getReadyEventsForProcessing(Pageable pageable);

    /**
     * Returns a fresh copy of the event from the database, falling back to the provided one.
     */
    OutboxEvent getFreshEvent(OutboxEvent event);
}
