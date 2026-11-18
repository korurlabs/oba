package com.korur.oba.connect.outbox.processor;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.Map;

import com.korur.oba.connect.outbox.constant.OutboxConstant;
import com.korur.oba.connect.outbox.constant.processor.OutboxProcessorLogConstant;
import com.korur.oba.connect.outbox.entity.OutboxEvent;
import com.korur.oba.connect.outbox.enums.ConnectOutboxPolicy;
import com.korur.oba.connect.outbox.enums.OutboxStatus;
import com.korur.oba.connect.outbox.exception.ExceptionDomainOutboxError;
import com.korur.oba.connect.outbox.handler.OutboxEventHandler;
import com.korur.oba.connect.outbox.service.OutboxDeadEventService;
import com.korur.oba.connect.outbox.service.OutboxEventQueryService;
import com.korur.oba.connect.outbox.service.OutboxEventService;
import com.korur.oba.connect.outbox.util.OutboxEventUtil;
// 4. Internal
import com.korur.oba.dojo.exception.external.InternalProcessException;
import com.korur.oba.dojo.exception.external.ThirdPartyException;
import com.korur.oba.dojo.exception.internal.infrastructure.InternalServerErrorException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Orchestrates processing of individual outbox events.
 */
@Service
@RequiredArgsConstructor
public class OutboxEventProcessor {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(OutboxEventProcessor.class);

    private final LoggingService loggingService;
    private final OutboxEventService outboxEventService;
    private final OutboxEventQueryService outboxEventQueryService;
    private final OutboxDeadEventService outboxDeadEventService;
    private final List<OutboxEventHandler> handlers;

    /**
     * Processes a single event in a new transaction.
     * Claims the event atomically, dispatches to handler, and handles failures.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSingleEventInNewTransaction(OutboxEvent event) {
        loggingService.info(LOG_PREFIX, LogLayer.SCHEDULER,
                OutboxProcessorLogConstant.OUTBOX_PROCESSING_START, event.getId());

        // Atomic claim: only one worker wins
        OutboxEvent claimedEvent = outboxEventService.claimEventAtomically(
                event.getId(), event.getVersion());
        if (claimedEvent == null) {
            loggingService.debug(LOG_PREFIX, LogLayer.SCHEDULER,
                    OutboxProcessorLogConstant.OUTBOX_PROCESSING_ALREADY_CLAIMED, event.getId());
            return;
        }

        try {
            dispatchToHandler(claimedEvent);
            OutboxEventUtil.markAsProcessed(claimedEvent);
            outboxEventService.save(claimedEvent);

            loggingService.info(LOG_PREFIX, LogLayer.SCHEDULER,
                    OutboxProcessorLogConstant.OUTBOX_PROCESSING_SUCCESS, claimedEvent.getId());
        } catch (Exception e) {
            if (e instanceof ThirdPartyException) {
                handleFailure(claimedEvent, e);
            } else if (e instanceof InternalProcessException) {
                handleFailureImmediately(claimedEvent, e);
            } else {
                loggingService.error(LOG_PREFIX, LogLayer.SCHEDULER,
                        OutboxProcessorLogConstant.OUTBOX_PROCESSING_THIRD_PARTY_ERROR,
                        e, claimedEvent.getId());
                handleFailure(claimedEvent, e);
            }
        }
    }

    private void handleFailureImmediately(OutboxEvent event, Exception ex) {
        loggingService.error(LOG_PREFIX, LogLayer.SCHEDULER,
                OutboxProcessorLogConstant.OUTBOX_PROCESSING_FAILED, ex, event.getId());

        try {
            OutboxEvent fresh = outboxEventQueryService.getFreshEvent(event);
            OutboxEventUtil.markAsFailedImmediately(fresh, ex);
            outboxEventService.save(fresh);

            loggingService.info(LOG_PREFIX, LogLayer.SCHEDULER,
                    OutboxProcessorLogConstant.OUTBOX_PROCESSING_MOVED_TO_DEAD_LETTER, fresh.getId());
            outboxDeadEventService.moveToDeadLetter(fresh, ex);
        } catch (Exception saveEx) {
            loggingService.error(LOG_PREFIX, LogLayer.SCHEDULER,
                    OutboxProcessorLogConstant.OUTBOX_PROCESSING_SAVE_FAILED, saveEx, event.getId());
        }
    }

    private void handleFailure(OutboxEvent event, Exception ex) {
        loggingService.error(LOG_PREFIX, LogLayer.SCHEDULER,
                OutboxProcessorLogConstant.OUTBOX_PROCESSING_FAILED, ex, event.getId());

        try {
            OutboxEvent fresh = outboxEventQueryService.getFreshEvent(event);
            ConnectOutboxPolicy policy = ConnectOutboxPolicy.findBySystem(fresh.getConnectOutboxSystem());
            OutboxEventUtil.markAsFailed(fresh, ex, policy);
            outboxEventService.save(fresh);

            if (fresh.getOutboxStatus() == OutboxStatus.ENQUEUED) {
                loggingService.warn(LOG_PREFIX, LogLayer.SCHEDULER,
                        OutboxProcessorLogConstant.OUTBOX_PROCESSING_RE_ENQUEUED,
                        fresh.getId(), fresh.getAttempts(),
                        policy.getMaxAttempts(),
                        fresh.getOutboxEventType(), fresh.getConnectOutboxSystem(),
                        fresh.getNextAttemptAt(), fresh.getPayload());
            } else {
                loggingService.error(LOG_PREFIX, LogLayer.SCHEDULER,
                        OutboxProcessorLogConstant.OUTBOX_PROCESSING_PERMANENTLY_FAILED,
                        fresh.getId(), fresh.getAttempts(),
                        policy.getMaxAttempts(),
                        fresh.getOutboxEventType(), fresh.getConnectOutboxSystem(),
                        fresh.getPayload());

                loggingService.info(LOG_PREFIX, LogLayer.SCHEDULER,
                        OutboxProcessorLogConstant.OUTBOX_PROCESSING_MOVED_TO_DEAD_LETTER, fresh.getId());
                outboxDeadEventService.moveToDeadLetter(fresh, ex);
            }
        } catch (Exception saveEx) {
            loggingService.error(LOG_PREFIX, LogLayer.SCHEDULER,
                    OutboxProcessorLogConstant.OUTBOX_PROCESSING_SAVE_FAILED, saveEx, event.getId());
        }
    }

    private void dispatchToHandler(OutboxEvent event) {
        handlers.stream()
                .filter(h -> h.supports(event.getConnectOutboxSystem()))
                .findFirst()
                .orElseThrow(() -> new InternalServerErrorException(
                        ExceptionDomainOutboxError.INT_NO_HANDLER_FOUND,
                        Map.of(OutboxConstant.FIELD_TARGET_SYSTEM,
                                event.getConnectOutboxSystem().name())))
                .handle(event);
    }
}
