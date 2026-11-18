package com.korur.oba.apps.kaizen.sprint.scheduler;

// 1. Framework-level
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.sprint.constant.scheduler.SprintRecurrenceSchedulerLogConstant;
import com.korur.oba.apps.kaizen.sprint.processor.SprintRecurrenceProcessor;
import com.korur.oba.apps.kaizen.sprint.repository.SprintSeriesRepository;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.time.BusinessCalendar;

/**
 * Runs the work of every due sprint series: creating the series' next sprint
 * ahead, rolling its active sprint over at the end date, and starting its
 * planned sprint at the start date.
 *
 * Sprint dates in a series fall at 00:00 UTC, so the job runs at
 * 00:00 UTC and retries at 00:15, 00:30 and 00:45 UTC what the first run could
 * not do (a blocked or failed sprint, or a pod that was still starting). Work
 * that becomes due later in the day waits for the next midnight hour. The job
 * also runs once when the application is ready, so a deploy or crash over the
 * whole midnight hour does not delay a rollover by a day.
 */
@Component
@RequiredArgsConstructor
public class SprintRecurrenceScheduler {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(SprintRecurrenceScheduler.class);

    private final SprintSeriesRepository sprintSeriesRepository;
    private final SprintRecurrenceProcessor sprintRecurrenceProcessor;
    private final Clock clock;
    private final LoggingService loggingService;

    /**
     * Runs every due series, each in its own transaction, so one failing
     * series does not stop the others.
     */
    @Scheduled(cron = "${scheduling.sprint-recurrence.cron:0 0,15,30,45 0 * * *}", zone = BusinessCalendar.SCHEDULER_ZONE_ID)
    public void runDueRecurrences() {
        Instant now = clock.instant();
        List<UUID> dueIds = sprintSeriesRepository.findDueIds(now);
        if (dueIds.isEmpty()) {
            return;
        }

        loggingService.info(LOG_PREFIX, LogLayer.SCHEDULER,
                SprintRecurrenceSchedulerLogConstant.SPRINT_RECURRENCE_RUN_STARTED, dueIds.size(), now);

        for (UUID seriesId : dueIds) {
            runOne(seriesId, now);
        }
    }

    /**
     * Catch-up run at application start.
     *
     * An exception here would stop the application from starting, while the
     * next midnight run retries the same work, so it is logged at error level
     * with its stack trace and not re-thrown.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void runOnApplicationReady() {
        try {
            runDueRecurrences();
        } catch (RuntimeException e) {
            loggingService.error(LOG_PREFIX, LogLayer.SCHEDULER,
                    SprintRecurrenceSchedulerLogConstant.SPRINT_RECURRENCE_STARTUP_RUN_FAILED, e);
        }
    }

    /**
     * Runs one series and records a failure as FAILED in a new transaction,
     * after the run's own changes were rolled back. A version conflict with a
     * user's save is recorded with its own message; the next run retries it.
     */
    private void runOne(UUID seriesId, Instant now) {
        try {
            sprintRecurrenceProcessor.runInNewTransaction(seriesId, now);
        } catch (RuntimeException e) {
            // A user saving the sprint mid-run is an expected race, so it is a
            // warning; the series still records it, with a message saying so.
            if (SprintRecurrenceProcessor.isConcurrentUpdate(e)) {
                loggingService.warn(LOG_PREFIX, LogLayer.SCHEDULER,
                        SprintRecurrenceSchedulerLogConstant.SPRINT_RECURRENCE_RUN_CONFLICT, seriesId, e.getMessage());
            } else {
                loggingService.error(LOG_PREFIX, LogLayer.SCHEDULER,
                        SprintRecurrenceSchedulerLogConstant.SPRINT_RECURRENCE_RUN_FAILED, e, seriesId);
            }
            try {
                sprintRecurrenceProcessor.recordFailureInNewTransaction(seriesId, now, e);
            } catch (RuntimeException recordFailure) {
                loggingService.error(LOG_PREFIX, LogLayer.SCHEDULER,
                        SprintRecurrenceSchedulerLogConstant.SPRINT_RECURRENCE_FAILURE_NOT_RECORDED,
                        recordFailure, seriesId);
            }
        }
    }
}
