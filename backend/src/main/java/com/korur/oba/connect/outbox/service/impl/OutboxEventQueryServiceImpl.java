package com.korur.oba.connect.outbox.service.impl;

// 1. Framework-level
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Instant;
import java.util.List;

// 4. Internal
import com.korur.oba.connect.outbox.entity.OutboxEvent;
import com.korur.oba.connect.outbox.repository.OutboxEventRepository;
import com.korur.oba.connect.outbox.service.OutboxEventQueryService;

/**
 * Implements read-only operations for outbox event queries.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OutboxEventQueryServiceImpl implements OutboxEventQueryService {

    private final OutboxEventRepository outboxEventRepository;

    @Override
    public List<OutboxEvent> getReadyEventsForProcessing(Pageable pageable) {
        return outboxEventRepository.findTopEnqueued(Instant.now(), pageable);
    }

    @Override
    public OutboxEvent getFreshEvent(OutboxEvent event) {
        return outboxEventRepository.findById(event.getId()).orElse(event);
    }
}
