package com.korur.oba.apps.kazan.sprint.scheduler;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.sprint.entity.Sprint;
import com.korur.oba.apps.kazan.sprint.processor.SprintRecurrenceProcessor;
import com.korur.oba.apps.kazan.sprint.repository.SprintSeriesRepository;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class SprintRecurrenceSchedulerUnitTest {

    private static final Instant NOW = Instant.parse("2026-01-08T00:00:00Z");

    @Mock
    private SprintSeriesRepository sprintSeriesRepository;

    @Mock
    private SprintRecurrenceProcessor sprintRecurrenceProcessor;

    @Mock
    private LoggingService loggingService;

    private SprintRecurrenceScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new SprintRecurrenceScheduler(sprintSeriesRepository, sprintRecurrenceProcessor,
                Clock.fixed(NOW, ZoneOffset.UTC), loggingService);
    }

    @Nested
    class RunDueRecurrences {

        @Test
        void nothingDue_runsNothing() {
            when(sprintSeriesRepository.findDueIds(NOW)).thenReturn(List.of());

            scheduler.runDueRecurrences();

            verify(sprintRecurrenceProcessor, never()).runInNewTransaction(any(), any());
        }

        @Test
        void dueRows_eachRunsWithTheClockTime() {
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            when(sprintSeriesRepository.findDueIds(NOW)).thenReturn(List.of(first, second));

            scheduler.runDueRecurrences();

            InOrder order = inOrder(sprintRecurrenceProcessor);
            order.verify(sprintRecurrenceProcessor).runInNewTransaction(first, NOW);
            order.verify(sprintRecurrenceProcessor).runInNewTransaction(second, NOW);
        }

        @Test
        void oneRowFails_failureIsRecordedAndTheOthersStillRun() {
            UUID failing = UUID.randomUUID();
            UUID healthy = UUID.randomUUID();
            IllegalStateException failure = new IllegalStateException("boom");
            when(sprintSeriesRepository.findDueIds(NOW)).thenReturn(List.of(failing, healthy));
            doThrow(failure).when(sprintRecurrenceProcessor).runInNewTransaction(failing, NOW);

            assertDoesNotThrow(() -> scheduler.runDueRecurrences());

            verify(sprintRecurrenceProcessor).recordFailureInNewTransaction(failing, NOW, failure);
            verify(sprintRecurrenceProcessor).runInNewTransaction(healthy, NOW);
            verify(sprintRecurrenceProcessor, never()).recordFailureInNewTransaction(eq(healthy), any(), any());
        }

        @Test
        void versionConflict_recordedAsWarningNotErrorAndTheOthersStillRun() {
            UUID conflicting = UUID.randomUUID();
            UUID healthy = UUID.randomUUID();
            ObjectOptimisticLockingFailureException conflict =
                    new ObjectOptimisticLockingFailureException(Sprint.class, UUID.randomUUID());
            when(sprintSeriesRepository.findDueIds(NOW)).thenReturn(List.of(conflicting, healthy));
            doThrow(conflict).when(sprintRecurrenceProcessor).runInNewTransaction(conflicting, NOW);

            assertDoesNotThrow(() -> scheduler.runDueRecurrences());

            verify(sprintRecurrenceProcessor).recordFailureInNewTransaction(conflicting, NOW, conflict);
            verify(sprintRecurrenceProcessor).runInNewTransaction(healthy, NOW);
            verify(loggingService).warn(any(), eq(LogLayer.SCHEDULER), any(), eq(conflicting), any());
            verify(loggingService, never()).error(any(), any(), any(), any(Throwable.class), any());
        }

        @Test
        void recordingTheFailureFails_isLoggedAndTheOthersStillRun() {
            UUID failing = UUID.randomUUID();
            UUID healthy = UUID.randomUUID();
            IllegalStateException failure = new IllegalStateException("boom");
            when(sprintSeriesRepository.findDueIds(NOW)).thenReturn(List.of(failing, healthy));
            doThrow(failure).when(sprintRecurrenceProcessor).runInNewTransaction(failing, NOW);
            doThrow(new IllegalStateException("db down"))
                    .when(sprintRecurrenceProcessor).recordFailureInNewTransaction(failing, NOW, failure);

            assertDoesNotThrow(() -> scheduler.runDueRecurrences());

            verify(sprintRecurrenceProcessor).runInNewTransaction(healthy, NOW);
        }

        @Test
        void methodRunsInTheMidnightHourInUtc() throws NoSuchMethodException {
            Scheduled scheduled = SprintRecurrenceScheduler.class.getMethod("runDueRecurrences")
                    .getAnnotation(Scheduled.class);

            assertEquals("UTC", scheduled.zone());
            assertEquals("${scheduling.sprint-recurrence.cron:0 0,15,30,45 0 * * *}", scheduled.cron());
        }
    }

    @Nested
    class RunOnApplicationReady {

        @Test
        void dueRows_runOnce() {
            UUID id = UUID.randomUUID();
            when(sprintSeriesRepository.findDueIds(NOW)).thenReturn(List.of(id));

            scheduler.runOnApplicationReady();

            verify(sprintRecurrenceProcessor).runInNewTransaction(id, NOW);
        }

        @Test
        void dueQueryFails_isLoggedAndNotRethrown() {
            when(sprintSeriesRepository.findDueIds(NOW)).thenThrow(new IllegalStateException("db down"));

            assertDoesNotThrow(() -> scheduler.runOnApplicationReady());

            verify(sprintRecurrenceProcessor, never()).runInNewTransaction(any(), any());
        }
    }
}
