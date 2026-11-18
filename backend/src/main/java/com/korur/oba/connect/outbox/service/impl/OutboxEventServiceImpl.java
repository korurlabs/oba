package com.korur.oba.connect.outbox.service.impl;

// 1. Framework-level
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

// 2. Third-party
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.outbox.constant.OutboxConstant;
import com.korur.oba.connect.outbox.constant.service.OutboxServiceLogConstant;
import com.korur.oba.connect.outbox.dto.OutboxCreateDTO;
import com.korur.oba.connect.outbox.entity.OutboxEvent;
import com.korur.oba.connect.outbox.enums.OutboxStatus;
import com.korur.oba.connect.outbox.exception.ExceptionDomainOutboxError;
import com.korur.oba.connect.outbox.repository.OutboxEventRepository;
import com.korur.oba.connect.outbox.service.OutboxEventService;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.util.JsonUtil;

/**
 * Write operations for outbox events.
 *
 * <p>Read operations have been moved to {@link com.korur.oba.connect.outbox.service.impl.OutboxEventQueryServiceImpl}
 * following the CQRS pattern.</p>
 */
@Service
@RequiredArgsConstructor
public class OutboxEventServiceImpl implements OutboxEventService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(OutboxEventServiceImpl.class);

    private final OutboxEventRepository outboxEventRepository;
    private final LoggingService loggingService;

    @Override
    public OutboxEvent create(OutboxCreateDTO dto) {
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                OutboxServiceLogConstant.OUTBOX_EVENT_CREATING,
                dto.outboxEventType(), dto.connectOutboxSystem());

        String payload = JsonUtil.toJson(dto.payload());
        OutboxEvent outboxEvent = OutboxEvent.builder()
                .payload(payload)
                .attempts(0)
                .outboxEventType(dto.outboxEventType())
                .connectOutboxSystem(dto.connectOutboxSystem())
                .outboxStatus(OutboxStatus.ENQUEUED)
                .build();
        outboxEvent = outboxEventRepository.save(outboxEvent);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                OutboxServiceLogConstant.OUTBOX_EVENT_CREATED, outboxEvent.getId());

        return outboxEvent;
    }

    @Override
    @Async("outboxEventExecutor")
    public void createAll(List<OutboxCreateDTO> outboxEventsDTO) {
        List<OutboxEvent> outboxEvents = new ArrayList<>();

        outboxEventsDTO.forEach(dto -> {
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                    OutboxServiceLogConstant.OUTBOX_EVENT_CREATING,
                    dto.outboxEventType(), dto.connectOutboxSystem());

            String payload = JsonUtil.toJson(dto.payload());
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .payload(payload)
                    .attempts(0)
                    .outboxEventType(dto.outboxEventType())
                    .connectOutboxSystem(dto.connectOutboxSystem())
                    .outboxStatus(OutboxStatus.ENQUEUED)
                    .build();
            outboxEvents.add(outboxEvent);
        });

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                OutboxServiceLogConstant.OUTBOX_EVENT_BATCH_CREATED, outboxEvents.size());

        outboxEventRepository.saveAll(outboxEvents);
    }

    @Override
    @Transactional
    public OutboxEvent claimEventAtomically(UUID id, long currentVersion) {
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                OutboxServiceLogConstant.OUTBOX_EVENT_CLAIMING, id);

        int updated = outboxEventRepository.markAsProcessingOptimistically(id, currentVersion);
        if (updated == 0) {
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                    OutboxServiceLogConstant.OUTBOX_EVENT_ALREADY_CLAIMED, id);
            return null;
        }

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                OutboxServiceLogConstant.OUTBOX_EVENT_CLAIMED, id);

        return outboxEventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ExceptionDomainOutboxError.NOT_EVENT_FOUND,
                        Map.of(OutboxConstant.FIELD_OUTBOX_EVENT_ID, id.toString())));
    }

    @Override
    public OutboxEvent save(OutboxEvent outboxEvent) {
        return outboxEventRepository.save(outboxEvent);
    }
}
