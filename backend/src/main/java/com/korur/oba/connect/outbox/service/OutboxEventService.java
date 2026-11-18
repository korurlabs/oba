package com.korur.oba.connect.outbox.service;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.outbox.dto.OutboxCreateDTO;
import com.korur.oba.connect.outbox.entity.OutboxEvent;

/**
 * Write operations for outbox events.
 *
 * <p>Follows CQRS pattern — read operations are in {@link OutboxEventQueryService}.</p>
 */
public interface OutboxEventService {

    OutboxEvent create(OutboxCreateDTO dto);

    void createAll(List<OutboxCreateDTO> outboxEvents);

    OutboxEvent claimEventAtomically(UUID id, long currentVersion);

    OutboxEvent save(OutboxEvent outboxEvent);
}
