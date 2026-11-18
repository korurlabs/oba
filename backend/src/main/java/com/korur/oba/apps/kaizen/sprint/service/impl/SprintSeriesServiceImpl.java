package com.korur.oba.apps.kaizen.sprint.service.impl;

// 1. Framework-level
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.board.entity.BoardSprint;
import com.korur.oba.apps.kaizen.board.enums.BoardMemberRole;
import com.korur.oba.apps.kaizen.board.service.BoardAccessService;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.apps.kaizen.sprint.constant.SprintConstant;
import com.korur.oba.apps.kaizen.sprint.constant.SprintExceptionLogConstant;
import com.korur.oba.apps.kaizen.sprint.constant.SprintRecurrenceConstant;
import com.korur.oba.apps.kaizen.sprint.constant.service.SprintServiceLogConstant;
import com.korur.oba.apps.kaizen.sprint.dto.request.SprintSeriesRequest;
import com.korur.oba.apps.kaizen.sprint.dto.response.SprintDetailResponse;
import com.korur.oba.apps.kaizen.sprint.entity.Sprint;
import com.korur.oba.apps.kaizen.sprint.entity.SprintSeries;
import com.korur.oba.apps.kaizen.sprint.entity.SprintSeriesBoard;
import com.korur.oba.apps.kaizen.sprint.enums.SprintRecurrenceCreationMode;
import com.korur.oba.apps.kaizen.sprint.enums.SprintRecurrenceRunStatus;
import com.korur.oba.apps.kaizen.sprint.enums.SprintStatus;
import com.korur.oba.apps.kaizen.sprint.helper.SprintLifecycleHelper;
import com.korur.oba.apps.kaizen.sprint.helper.SprintRecurrenceCalculator;
import com.korur.oba.apps.kaizen.sprint.helper.SprintSeriesNaming;
import com.korur.oba.apps.kaizen.sprint.mapper.SprintMapper;
import com.korur.oba.apps.kaizen.sprint.mapper.SprintSeriesMapper;
import com.korur.oba.apps.kaizen.sprint.repository.SprintRepository;
import com.korur.oba.apps.kaizen.sprint.repository.SprintSeriesBoardRepository;
import com.korur.oba.apps.kaizen.sprint.repository.SprintSeriesRepository;
import com.korur.oba.apps.kaizen.sprint.service.SprintSeriesService;
import com.korur.oba.apps.kaizen.sprint.validator.SprintValidator;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.util.SlugUtil;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Implementation of the write operations on sprint series.
 *
 * The date and days-before rules are checked only while a series is switched
 * on, so switching off always succeeds and leaves the sprints' names, rounds
 * and the counter as they are. Every save recomputes when the job creates the
 * next sprint ahead, since that time depends on the current sprint's end date
 * and on whether the next sprint already exists.
 *
 * The series row is locked, waiting for the job, before any sprint of the
 * series is written. The job advances the counter, the schedule and the run
 * state while it holds that row, so a save that read them before the job
 * committed would put back old values and the next run would create a second
 * sprint for the same round, which the database then refuses.
 *
 * A board belongs to at most one series, so the job never creates two sprints
 * for it in one rollover. Every path that adds a board to a series goes
 * through freeBoardFor: an enabled series holds its boards and the request is
 * refused, a disabled one gives the board up.
 */
@Service
@RequiredArgsConstructor
public class SprintSeriesServiceImpl implements SprintSeriesService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(SprintSeriesServiceImpl.class);

    private final SprintSeriesRepository sprintSeriesRepository;
    private final SprintSeriesBoardRepository sprintSeriesBoardRepository;
    private final SprintRepository sprintRepository;
    private final BoardAccessService boardAccessService;
    private final SprintValidator sprintValidator;
    private final SprintLifecycleHelper sprintLifecycleHelper;
    private final SprintRecurrenceCalculator sprintRecurrenceCalculator;
    private final SprintSeriesMapper sprintSeriesMapper;
    private final SprintMapper sprintMapper;
    private final LoggingService loggingService;
    private final EntityManager entityManager;
    private final Clock clock;

    @Override
    @Transactional
    public SprintDetailResponse updateSeries(
            User user,
            UUID organizationId,
            UUID boardId,
            UUID sprintId,
            SprintSeriesRequest request) {
        // Validate board and sprint exist, and user has admin permissions.
        Sprint sprint = boardAccessService.verifySprintAccess(
                organizationId, boardId, sprintId, user.getId(), BoardMemberRole.ADMIN);

        boolean enabled = Boolean.TRUE.equals(request.enabled());
        if (enabled) {
            ensureCanRepeat(sprint, request);
        }

        // The lock may have waited for the job or another save, which can complete
        // the sprint, change its dates or start a series for it, so the sprint is
        // read again and the rules are checked on the stored state.
        SprintStatus statusAtRequest = sprint.getStatus();
        Optional<SprintSeries> locked = lockSeries(sprint);
        entityManager.refresh(sprint);
        if (locked.isEmpty()) {
            locked = lockSeries(sprint);
        }
        sprintValidator.ensureRecurrenceSprintNotCompletedDuringSave(sprint, statusAtRequest);
        if (enabled) {
            ensureCanRepeat(sprint, request);
        }

        if (locked.isEmpty()) {
            // No series already means the sprint does not repeat.
            if (enabled) {
                startSeries(sprint, request);
            }
            return sprintMapper.toDetailResponse(sprint);
        }

        SprintSeries series = locked.get();
        boolean switchedOn = enabled && !series.isEnabled();
        sprintSeriesMapper.updateEntity(series, request);
        clearUnusedDaysBefore(series);

        List<Sprint> seriesSprints = sprintRepository.findBySeries_IdOrderByRoundDesc(series.getId());

        // The job rolls the series over at the dates of each of its unfinished
        // sprints, including one created ahead whose dates an admin may have
        // edited while the series was off, so they must all suit a series.
        if (series.isEnabled()) {
            ensureUnfinishedSprintDatesValid(sprint, seriesSprints);
        }

        // Names are free while a series is off, so switching it on again takes the
        // series name from this sprint's current name and brings every unfinished
        // sprint back to "{series name} {round}"; the stored rounds stay.
        if (switchedOn) {
            String seriesName = SprintSeriesNaming.split(sprint.getName()).name();
            sprintValidator.ensureSeriesNameValid(seriesName, sprint.getId());
            claimLinkedBoards(series, seriesSprints);
            renameUnfinishedSprints(series, seriesName, seriesSprints);
        }

        series.setNextCreationAt(sprintRecurrenceCalculator.nextCreationAt(series, seriesSprints));
        sprintSeriesRepository.save(series);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                SprintServiceLogConstant.SPRINT_SERIES_UPDATED, series.getId(), sprintId, series.isEnabled());

        return sprintMapper.toDetailResponse(sprint);
    }

    @Override
    @Transactional
    public void applyOnCreate(Sprint sprint, String seriesName, SprintSeriesRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.enabled())) {
            return;
        }

        ensureCanRepeat(sprint, request);

        SprintSeries series = sprintSeriesMapper.toEntity(request);
        series.setName(seriesName);
        series.setNextRound(SprintRecurrenceConstant.SERIES_FIRST_ROUND + 1);
        joinSprint(series, sprint, SprintRecurrenceConstant.SERIES_FIRST_ROUND);
    }

    @Override
    @Transactional
    public void renameSeries(Sprint sprint, String seriesName) {
        SprintSeries series = sprint.getSeries();
        renameUnfinishedSprints(series, seriesName.trim(),
                sprintRepository.findBySeries_IdOrderByRoundDesc(series.getId()));
        sprintSeriesRepository.save(series);
    }

    @Override
    @Transactional
    public void onSprintDatesChanged(Sprint sprint) {
        SprintSeries series = sprint.getSeries();
        if (series == null || !series.isEnabled() || sprint.getStatus() == SprintStatus.COMPLETED) {
            return;
        }

        // The job rolls the series over at this sprint's dates, so they must still
        // fall at midnight. The series length does not follow the new dates.
        sprintValidator.ensureRecurrenceDatesValid(sprint, sprintRecurrenceCalculator.lengthDays(sprint));

        series.setNextCreationAt(sprintRecurrenceCalculator.nextCreationAt(series,
                sprintRepository.findBySeries_IdOrderByRoundDesc(series.getId())));
        sprintSeriesRepository.save(series);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                SprintServiceLogConstant.SPRINT_SERIES_RESCHEDULED, series.getId(), series.getNextCreationAt());
    }

    @Override
    @Transactional
    public void rescheduleSeries(Sprint sprint) {
        SprintSeries series = sprint.getSeries();
        if (series == null) {
            return;
        }
        List<Sprint> seriesSprints = sprintRepository.findBySeries_IdOrderByRoundDesc(series.getId());

        // A takeover or delete can leave the series without a running or planned
        // sprint. The job then never picks it up again, so left on it would hold
        // its boards forever and refuse them to every other series. Switched off,
        // it gives them up to the next series that asks.
        if (series.isEnabled() && sprintRecurrenceCalculator.currentSprint(seriesSprints).isEmpty()) {
            sprintLifecycleHelper.switchOff(series, clock.instant(), SprintRecurrenceConstant.RUN_MESSAGE_NO_LIVE_SPRINT);
            sprintSeriesRepository.save(series);

            loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                    SprintServiceLogConstant.SPRINT_SERIES_SWITCHED_OFF_NO_LIVE_SPRINT, series.getId(), sprint.getId());
            return;
        }

        Instant nextCreationAt = sprintRecurrenceCalculator.nextCreationAt(series, seriesSprints);
        if (Objects.equals(nextCreationAt, series.getNextCreationAt())) {
            return;
        }
        series.setNextCreationAt(nextCreationAt);
        sprintSeriesRepository.save(series);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                SprintServiceLogConstant.SPRINT_SERIES_RESCHEDULED, series.getId(), nextCreationAt);
    }

    @Override
    @Transactional
    public Optional<Sprint> advanceOnComplete(Sprint closingSprint, Instant now) {
        SprintSeries series = closingSprint.getSeries();
        if (series == null || !series.isEnabled()) {
            return Optional.empty();
        }

        List<Board> boards = sprintLifecycleHelper.liveBoardsInOrder(series);
        if (boards.isEmpty()) {
            sprintLifecycleHelper.switchOff(series, now, SprintRecurrenceConstant.RUN_MESSAGE_NO_BOARDS);
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE,
                    SprintServiceLogConstant.SPRINT_SERIES_SWITCHED_OFF_ON_COMPLETE, series.getId());
            return Optional.empty();
        }

        // Sprint dates of a series fall at 00:00 UTC, so the next round starts on
        // the completion day and the cadence moves to the manual completion.
        Instant startDate = now.truncatedTo(ChronoUnit.DAYS);
        Optional<Sprint> createdAhead = sprintRecurrenceCalculator.plannedHead(
                sprintRepository.findBySeries_IdOrderByRoundDesc(series.getId()));
        if (createdAhead.isEmpty()) {
            return Optional.of(sprintLifecycleHelper.createSeriesSprint(series, startDate,
                    startDate.plus(Duration.ofDays(series.getLengthDays())), boards));
        }

        Sprint nextSprint = createdAhead.get();
        nextSprint.setEndDate(sprintRecurrenceCalculator.nextEndDate(
                closingSprint, series, startDate, nextSprint.getEndDate()));
        nextSprint.setStartDate(startDate);
        for (Board board : boards) {
            sprintLifecycleHelper.linkSprintToBoardIfNeeded(nextSprint, board);
        }
        return Optional.of(sprintRepository.save(nextSprint));
    }

    @Override
    @Transactional
    public void startAdvancedSprint(Sprint nextSprint, Instant now) {
        SprintSeries series = nextSprint.getSeries();
        List<Board> blockingBoards = sprintLifecycleHelper.findLinkedBoardsWithOtherActiveSprint(nextSprint, null);
        boolean started = blockingBoards.isEmpty();
        if (started) {
            sprintLifecycleHelper.startSprint(nextSprint);
            sprintLifecycleHelper.recordRun(series, now, SprintRecurrenceRunStatus.OK, null);
        } else {
            // Taking over would close sprints on boards the admin did not touch, so
            // the round waits as PLANNED and the job starts it once they are free.
            sprintLifecycleHelper.recordBlocked(series, nextSprint, blockingBoards, now);
        }

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                SprintServiceLogConstant.SPRINT_SERIES_ADVANCED_ON_COMPLETE, series.getId(), nextSprint.getId(), started);
    }

    @Override
    @Transactional
    public void onBoardLinked(Sprint sprint, Board board) {
        SprintSeries series = sprint.getSeries();
        if (series == null || sprint.getStatus() == SprintStatus.COMPLETED) {
            return;
        }
        if (freeBoardFor(series, board)) {
            addBoard(series, board, nextPosition(series));
        }
    }

    @Override
    @Transactional
    public void onBoardUnlinked(Sprint sprint, UUID boardId) {
        SprintSeries series = sprint.getSeries();
        if (series == null || sprint.getStatus() == SprintStatus.COMPLETED) {
            return;
        }
        boolean removed = series.getBoards().removeIf(seriesBoard -> seriesBoard.getBoard().getId().equals(boardId));
        if (!removed) {
            return;
        }
        sprintSeriesRepository.save(series);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                SprintServiceLogConstant.SPRINT_SERIES_BOARD_REMOVED, series.getId(), boardId);
    }

    @Override
    @Transactional
    public Optional<SprintSeries> lockSeries(Sprint sprint) {
        if (sprint.getSeries() == null) {
            return Optional.empty();
        }
        Optional<SprintSeries> locked = sprintSeriesRepository.findByIdForUpdate(sprint.getSeries().getId());
        // The row may already sit in the persistence context with values read
        // before the lock, which the locking query does not overwrite.
        locked.ifPresent(entityManager::refresh);
        return locked;
    }

    /**
     * Starts a series from a sprint outside any series: the sprint's name with a
     * trailing round split off is the series name, that round (else 1) is the
     * sprint's round, and the sprint is renamed "{series name} {round}" when its
     * name differs, as "Atasprint" becomes "Atasprint 1".
     */
    private void startSeries(Sprint sprint, SprintSeriesRequest request) {
        SprintSeriesNaming.SeriesName parsed = SprintSeriesNaming.split(sprint.getName());
        sprintValidator.ensureSeriesNameValid(parsed.name(), sprint.getId());

        SprintSeries series = sprintSeriesMapper.toEntity(request);
        series.setName(parsed.name());
        series.setNextRound(parsed.round() + 1);
        joinSprint(series, sprint, parsed.round());
    }

    /**
     * Saves a new series with the sprint's boards in link order, makes the
     * sprint its given round, named after the series with its slugs following,
     * and schedules the next sprint.
     */
    private void joinSprint(SprintSeries series, Sprint sprint, int round) {
        clearUnusedDaysBefore(series);
        SprintSeries savedSeries = series;
        List<BoardSprint> links = sprint.getBoardSprints();
        for (int position = 0; position < links.size(); position++) {
            Board board = links.get(position).getBoard();
            if (freeBoardFor(savedSeries, board)) {
                savedSeries = addBoard(savedSeries, board, position);
            }
        }
        savedSeries = sprintSeriesRepository.save(savedSeries);

        sprint.setSeries(savedSeries);
        sprint.setRound(round);
        String sprintName = SprintSeriesNaming.sprintName(savedSeries.getName(), round);
        if (!sprintName.equals(sprint.getName())) {
            sprint.setName(sprintName);
            sprintLifecycleHelper.syncSlugsWithName(sprint);
        }
        Sprint savedSprint = sprintLifecycleHelper.saveWithSlugs(sprint);

        savedSeries.setNextCreationAt(sprintRecurrenceCalculator.nextCreationAt(savedSeries, List.of(savedSprint)));
        sprintSeriesRepository.save(savedSeries);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                SprintServiceLogConstant.SPRINT_SERIES_CREATED, savedSeries.getId(), savedSprint.getId(), round);
    }

    /**
     * Adds to a series that is switched on again every board linked to one of
     * its unfinished sprints that it lost while it was off, so the job never
     * starts a planned sprint on a board that another series now holds.
     */
    private void claimLinkedBoards(SprintSeries series, List<Sprint> seriesSprints) {
        for (Sprint seriesSprint : seriesSprints) {
            if (seriesSprint.getStatus() == SprintStatus.COMPLETED) {
                continue;
            }
            for (BoardSprint link : seriesSprint.getBoardSprints()) {
                if (freeBoardFor(series, link.getBoard())) {
                    addBoard(series, link.getBoard(), nextPosition(series));
                }
            }
        }
    }

    /**
     * Makes a board free to join the series, with the caller holding the lock
     * of the series once it is saved. A board in a disabled series leaves it,
     * and that series is flushed before the caller adds the new row: Hibernate
     * runs inserts before orphan deletes within one flush, so the new row would
     * otherwise break the one-series-per-board constraint.
     *
     * @return false when the board is in the series already, true when the caller adds its row
     * @throws ConflictException STA_BOARD_IN_OTHER_SPRINT_SERIES when another enabled series holds the board
     */
    private boolean freeBoardFor(SprintSeries series, Board board) {
        Optional<SprintSeriesBoard> existing = sprintSeriesBoardRepository.findByBoard_Id(board.getId());
        if (existing.isPresent() && existing.get().getSeries().getId().equals(series.getId())) {
            return false;
        }
        sprintValidator.ensureBoardNotInOtherEnabledSeries(board, series, existing);
        if (existing.isEmpty()) {
            return true;
        }

        // The owner may be switched on while this request waits for its row, so
        // the rule is checked again on the state stored under the lock.
        SprintSeries owner = existing.get().getSeries();
        sprintSeriesRepository.findByIdForUpdate(owner.getId()).ifPresent(entityManager::refresh);
        sprintValidator.ensureBoardNotInOtherEnabledSeries(board, series, existing);
        owner.getBoards().removeIf(seriesBoard -> seriesBoard.getBoard().getId().equals(board.getId()));
        sprintSeriesRepository.saveAndFlush(owner);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                SprintServiceLogConstant.SPRINT_SERIES_BOARD_MOVED, board.getId(), owner.getId(), series.getId());
        return true;
    }

    /**
     * Adds the board's row to the series at the given position and saves it.
     */
    private SprintSeries addBoard(SprintSeries series, Board board, int position) {
        series.getBoards().add(SprintSeriesBoard.builder()
                .series(series)
                .board(board)
                .position(position)
                .build());
        SprintSeries savedSeries = saveBoards(series, board);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                SprintServiceLogConstant.SPRINT_SERIES_BOARD_ADDED, savedSeries.getId(), board.getId());
        return savedSeries;
    }

    /**
     * Saves and flushes a series whose boards gained the given board. Two
     * requests can both read the board as free; the second insert then fails on
     * the unique board index, and the conflict names only the board because the
     * failed transaction can no longer read the series that won.
     *
     * @throws ConflictException STA_BOARD_IN_OTHER_SPRINT_SERIES when a concurrent write added the board to another series
     */
    private SprintSeries saveBoards(SprintSeries series, Board board) {
        try {
            return sprintSeriesRepository.saveAndFlush(series);
        } catch (DataIntegrityViolationException exception) {
            if (!SlugUtil.isSlugConflict(exception, SprintRecurrenceConstant.SERIES_BOARD_UNIQUE_INDEX)) {
                throw exception;
            }
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE,
                    SprintExceptionLogConstant.SPRINT_SERIES_BOARD_RACE_LOST, series.getId(), board.getId(),
                    exception.getMostSpecificCause().getMessage());
            throw new ConflictException(ExceptionDomainKaizenError.STA_BOARD_IN_OTHER_SPRINT_SERIES,
                    Map.of(SprintConstant.FIELD_BOARD_ID, board.getId(),
                            SprintConstant.FIELD_BOARD_NAME, board.getName()));
        }
    }

    /**
     * Stores the series name and renames every live sprint of the series that is
     * not COMPLETED to "{series name} {round}", moving its slugs. A completed
     * sprint keeps the name it ran under.
     */
    private void renameUnfinishedSprints(SprintSeries series, String seriesName, List<Sprint> seriesSprints) {
        series.setName(seriesName);
        int renamed = 0;
        for (Sprint seriesSprint : seriesSprints) {
            if (seriesSprint.getStatus() == SprintStatus.COMPLETED) {
                continue;
            }
            String sprintName = SprintSeriesNaming.sprintName(seriesName, seriesSprint.getRound());
            if (sprintName.equals(seriesSprint.getName())) {
                continue;
            }
            seriesSprint.setName(sprintName);
            sprintLifecycleHelper.syncSlugsWithName(seriesSprint);
            sprintLifecycleHelper.saveWithSlugs(seriesSprint);
            renamed++;
        }

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                SprintServiceLogConstant.SPRINT_SERIES_RENAMED, series.getId(), renamed);
    }

    /**
     * Applies the rules for switching repetition on: the sprint is not
     * completed, its dates fall at midnight, and the days before fit the
     * series length.
     */
    private void ensureCanRepeat(Sprint sprint, SprintSeriesRequest request) {
        sprintValidator.ensureRecurrenceSprintNotCompleted(sprint);
        sprintValidator.ensureRecurrenceDatesValid(sprint, sprintRecurrenceCalculator.lengthDays(sprint));
        sprintValidator.ensureRecurrenceDaysBeforeValid(
                sprint, request.creationMode(), request.createDaysBefore(), request.lengthDays());
    }

    /**
     * Checks the dates of every unfinished sprint of the series other than the
     * saved one, which ensureCanRepeat has checked already.
     */
    private void ensureUnfinishedSprintDatesValid(Sprint savedSprint, List<Sprint> seriesSprints) {
        for (Sprint seriesSprint : seriesSprints) {
            if (seriesSprint.getStatus() == SprintStatus.COMPLETED
                    || seriesSprint.getId().equals(savedSprint.getId())) {
                continue;
            }
            sprintValidator.ensureRecurrenceDatesValid(seriesSprint,
                    sprintRecurrenceCalculator.lengthDays(seriesSprint));
        }
    }

    /**
     * Drops a days-before value sent with a mode that does not use it, so the
     * stored series never holds a value the job ignores.
     */
    private static void clearUnusedDaysBefore(SprintSeries series) {
        if (series.getCreationMode() != SprintRecurrenceCreationMode.DAYS_BEFORE) {
            series.setCreateDaysBefore(null);
        }
    }

    /**
     * The position after the series' last board, 0 for a series without boards.
     */
    private static int nextPosition(SprintSeries series) {
        return series.getBoards().stream()
                .mapToInt(SprintSeriesBoard::getPosition)
                .max()
                .orElse(-1) + 1;
    }
}
