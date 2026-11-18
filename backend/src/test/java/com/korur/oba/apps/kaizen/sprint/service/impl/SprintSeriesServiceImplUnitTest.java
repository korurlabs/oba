package com.korur.oba.apps.kaizen.sprint.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static com.korur.oba.apps.kaizen.sprint.mock_data.SprintRecurrenceMocks.atEndSeries;
import static com.korur.oba.apps.kaizen.sprint.mock_data.SprintRecurrenceMocks.board;
import static com.korur.oba.apps.kaizen.sprint.mock_data.SprintRecurrenceMocks.inSeries;
import static com.korur.oba.apps.kaizen.sprint.mock_data.SprintRecurrenceMocks.linkedTo;
import static com.korur.oba.apps.kaizen.sprint.mock_data.SprintRecurrenceMocks.sprint;
import static com.korur.oba.apps.kaizen.sprint.mock_data.SprintRecurrenceMocks.utc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 2. Third-party
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;

// 3. Java SDK
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.board.enums.BoardMemberRole;
import com.korur.oba.apps.kaizen.board.service.BoardAccessService;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.apps.kaizen.sprint.constant.SprintConstant;
import com.korur.oba.apps.kaizen.sprint.constant.SprintRecurrenceConstant;
import com.korur.oba.apps.kaizen.sprint.dto.request.SprintSeriesRequest;
import com.korur.oba.apps.kaizen.sprint.entity.Sprint;
import com.korur.oba.apps.kaizen.sprint.entity.SprintSeries;
import com.korur.oba.apps.kaizen.sprint.entity.SprintSeriesBoard;
import com.korur.oba.apps.kaizen.sprint.enums.SprintRecurrenceCreationMode;
import com.korur.oba.apps.kaizen.sprint.enums.SprintRecurrenceRunStatus;
import com.korur.oba.apps.kaizen.sprint.enums.SprintRecurrenceTicketAction;
import com.korur.oba.apps.kaizen.sprint.enums.SprintStatus;
import com.korur.oba.apps.kaizen.sprint.helper.SprintLifecycleHelper;
import com.korur.oba.apps.kaizen.sprint.helper.SprintRecurrenceCalculator;
import com.korur.oba.apps.kaizen.sprint.mapper.SprintMapper;
import com.korur.oba.apps.kaizen.sprint.mapper.SprintSeriesMapper;
import com.korur.oba.apps.kaizen.sprint.repository.SprintRepository;
import com.korur.oba.apps.kaizen.sprint.repository.SprintSeriesBoardRepository;
import com.korur.oba.apps.kaizen.sprint.repository.SprintSeriesRepository;
import com.korur.oba.apps.kaizen.sprint.validator.SprintValidator;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class SprintSeriesServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private SprintSeriesRepository sprintSeriesRepository;

    @Mock
    private SprintSeriesBoardRepository sprintSeriesBoardRepository;

    @Mock
    private SprintRepository sprintRepository;

    @Mock
    private BoardAccessService boardAccessService;

    @Mock
    private SprintValidator sprintValidator;

    @Mock
    private SprintLifecycleHelper sprintLifecycleHelper;

    @Mock
    private SprintSeriesMapper sprintSeriesMapper;

    @Mock
    private SprintMapper sprintMapper;

    @Mock
    private LoggingService loggingService;

    @Mock
    private EntityManager entityManager;

    private final SprintRecurrenceCalculator calculator = new SprintRecurrenceCalculator();

    private static final Instant NOW = Instant.parse("2026-11-04T07:35:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private SprintSeriesServiceImpl sprintSeriesServiceImpl;

    private final Board alpha = board("Alpha");
    private SprintSeries series;
    private Sprint active;
    private Sprint ahead;

    @BeforeEach
    void setUp() {
        sprintSeriesServiceImpl = new SprintSeriesServiceImpl(sprintSeriesRepository, sprintSeriesBoardRepository, sprintRepository,
                boardAccessService, sprintValidator, sprintLifecycleHelper, calculator, sprintSeriesMapper,
                sprintMapper, loggingService, entityManager, clock);

        series = atEndSeries(alpha);
        series.setNextRound(3);
        active = linkedTo(inSeries(sprint(SprintStatus.ACTIVE, utc("2026-11-02"), utc("2026-11-09")),
                series, 1), alpha);
        ahead = linkedTo(inSeries(sprint(SprintStatus.PLANNED, utc("2026-11-09"), utc("2026-11-16")),
                series, 2), alpha);
    }

    @Nested
    class RescheduleSeries {

        @Test
        void sprintOutsideSeries_touchesNothing() {
            // Arrange
            Sprint loose = sprint(SprintStatus.ACTIVE, utc("2026-11-02"), utc("2026-11-09"));

            // Act
            sprintSeriesServiceImpl.rescheduleSeries(loose);

            // Assert
            verifyNoInteractions(sprintRepository, sprintSeriesRepository);
        }

        @Test
        void staleCreationTime_isRecomputedAndSaved() {
            // Arrange
            // The created-ahead sprint was deleted, so the job must create the next one again.
            series.setCreationMode(SprintRecurrenceCreationMode.DAYS_BEFORE);
            series.setCreateDaysBefore(2);
            series.setNextCreationAt(null);
            List<Sprint> sprints = List.of(active);
            when(sprintRepository.findBySeries_IdOrderByRoundDesc(series.getId())).thenReturn(sprints);
            Instant expected = calculator.nextCreationAt(series, sprints);

            // Act
            sprintSeriesServiceImpl.rescheduleSeries(active);

            // Assert
            assertEquals(utc("2026-11-07"), expected);
            assertEquals(expected, series.getNextCreationAt());
            verify(sprintSeriesRepository).save(series);
        }

        @Test
        void takeoverClosedTheOnlySprint_switchesTheSeriesOff() {
            // Arrange
            // A takeover completed the series' running sprint and nothing was planned after it.
            active.setStatus(SprintStatus.COMPLETED);
            when(sprintRepository.findBySeries_IdOrderByRoundDesc(series.getId())).thenReturn(List.of(active));

            // Act
            sprintSeriesServiceImpl.rescheduleSeries(active);

            // Assert
            verify(sprintLifecycleHelper).switchOff(series, NOW, SprintRecurrenceConstant.RUN_MESSAGE_NO_LIVE_SPRINT);
            verify(sprintSeriesRepository).save(series);
        }

        @Test
        void takeoverClosedTheRunningSprintButOneIsPlanned_keepsTheSeriesOn() {
            // Arrange
            active.setStatus(SprintStatus.COMPLETED);
            when(sprintRepository.findBySeries_IdOrderByRoundDesc(series.getId())).thenReturn(List.of(ahead, active));

            // Act
            sprintSeriesServiceImpl.rescheduleSeries(active);

            // Assert
            verify(sprintLifecycleHelper, never()).switchOff(any(), any(), any());
        }

        @Test
        void switchedOffSeriesWithoutLiveSprint_isLeftAsItIs() {
            // Arrange
            series.setEnabled(false);
            series.setNextCreationAt(null);
            active.setStatus(SprintStatus.COMPLETED);
            when(sprintRepository.findBySeries_IdOrderByRoundDesc(series.getId())).thenReturn(List.of(active));

            // Act
            sprintSeriesServiceImpl.rescheduleSeries(active);

            // Assert
            verify(sprintLifecycleHelper, never()).switchOff(any(), any(), any());
            verify(sprintSeriesRepository, never()).save(any());
        }

        @Test
        void unchangedCreationTime_savesNothing() {
            // Arrange
            List<Sprint> sprints = List.of(ahead, active);
            when(sprintRepository.findBySeries_IdOrderByRoundDesc(series.getId())).thenReturn(sprints);
            series.setNextCreationAt(calculator.nextCreationAt(series, sprints));

            // Act
            sprintSeriesServiceImpl.rescheduleSeries(active);

            // Assert
            verify(sprintSeriesRepository, never()).save(any());
        }
    }

    @Nested
    class LockSeries {

        @Test
        void sprintOutsideSeries_returnsEmptyWithoutLocking() {
            // Arrange
            Sprint loose = sprint(SprintStatus.ACTIVE, utc("2026-11-02"), utc("2026-11-09"));

            // Act
            Optional<SprintSeries> result = sprintSeriesServiceImpl.lockSeries(loose);

            // Assert
            assertTrue(result.isEmpty());
            verifyNoInteractions(sprintSeriesRepository, entityManager);
        }

        @Test
        void sprintInSeries_locksAndRefreshesTheRow() {
            // Arrange
            when(sprintSeriesRepository.findByIdForUpdate(series.getId())).thenReturn(Optional.of(series));

            // Act
            Optional<SprintSeries> result = sprintSeriesServiceImpl.lockSeries(active);

            // Assert
            // The refresh drops values the persistence context read before the lock.
            assertSame(series, result.orElseThrow());
            verify(sprintSeriesRepository).findByIdForUpdate(series.getId());
            verify(entityManager).refresh(series);
        }

        @Test
        void seriesRowGone_returnsEmptyWithoutRefresh() {
            // Arrange
            when(sprintSeriesRepository.findByIdForUpdate(series.getId())).thenReturn(Optional.empty());

            // Act
            Optional<SprintSeries> result = sprintSeriesServiceImpl.lockSeries(active);

            // Assert
            assertTrue(result.isEmpty());
            verify(entityManager, never()).refresh(any());
        }
    }

    @Nested
    class UpdateSeriesChecksUnfinishedSprints {

        private final User user = userWithId();

        private User userWithId() {
            User created = User.builder().build();
            created.setId(UUID.randomUUID());
            return created;
        }

        private SprintSeriesRequest request(boolean enabled) {
            return new SprintSeriesRequest(enabled, 7, SprintRecurrenceTicketAction.MOVE_TO_NEXT_SPRINT,
                    SprintRecurrenceCreationMode.AT_END, null);
        }

        private void arrangeSave() {
            when(boardAccessService.verifySprintAccess(organizationId, alpha.getId(), active.getId(), user.getId(),
                    BoardMemberRole.ADMIN)).thenReturn(active);
            when(sprintSeriesRepository.findByIdForUpdate(series.getId())).thenReturn(Optional.of(series));
            when(sprintRepository.findBySeries_IdOrderByRoundDesc(series.getId()))
                    .thenReturn(List.of(ahead, active));
        }

        @Test
        void enabledSeries_checksTheCreatedAheadSprintsDates() {
            // Arrange
            SprintSeriesRequest request = request(true);
            arrangeSave();

            // Act
            sprintSeriesServiceImpl.updateSeries(user, organizationId, alpha.getId(), active.getId(), request);

            // Assert
            verify(sprintValidator).ensureRecurrenceDatesValid(ahead, 7);
            verify(sprintSeriesRepository).save(series);
        }

        @Test
        void createdAheadSprintOffMidnight_rejectsTheSaveAndKeepsTheSeries() {
            // Arrange
            SprintSeriesRequest request = request(true);
            arrangeSave();
            ahead.setEndDate(Instant.parse("2026-11-16T10:00:00Z"));
            // The saved sprint passes, so only the created-ahead sprint's check throws.
            lenient().doThrow(new ValidationException(ExceptionDomainKaizenError.VAL_SPRINT_RECURRENCE_DATES_INVALID, Map.of()))
                    .when(sprintValidator).ensureRecurrenceDatesValid(eq(ahead), any());

            // Act and Assert
            assertThrows(ValidationException.class, () ->
                    sprintSeriesServiceImpl.updateSeries(user, organizationId, alpha.getId(), active.getId(), request));
            verify(sprintSeriesRepository, never()).save(any());
        }

        @Test
        void disabledSeries_doesNotCheckTheOtherSprints() {
            // Arrange
            SprintSeriesRequest request = request(false);
            series.setEnabled(false);
            arrangeSave();

            // Act
            sprintSeriesServiceImpl.updateSeries(user, organizationId, alpha.getId(), active.getId(), request);

            // Assert
            verify(sprintValidator, never()).ensureRecurrenceDatesValid(eq(ahead), any());
            verify(sprintSeriesRepository).save(series);
        }
    }

    @Nested
    class AdvanceOnComplete {

        private final Instant now = Instant.parse("2026-11-10T15:00:00Z");

        @Test
        void noSprintCreatedAhead_createsTheNextRoundFromTheCompletionDay() {
            // Arrange
            series.setLengthDays(14);
            List<Board> boards = List.of(alpha);
            Sprint created = inSeries(sprint(SprintStatus.PLANNED, utc("2026-11-10"), utc("2026-11-24")), series, 3);
            when(sprintLifecycleHelper.liveBoardsInOrder(series)).thenReturn(boards);
            when(sprintRepository.findBySeries_IdOrderByRoundDesc(series.getId())).thenReturn(List.of(active));
            when(sprintLifecycleHelper.createSeriesSprint(series, utc("2026-11-10"), utc("2026-11-24"), boards))
                    .thenReturn(created);

            // Act
            Optional<Sprint> next = sprintSeriesServiceImpl.advanceOnComplete(active, now);

            // Assert
            assertEquals(Optional.of(created), next);
        }

        @Test
        void sprintCreatedAhead_isRedatedAndLinkedToTheSeriesBoards() {
            // Arrange
            series.setLengthDays(14);
            when(sprintLifecycleHelper.liveBoardsInOrder(series)).thenReturn(List.of(alpha));
            when(sprintRepository.findBySeries_IdOrderByRoundDesc(series.getId())).thenReturn(List.of(ahead, active));
            when(sprintRepository.save(ahead)).thenReturn(ahead);

            // Act
            Optional<Sprint> next = sprintSeriesServiceImpl.advanceOnComplete(active, now);

            // Assert
            assertEquals(Optional.of(ahead), next);
            assertEquals(utc("2026-11-10"), ahead.getStartDate());
            assertEquals(utc("2026-11-24"), ahead.getEndDate());
            verify(sprintLifecycleHelper).linkSprintToBoardIfNeeded(ahead, alpha);
            verify(sprintLifecycleHelper, never()).createSeriesSprint(any(), any(), any(), any());
        }

        @Test
        void noLiveBoard_switchesTheSeriesOffAndReturnsNothing() {
            // Arrange
            when(sprintLifecycleHelper.liveBoardsInOrder(series)).thenReturn(List.of());

            // Act
            Optional<Sprint> next = sprintSeriesServiceImpl.advanceOnComplete(active, now);

            // Assert
            assertEquals(Optional.empty(), next);
            verify(sprintLifecycleHelper).switchOff(series, now, SprintRecurrenceConstant.RUN_MESSAGE_NO_BOARDS);
            verifyNoInteractions(sprintRepository);
        }

        @Test
        void disabledSeries_returnsNothing() {
            // Arrange
            series.setEnabled(false);

            // Act
            Optional<Sprint> next = sprintSeriesServiceImpl.advanceOnComplete(active, now);

            // Assert
            assertEquals(Optional.empty(), next);
            verifyNoInteractions(sprintLifecycleHelper, sprintRepository);
        }
    }

    @Nested
    class StartAdvancedSprint {

        private final Instant now = Instant.parse("2026-11-10T15:00:00Z");

        @Test
        void noBoardBlocks_startsTheSprintAndRecordsOk() {
            // Arrange
            when(sprintLifecycleHelper.findLinkedBoardsWithOtherActiveSprint(ahead, null)).thenReturn(List.of());

            // Act
            sprintSeriesServiceImpl.startAdvancedSprint(ahead, now);

            // Assert
            verify(sprintLifecycleHelper).startSprint(ahead);
            verify(sprintLifecycleHelper).recordRun(series, now, SprintRecurrenceRunStatus.OK, null);
        }

        @Test
        void boardWithAnotherActiveSprint_leavesItPlannedAndRecordsBlocked() {
            // Arrange
            when(sprintLifecycleHelper.findLinkedBoardsWithOtherActiveSprint(ahead, null)).thenReturn(List.of(alpha));

            // Act
            sprintSeriesServiceImpl.startAdvancedSprint(ahead, now);

            // Assert
            verify(sprintLifecycleHelper, never()).startSprint(any());
            verify(sprintLifecycleHelper, never()).takeOverAndStart(any(), any());
            verify(sprintLifecycleHelper).recordBlocked(series, ahead, List.of(alpha), now);
        }
    }

    /**
     * The one-series-per-board rule on every path that adds a board to a series,
     * with the real validator so the conflict carries the stored names.
     */
    @Nested
    class BoardClaim {

        private final Board beta = board("Beta");
        private final User user = User.builder().build();

        private SprintSeriesServiceImpl claimService;

        @BeforeEach
        void setUpClaim() {
            user.setId(UUID.randomUUID());
            claimService = new SprintSeriesServiceImpl(sprintSeriesRepository, sprintSeriesBoardRepository,
                    sprintRepository, boardAccessService, new SprintValidator(sprintRepository, loggingService),
                    sprintLifecycleHelper, calculator, sprintSeriesMapper, sprintMapper, loggingService,
                    entityManager, clock);
            lenient().when(sprintSeriesRepository.save(any(SprintSeries.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            lenient().when(sprintSeriesRepository.saveAndFlush(any(SprintSeries.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            lenient().when(sprintLifecycleHelper.saveWithSlugs(any(Sprint.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
        }

        private SprintSeries otherSeries(String name, boolean enabled, Board... boards) {
            SprintSeries other = atEndSeries(boards);
            other.setName(name);
            other.setEnabled(enabled);
            return other;
        }

        private void rowOf(Board board, SprintSeries owner) {
            SprintSeriesBoard row = owner.getBoards().stream()
                    .filter(seriesBoard -> seriesBoard.getBoard().getId().equals(board.getId()))
                    .findFirst()
                    .orElseThrow();
            when(sprintSeriesBoardRepository.findByBoard_Id(board.getId())).thenReturn(Optional.of(row));
        }

        private void free(Board board) {
            when(sprintSeriesBoardRepository.findByBoard_Id(board.getId())).thenReturn(Optional.empty());
        }

        private void lockable(SprintSeries owner) {
            when(sprintSeriesRepository.findByIdForUpdate(owner.getId())).thenReturn(Optional.of(owner));
        }

        private SprintSeriesRequest repeat(boolean enabled) {
            return new SprintSeriesRequest(enabled, 7, SprintRecurrenceTicketAction.MOVE_TO_NEXT_SPRINT,
                    SprintRecurrenceCreationMode.AT_END, null);
        }

        private List<UUID> boardIds(SprintSeries owner) {
            return owner.getBoards().stream().map(seriesBoard -> seriesBoard.getBoard().getId()).toList();
        }

        private int positionOf(SprintSeries owner, Board board) {
            return owner.getBoards().stream()
                    .filter(seriesBoard -> seriesBoard.getBoard().getId().equals(board.getId()))
                    .findFirst()
                    .orElseThrow()
                    .getPosition();
        }

        private void assertTakenBy(ConflictException exception, Board board, SprintSeries owner) {
            assertEquals(ExceptionDomainKaizenError.STA_BOARD_IN_OTHER_SPRINT_SERIES.getCode(),
                    exception.getErrorCode());
            assertEquals(board.getId(), exception.getErrorValues().get(SprintConstant.FIELD_BOARD_ID));
            assertEquals(board.getName(), exception.getErrorValues().get(SprintConstant.FIELD_BOARD_NAME));
            assertEquals(owner.getId(), exception.getErrorValues().get(SprintRecurrenceConstant.FIELD_SERIES_ID));
            assertEquals(owner.getName(), exception.getErrorValues().get(SprintRecurrenceConstant.FIELD_SERIES_NAME));
        }

        private Sprint looseSprint(Board... boards) {
            Sprint loose = linkedTo(sprint(SprintStatus.PLANNED, utc("2026-11-09"), utc("2026-11-16")), boards);
            loose.setName("Gammasprint");
            return loose;
        }

        private SprintSeries arrangeNewSeries(SprintSeriesRequest request) {
            SprintSeries created = SprintSeries.builder().lengthDays(7).build();
            when(sprintSeriesMapper.toEntity(request)).thenReturn(created);
            return created;
        }

        private void arrangeReEnable(SprintSeriesRequest request) {
            series.setEnabled(false);
            linkedTo(active, beta);
            when(boardAccessService.verifySprintAccess(organizationId, alpha.getId(), active.getId(), user.getId(),
                    BoardMemberRole.ADMIN)).thenReturn(active);
            lockable(series);
            when(sprintRepository.findBySeries_IdOrderByRoundDesc(series.getId()))
                    .thenReturn(List.of(ahead, active));
            doAnswer(invocation -> {
                series.setEnabled(request.enabled());
                return null;
            }).when(sprintSeriesMapper).updateEntity(series, request);
            rowOf(alpha, series);
        }

        @Test
        void createWithRepeat_pathBoardInEnabledSeries_throwsKaiSta018AndSavesNothing() {
            // Arrange
            SprintSeriesRequest request = repeat(true);
            Sprint created = looseSprint(alpha);
            arrangeNewSeries(request);
            rowOf(alpha, series);

            // Act
            ConflictException exception = assertThrows(ConflictException.class,
                    () -> claimService.applyOnCreate(created, "Gammasprint", request));

            // Assert
            assertTakenBy(exception, alpha, series);
            verify(sprintSeriesRepository, never()).saveAndFlush(any());
            verify(sprintLifecycleHelper, never()).saveWithSlugs(any());
        }

        @Test
        void createWithRepeat_onlyChildBoardInEnabledSeries_throwsKaiSta018NamingTheChild() {
            // Arrange
            SprintSeriesRequest request = repeat(true);
            Sprint created = looseSprint(beta, alpha);
            arrangeNewSeries(request);
            free(beta);
            rowOf(alpha, series);

            // Act
            ConflictException exception = assertThrows(ConflictException.class,
                    () -> claimService.applyOnCreate(created, "Gammasprint", request));

            // Assert
            assertTakenBy(exception, alpha, series);
            verify(sprintLifecycleHelper, never()).saveWithSlugs(any());
        }

        @Test
        void createWithRepeatOff_takenBoard_doesNoSeriesWork() {
            // Act
            claimService.applyOnCreate(looseSprint(alpha), "Gammasprint", repeat(false));

            // Assert
            verifyNoInteractions(sprintSeriesBoardRepository, sprintSeriesRepository);
        }

        @Test
        void enable_boardInEnabledSeries_throwsKaiSta018AndLeavesTheSprintOutsideAnySeries() {
            // Arrange
            SprintSeriesRequest request = repeat(true);
            Sprint loose = looseSprint(alpha);
            when(boardAccessService.verifySprintAccess(organizationId, alpha.getId(), loose.getId(), user.getId(),
                    BoardMemberRole.ADMIN)).thenReturn(loose);
            arrangeNewSeries(request);
            rowOf(alpha, series);

            // Act
            ConflictException exception = assertThrows(ConflictException.class,
                    () -> claimService.updateSeries(user, organizationId, alpha.getId(), loose.getId(), request));

            // Assert
            assertTakenBy(exception, alpha, series);
            assertNull(loose.getSeries());
        }

        @Test
        void enable_boardInDisabledSeries_movesTheRowAndLeavesThatSeriesOffWithItsOtherBoards() {
            // Arrange
            SprintSeriesRequest request = repeat(true);
            Sprint loose = looseSprint(alpha);
            when(boardAccessService.verifySprintAccess(organizationId, alpha.getId(), loose.getId(), user.getId(),
                    BoardMemberRole.ADMIN)).thenReturn(loose);
            SprintSeries created = arrangeNewSeries(request);
            SprintSeries owner = otherSeries("Atasprint", false, alpha, beta);
            rowOf(alpha, owner);
            lockable(owner);

            // Act
            claimService.updateSeries(user, organizationId, alpha.getId(), loose.getId(), request);

            // Assert
            assertSame(created, loose.getSeries());
            assertEquals(List.of(alpha.getId()), boardIds(created));
            assertEquals(List.of(beta.getId()), boardIds(owner));
            assertFalse(owner.isEnabled());
            // The old row is deleted before the new one is inserted, or the unique board index refuses it.
            InOrder order = inOrder(entityManager, sprintSeriesRepository);
            order.verify(entityManager).refresh(owner);
            order.verify(sprintSeriesRepository).saveAndFlush(owner);
            order.verify(sprintSeriesRepository).saveAndFlush(created);
        }

        @Test
        void link_boardInEnabledSeries_throwsKaiSta018() {
            // Arrange
            SprintSeries owner = otherSeries("Betasprint", true, beta);
            rowOf(beta, owner);

            // Act
            ConflictException exception = assertThrows(ConflictException.class,
                    () -> claimService.onBoardLinked(ahead, beta));

            // Assert
            assertTakenBy(exception, beta, owner);
            assertEquals(List.of(alpha.getId()), boardIds(series));
            verify(sprintSeriesRepository, never()).saveAndFlush(any());
        }

        @Test
        void link_boardInDisabledSeries_movesTheRowToTheSprintsSeries() {
            // Arrange
            SprintSeries owner = otherSeries("Betasprint", false, beta);
            rowOf(beta, owner);
            lockable(owner);

            // Act
            claimService.onBoardLinked(ahead, beta);

            // Assert
            assertEquals(List.of(alpha.getId(), beta.getId()), boardIds(series));
            assertEquals(1, positionOf(series, beta));
            assertTrue(owner.getBoards().isEmpty());
            verify(sprintSeriesRepository).saveAndFlush(owner);
            verify(sprintSeriesRepository).saveAndFlush(series);
        }

        @Test
        void link_boardAlreadyInTheSameSeries_isANoOp() {
            // Arrange
            rowOf(alpha, series);

            // Act
            claimService.onBoardLinked(ahead, alpha);

            // Assert
            assertEquals(1, series.getBoards().size());
            verify(sprintSeriesRepository, never()).saveAndFlush(any());
        }

        @Test
        void link_completedSprintOrSprintWithoutSeries_isNotChecked() {
            // Arrange
            active.setStatus(SprintStatus.COMPLETED);

            // Act
            claimService.onBoardLinked(active, beta);
            claimService.onBoardLinked(looseSprint(alpha), beta);

            // Assert
            verifyNoInteractions(sprintSeriesBoardRepository, sprintSeriesRepository);
        }

        @Test
        void reEnable_linkedBoardMovedToEnabledSeries_throwsKaiSta018NamingIt() {
            // Arrange
            SprintSeriesRequest request = repeat(true);
            arrangeReEnable(request);
            SprintSeries owner = otherSeries("Betasprint", true, beta);
            rowOf(beta, owner);

            // Act
            ConflictException exception = assertThrows(ConflictException.class,
                    () -> claimService.updateSeries(user, organizationId, alpha.getId(), active.getId(), request));

            // Assert
            assertTakenBy(exception, beta, owner);
            verify(sprintSeriesRepository, never()).save(any());
            verify(sprintSeriesRepository, never()).saveAndFlush(any());
        }

        @Test
        void reEnable_linkedBoardMovedToDisabledSeries_movesItBack() {
            // Arrange
            SprintSeriesRequest request = repeat(true);
            arrangeReEnable(request);
            SprintSeries owner = otherSeries("Betasprint", false, beta);
            rowOf(beta, owner);
            lockable(owner);

            // Act
            claimService.updateSeries(user, organizationId, alpha.getId(), active.getId(), request);

            // Assert
            assertEquals(List.of(alpha.getId(), beta.getId()), boardIds(series));
            assertTrue(owner.getBoards().isEmpty());
            assertTrue(series.isEnabled());
        }

        @Test
        void reEnable_linkedBoardInNoSeries_isAddedAtTheLastPosition() {
            // Arrange
            SprintSeriesRequest request = repeat(true);
            arrangeReEnable(request);
            free(beta);

            // Act
            claimService.updateSeries(user, organizationId, alpha.getId(), active.getId(), request);

            // Assert
            assertEquals(List.of(alpha.getId(), beta.getId()), boardIds(series));
            assertEquals(1, positionOf(series, beta));
        }

        @Test
        void saveBoards_lostRaceOnTheBoardIndex_throwsKaiSta018WithoutTheSeries() {
            // Arrange
            free(beta);
            when(sprintSeriesRepository.saveAndFlush(series))
                    .thenThrow(violation(SprintRecurrenceConstant.SERIES_BOARD_UNIQUE_INDEX));

            // Act
            ConflictException exception = assertThrows(ConflictException.class,
                    () -> claimService.onBoardLinked(ahead, beta));

            // Assert
            assertEquals(ExceptionDomainKaizenError.STA_BOARD_IN_OTHER_SPRINT_SERIES.getCode(),
                    exception.getErrorCode());
            assertEquals(beta.getId(), exception.getErrorValues().get(SprintConstant.FIELD_BOARD_ID));
            assertEquals("Beta", exception.getErrorValues().get(SprintConstant.FIELD_BOARD_NAME));
            assertFalse(exception.getErrorValues().containsKey(SprintRecurrenceConstant.FIELD_SERIES_NAME));
            assertFalse(exception.getErrorValues().containsKey(SprintRecurrenceConstant.FIELD_SERIES_ID));
        }

        @Test
        void saveBoards_otherIntegrityViolation_isRethrownUnchanged() {
            // Arrange
            free(beta);
            DataIntegrityViolationException other = violation("sprint_series_boards_series_id_board_id_uk");
            when(sprintSeriesRepository.saveAndFlush(series)).thenThrow(other);

            // Act
            DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                    () -> claimService.onBoardLinked(ahead, beta));

            // Assert
            assertSame(other, thrown);
            assertNotNull(thrown.getCause());
        }

        private DataIntegrityViolationException violation(String constraintName) {
            return new DataIntegrityViolationException("could not execute statement",
                    new ConstraintViolationException("duplicate key", new SQLException("duplicate key"),
                            constraintName));
        }
    }
}
