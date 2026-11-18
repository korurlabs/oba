package com.korur.oba.connect.outbox.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 4. Internal
import com.korur.oba.connect.outbox.constant.service.OutboxDeadEventServiceLogConstant;
import com.korur.oba.connect.outbox.entity.OutboxDeadEvent;
import com.korur.oba.connect.outbox.entity.OutboxEvent;
import com.korur.oba.connect.outbox.repository.OutboxDeadEventRepository;
import com.korur.oba.connect.outbox.repository.OutboxEventRepository;
import com.korur.oba.connect.outbox.service.OutboxDeadEventService;
import com.korur.oba.connect.outbox.util.OutboxEventUtil;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Moves permanently failed outbox events to the dead letter table.
 */
@Service
@RequiredArgsConstructor
public class OutboxDeadEventServiceImpl implements OutboxDeadEventService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(OutboxDeadEventServiceImpl.class);

    private final OutboxDeadEventRepository outboxDeadEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final LoggingService loggingService;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void moveToDeadLetter(OutboxEvent event, Exception ex) {
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                OutboxDeadEventServiceLogConstant.DEAD_LETTER_MOVING, event.getId());

        OutboxDeadEvent deadEvent = OutboxEventUtil.toDeadEvent(event);
        deadEvent = outboxDeadEventRepository.save(deadEvent);

        outboxEventRepository.deleteById(event.getId());

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                OutboxDeadEventServiceLogConstant.DEAD_LETTER_MOVED,
                event.getId(), deadEvent.getId());
    }
}
