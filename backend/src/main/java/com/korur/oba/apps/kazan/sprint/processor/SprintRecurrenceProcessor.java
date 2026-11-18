package com.korur.oba.apps.kazan.sprint.processor;

// 1. Framework-level
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.hibernate.StaleStateException;

// 3. Java SDK
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.entity.BoardSprint;
import com.korur.oba.apps.kazan.sprint.constant.SprintRecurrenceConstant;
import com.korur.oba.apps.kazan.sprint.constant.processor.SprintRecurrenceProcessorLogConstant;
import com.korur.oba.apps.kazan.sprint.entity.Sprint;
import com.korur.oba.apps.kazan.sprint.entity.SprintSeries;
import com.korur.oba.apps.kazan.sprint.enums.SprintRecurrenceRunStatus;
import com.korur.oba.apps.kazan.sprint.enums.SprintRecurrenceTicketAction;
import com.korur.oba.apps.kazan.sprint.enums.SprintStatus;
import com.korur.oba.apps.kazan.sprint.helper.SprintLifecycleHelper;
import com.korur.oba.apps.kazan.sprint.helper.SprintRecurrenceCalculator;
import com.korur.oba.apps.kazan.sprint.repository.SprintRepository;
import com.korur.oba.apps.kazan.sprint.repository.SprintSeriesRepository;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.enums.TicketRelocationAction;
import com.korur.oba.apps.kazan.ticket.enums.TicketStatus;
import com.korur.oba.apps.kazan.ticket.repository.TicketRepository;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Runs the work of one sprint series for the recurrence job: creating the
 * series' next sprint ahead, rolling its active sprint over at the end date,
 * or starting its planned sprint at the start date.
 *
 * Each run has its own transaction and first locks the series row with SKIP
 * LOCKED, so two pods running the job during a rollout never act on the same
 * series. A user change to the series or to one of its sprints locks the same
 * row first and waits, so both sides take the series row before any sprint row
 * and never deadlock. A sprint a user saved after the run loaded it fails its
 * version check; the run is rolled back, recorded with a message that says
 * so, and retried by the next run.
 *
 * Every sprint the job creates takes the series' next round, and the database
 * refuses a second sprint with the same series and round, so a retried run or
 * a second pod can never create the same round twice: such a run fails, is
 * rolled back and is recorded as FAILED.
 *
 * The job acts as the system on series an admin switched on, so it checks no
 * user permission, and it links a new sprint only to the series' boards. A
 * board may have one active sprint, so a run that would break that rule
 * changes nothing and records BLOCKED; a later run retries.
 */
@Service
@RequiredArgsConstructor
public class SprintRecurrenceProcessor {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(SprintRecurrenceProcessor.class);

    private final SprintSeriesRepository sprintSeriesRepository;
    private final SprintRepository sprintRepository;
    private final TicketRepository ticketRepository;
    private final SprintLifecycleHelper sprintLifecycleHelper;
    private final SprintRecurrenceCalculator sprintRecurrenceCalculator;
    private final LoggingService loggingService;

    /**
     * Does the due work of one series in a new transaction. A series locked by
     * another run, or no longer due, is left alone. An exception rolls back
     * every change of this run; the caller records it as FAILED.
     *
     * @param seriesId ID of the series
     * @param now      the time of the run
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void runInNewTransaction(UUID seriesId, Instant now) {
        Optional<SprintSeries> locked = sprintSeriesRepository.findByIdForUpdateSkipLocked(seriesId);
        if (locked.isEmpty()) {
            loggingService.debug(LOG_PREFIX, LogLayer.SCHEDULER,
                    SprintRecurrenceProcessorLogConstant.SPRINT_SERIES_LOCKED_ELSEWHERE, seriesId);
            return;
        }
        SprintSeries series = locked.get();
        if (!series.isEnabled()) {
            loggingService.debug(LOG_PREFIX, LogLayer.SCHEDULER,
                    SprintRecurrenceProcessorLogConstant.SPRINT_SERIES_NOT_DUE, seriesId);
            return;
        }

        // Another run, or a user, may have changed the sprints since the due query.
        List<Sprint> seriesSprints = sprintRepository.findBySeries_IdOrderByRoundDesc(seriesId);
        if (seriesSprints.isEmpty()) {
            sprintLifecycleHelper.switchOff(series, now, SprintRecurrenceConstant.RUN_MESSAGE_NO_SPRINTS);
            loggingService.warn(LOG_PREFIX, LogLayer.SCHEDULER,
                    SprintRecurrenceProcessorLogConstant.SPRINT_SERIES_NO_SPRINTS, seriesId);
            return;
        }

        Optional<Sprint> current = sprintRecurrenceCalculator.currentSprint(seriesSprints);
        if (current.isEmpty() || !isDue(series, current.get(), now)) {
            rescheduleWhenStale(series, seriesSprints);
            loggingService.debug(LOG_PREFIX, LogLayer.SCHEDULER,
                    SprintRecurrenceProcessorLogConstant.SPRINT_SERIES_NOT_DUE, seriesId);
            return;
        }

        List<Board> boards = sprintLifecycleHelper.liveBoardsInOrder(series);
        if (boards.isEmpty()) {
            sprintLifecycleHelper.switchOff(series, now, SprintRecurrenceConstant.RUN_MESSAGE_NO_BOARDS);
            loggingService.warn(LOG_PREFIX, LogLayer.SCHEDULER,
                    SprintRecurrenceProcessorLogConstant.SPRINT_SERIES_NO_BOARDS, seriesId);
            return;
        }

        Sprint sprint = current.get();
        if (sprint.getStatus() == SprintStatus.PLANNED) {
            // No sprint of the series runs, so its planned sprint starts first; the
            // rollover or creation it then owes follows in the same run.
            if (!startPlannedSprint(series, sprint, seriesSprints, now)) {
                return;
            }
            if (sprint.getEndDate().isAfter(now) && !creationTimeReached(series, now)) {
                return;
            }
        }

        if (!sprint.getEndDate().isAfter(now)) {
            rollOver(series, sprint, seriesSprints, boards, now);
        } else {
            createNextSprintAhead(series, sprint, seriesSprints, boards, now);
        }
    }

    /**
     * Records an unexpected failure of a run in a new transaction, after the
     * run's own transaction was rolled back. Nothing else changes, so the
     * sprints stay as they were and the next run retries. A series locked by a
     * user's save or by another run is left alone: writing it from an unlocked
     * read would overwrite that save, and the scheduler has already logged the
     * failure.
     *
     * @param seriesId ID of the series
     * @param now      the time of the run
     * @param failure  the exception that ended the run
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailureInNewTransaction(UUID seriesId, Instant now, Exception failure) {
        Optional<SprintSeries> series = sprintSeriesRepository.findByIdForUpdateSkipLocked(seriesId);
        if (series.isEmpty()) {
            loggingService.debug(LOG_PREFIX, LogLayer.SCHEDULER,
                    SprintRecurrenceProcessorLogConstant.SPRINT_SERIES_LOCKED_ELSEWHERE, seriesId);
            return;
        }
        String message = isConcurrentUpdate(failure)
                ? SprintRecurrenceConstant.RUN_MESSAGE_CONCURRENT_UPDATE
                : String.format(SprintRecurrenceConstant.RUN_MESSAGE_FAILED_FORMAT,
                        failure.getClass().getSimpleName(), failure.getMessage());
        sprintLifecycleHelper.recordRun(series.get(), now, SprintRecurrenceRunStatus.FAILED, message);

        loggingService.info(LOG_PREFIX, LogLayer.SCHEDULER,
                SprintRecurrenceProcessorLogConstant.SPRINT_SERIES_FAILURE_RECORDED, seriesId);
    }

    /**
     * Whether a run ended because a user saved one of its sprints while it ran,
     * so the sprint's version check rejected the run's write. That is an
     * expected race, not a defect: the run was rolled back and the next run
     * starts from the user's change.
     *
     * @param failure the exception that ended the run
     * @return true when the exception or one of its causes is an optimistic lock failure
     */
    public static boolean isConcurrentUpdate(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof OptimisticLockingFailureException
                    || current instanceof OptimisticLockException
                    || current instanceof StaleStateException) {
                return true;
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }

    /**
     * Same rule as the due query, applied to the locked series and its
     * reloaded current sprint: the ACTIVE sprint has ended or the creation
     * time has passed, or the PLANNED sprint, with none running, has reached
     * its start date.
     */
    private static boolean isDue(SprintSeries series, Sprint current, Instant now) {
        if (current.getStartDate() == null || current.getEndDate() == null) {
            return false;
        }
        if (current.getStatus() == SprintStatus.ACTIVE) {
            return !current.getEndDate().isAfter(now) || creationTimeReached(series, now);
        }
        return !current.getStartDate().isAfter(now);
    }

    private static boolean creationTimeReached(SprintSeries series, Instant now) {
        return series.getNextCreationAt() != null && !series.getNextCreationAt().isAfter(now);
    }

    /**
     * Starts the series' planned sprint when none of its boards has another
     * active sprint; otherwise records BLOCKED.
     *
     * @return true when the sprint was started
     */
    private boolean startPlannedSprint(SprintSeries series, Sprint sprint, List<Sprint> seriesSprints, Instant now) {
        List<Board> blockingBoards = findBoardsWithOtherActiveSprint(boardsOf(sprint), sprint.getId());
        if (!blockingBoards.isEmpty()) {
            sprintLifecycleHelper.recordBlocked(series, sprint, blockingBoards, now);
            return false;
        }

        sprintLifecycleHelper.startSprint(sprint);
        series.setNextCreationAt(nextCreationAt(series, seriesSprints));
        sprintLifecycleHelper.recordRun(series, now, SprintRecurrenceRunStatus.OK, null);

        loggingService.info(LOG_PREFIX, LogLayer.SCHEDULER,
                SprintRecurrenceProcessorLogConstant.SPRINT_RECURRENCE_STARTED, sprint.getId());
        return true;
    }

    /**
     * Creates the series' next sprint as PLANNED, with the dates it will run
     * on, so tickets can be planned into it before the rollover. A sprint
     * already created ahead is kept; no second one is created.
     */
    private void createNextSprintAhead(SprintSeries series, Sprint current, List<Sprint> seriesSprints,
                                       List<Board> boards, Instant now) {
        Optional<Sprint> createdAhead = sprintCreatedAhead(current, seriesSprints);
        List<Sprint> sprintsAfterRun = seriesSprints;
        if (createdAhead.isEmpty()) {
            Instant nextStart = current.getEndDate();
            Sprint nextSprint = sprintLifecycleHelper.createSeriesSprint(series, nextStart,
                    nextStart.plus(Duration.ofDays(series.getLengthDays())), boards);
            sprintsAfterRun = withHead(nextSprint, seriesSprints);

            loggingService.info(LOG_PREFIX, LogLayer.SCHEDULER,
                    SprintRecurrenceProcessorLogConstant.SPRINT_SERIES_SPRINT_CREATED,
                    series.getId(), nextSprint.getId(), nextSprint.getRound());
        }

        series.setNextCreationAt(nextCreationAt(series, sprintsAfterRun));
        sprintLifecycleHelper.recordRun(series, now, SprintRecurrenceRunStatus.OK, null);
    }

    /**
     * Completes the closing sprint, moves its unfinished tickets and starts the
     * next sprint of the series in the period that contains now: the sprint
     * created ahead when there is one, else a new sprint with the next round.
     * Every board check runs before the first change, so a BLOCKED run leaves
     * everything as it was.
     */
    private void rollOver(SprintSeries series, Sprint closingSprint, List<Sprint> seriesSprints,
                          List<Board> boards, Instant now) {
        Optional<Sprint> plannedNext = sprintCreatedAhead(closingSprint, seriesSprints);

        List<Board> candidateBoards = new ArrayList<>(boardsOf(closingSprint));
        plannedNext.ifPresent(next -> candidateBoards.addAll(boardsOf(next)));
        candidateBoards.addAll(boards);
        List<Board> blockingBoards = findBoardsWithOtherActiveSprint(candidateBoards, closingSprint.getId());
        if (!blockingBoards.isEmpty()) {
            sprintLifecycleHelper.recordBlocked(series, closingSprint, blockingBoards, now);
            return;
        }

        Instant rolloverAt = sprintRecurrenceCalculator.rolloverAt(closingSprint, series, now);
        Sprint nextSprint;
        if (plannedNext.isPresent()) {
            nextSprint = plannedNext.get();
            nextSprint.setEndDate(sprintRecurrenceCalculator.nextEndDate(
                    closingSprint, series, rolloverAt, nextSprint.getEndDate()));
            nextSprint.setStartDate(rolloverAt);
            for (Board board : boards) {
                sprintLifecycleHelper.linkSprintToBoardIfNeeded(nextSprint, board);
            }
            nextSprint = sprintRepository.save(nextSprint);
        } else {
            nextSprint = sprintLifecycleHelper.createSeriesSprint(series, rolloverAt,
                    sprintRecurrenceCalculator.nextEndDate(closingSprint, series, rolloverAt, null), boards);
            loggingService.info(LOG_PREFIX, LogLayer.SCHEDULER,
                    SprintRecurrenceProcessorLogConstant.SPRINT_SERIES_SPRINT_CREATED,
                    series.getId(), nextSprint.getId(), nextSprint.getRound());
        }

        List<Ticket> unfinishedTickets = ticketRepository.findBySprint_IdAndStatusNot(
                closingSprint.getId(), TicketStatus.DONE);
        if (series.getUnfinishedTicketAction() == SprintRecurrenceTicketAction.MOVE_TO_BACKLOG) {
            sprintLifecycleHelper.moveToEachBoardBacklog(unfinishedTickets, closingSprint.getId());
            sprintLifecycleHelper.completeSprint(closingSprint, List.of(),
                    TicketRelocationAction.MOVE_TO_BACKLOG, null, null, null);
        } else {
            sprintLifecycleHelper.completeSprint(closingSprint, unfinishedTickets,
                    TicketRelocationAction.MOVE_TO_SPRINT, null, nextSprint, null);
        }

        sprintLifecycleHelper.startSprint(nextSprint);

        series.setNextCreationAt(nextCreationAt(series, withHead(nextSprint, seriesSprints)));
        sprintLifecycleHelper.recordRun(series, now, SprintRecurrenceRunStatus.OK, null);

        loggingService.info(LOG_PREFIX, LogLayer.SCHEDULER,
                SprintRecurrenceProcessorLogConstant.SPRINT_RECURRENCE_ROLLED_OVER,
                closingSprint.getId(), nextSprint.getId(), rolloverAt, unfinishedTickets.size());
    }

    /**
     * The series' PLANNED sprint with a higher round than the given one: the
     * sprint created ahead of its rollover. A deleted, started or completed
     * sprint is not reused; a new one is created instead.
     */
    private Optional<Sprint> sprintCreatedAhead(Sprint current, List<Sprint> seriesSprints) {
        return sprintRecurrenceCalculator.plannedHead(seriesSprints)
                .filter(head -> head != current);
    }

    /**
     * The time the job next creates a sprint ahead, after this run's changes.
     * When the days before do not fit the current sprint (its dates were
     * edited), the next sprint is created at the end date instead, which is
     * worth a warning.
     */
    private Instant nextCreationAt(SprintSeries series, List<Sprint> seriesSprints) {
        Optional<Sprint> current = sprintRecurrenceCalculator.currentSprint(seriesSprints);
        if (current.isPresent() && !sprintRecurrenceCalculator.daysBeforeFitsLength(current.get(), series)) {
            loggingService.warn(LOG_PREFIX, LogLayer.SCHEDULER,
                    SprintRecurrenceProcessorLogConstant.SPRINT_RECURRENCE_DAYS_BEFORE_DOES_NOT_FIT,
                    current.get().getId(), series.getCreateDaysBefore(),
                    sprintRecurrenceCalculator.lengthDays(current.get()));
        }
        return sprintRecurrenceCalculator.nextCreationAt(series, seriesSprints);
    }

    /**
     * Stores the creation time the series' sprints call for when the stored one
     * differs, so a series the due query keeps returning for an outdated time
     * stops being picked up. Nothing is written when the time is right.
     */
    private void rescheduleWhenStale(SprintSeries series, List<Sprint> seriesSprints) {
        Instant expected = sprintRecurrenceCalculator.nextCreationAt(series, seriesSprints);
        if (Objects.equals(expected, series.getNextCreationAt())) {
            return;
        }
        series.setNextCreationAt(expected);
        sprintSeriesRepository.save(series);
    }

    /**
     * The series' sprints with the given sprint as their head, the highest
     * round; the sprint is not repeated when the list already holds it.
     */
    private static List<Sprint> withHead(Sprint head, List<Sprint> seriesSprints) {
        List<Sprint> sprints = new ArrayList<>();
        sprints.add(head);
        for (Sprint sprint : seriesSprints) {
            if (sprint != head) {
                sprints.add(sprint);
            }
        }
        return sprints;
    }

    private static List<Board> boardsOf(Sprint sprint) {
        return sprint.getBoardSprints().stream()
                .map(BoardSprint::getBoard)
                .toList();
    }

    /**
     * The boards, each once and in the given order, that hold an ACTIVE sprint
     * other than the ignored one, so starting or rolling over would leave them
     * with two.
     */
    private List<Board> findBoardsWithOtherActiveSprint(List<Board> boards, UUID ignoreSprintId) {
        Map<UUID, Board> distinct = new LinkedHashMap<>();
        for (Board board : boards) {
            distinct.putIfAbsent(board.getId(), board);
        }
        return distinct.values().stream()
                .filter(board -> sprintRepository
                        .findIdsByBoardIdsAndStatus(List.of(board.getId()), SprintStatus.ACTIVE).stream()
                        .anyMatch(activeId -> !activeId.equals(ignoreSprintId)))
                .toList();
    }
}
