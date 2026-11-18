package com.korur.oba.connect.outbox.service;

// 4. Internal
import com.korur.oba.connect.outbox.entity.OutboxEvent;

/**
 * Write operations for outbox dead letter events.
 */
public interface OutboxDeadEventService {

    /**
     * Moves a permanently failed outbox event to the dead letter table.
     * Maps the event to an {@code OutboxDeadEvent}, persists it, and deletes
     * the original from the active outbox table.
     *
     * @param event the outbox event that permanently failed
     * @param ex    the exception from the final attempt
     */
    void moveToDeadLetter(OutboxEvent event, Exception ex);
}
