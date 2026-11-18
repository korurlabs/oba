package com.korur.oba.connect.outbox.handler;

import com.korur.oba.connect.outbox.entity.OutboxEvent;
import com.korur.oba.connect.outbox.enums.ConnectOutboxSystem;

/**
 * Handler interface for processing outbox events.
 * Implementations are dispatched based on the target system.
 */
public interface OutboxEventHandler {

    boolean supports(ConnectOutboxSystem connectOutboxSystem);

    void handle(OutboxEvent event);
}
