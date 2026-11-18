package com.korur.oba.connect.outbox.scheduler;

// 1. Framework-level
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;

import com.korur.oba.connect.outbox.constant.scheduler.OutboxSchedulerLogConstant;
import com.korur.oba.connect.outbox.entity.OutboxEvent;
import com.korur.oba.connect.outbox.processor.OutboxEventProcessor;
import com.korur.oba.connect.outbox.service.OutboxEventQueryService;
// 4. Internal
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Scheduled component that polls for pending outbox events and dispatches them.
 */
@Component
@RequiredArgsConstructor
public class OutboxEventScheduler {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(OutboxEventScheduler.class);

    private final LoggingService loggingService;
    private final OutboxEventQueryService outboxEventQueryService;
    private final OutboxEventProcessor outboxEventProcessor;

    /**
     * Runs on configurable interval (default 30 seconds).
     * Picks up to 100 ready events per cycle.
     */
    @Scheduled(fixedRateString = "${scheduling.outbox.interval:30000}")
    public void processPendingEvents() {
        Pageable pageable = PageRequest.of(0, 100);

        List<OutboxEvent> pendingEvents = outboxEventQueryService.getReadyEventsForProcessing(pageable);

        if (pendingEvents.isEmpty()) {
            return;
        }

        loggingService.info(LOG_PREFIX, LogLayer.SCHEDULER,
                OutboxSchedulerLogConstant.OUTBOX_PROCESSING_BATCH, pendingEvents.size());

        pendingEvents.forEach(outboxEventProcessor::processSingleEventInNewTransaction);
    }
}
