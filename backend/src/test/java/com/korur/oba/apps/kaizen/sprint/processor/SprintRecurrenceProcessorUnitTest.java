package com.korur.oba.apps.kaizen.sprint.processor;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.korur.oba.apps.kaizen.sprint.mock_data.SprintRecurrenceMocks.atEndSeries;
import static com.korur.oba.apps.kaizen.sprint.mock_data.SprintRecurrenceMocks.board;
import static com.korur.oba.apps.kaizen.sprint.mock_data.SprintRecurrenceMocks.inSeries;
import static com.korur.oba.apps.kaizen.sprint.mock_data.SprintRecurrenceMocks.linkedTo;
import static com.korur.oba.apps.kaizen.sprint.mock_data.SprintRecurrenceMocks.series;
import static com.korur.oba.apps.kaizen.sprint.mock_data.SprintRecurrenceMocks.sprint;
import static com.korur.oba.apps.kaizen.sprint.mock_data.SprintRecurrenceMocks.ticket;
import static com.korur.oba.apps.kaizen.sprint.mock_data.SprintRecurrenceMocks.utc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.backlog.entity.Backlog;
import com.korur.oba.apps.kaizen.backlog.validator.BacklogValidator;
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.board.entity.BoardSprint;
import com.korur.oba.apps.kaizen.board.repository.BoardSprintRepository;
import com.korur.oba.apps.kaizen.sprint.constant.SprintRecurrenceConstant;
import com.korur.oba.apps.kaizen.sprint.entity.Sprint;
import com.korur.oba.apps.kaizen.sprint.entity.SprintSeries;
import com.korur.oba.apps.kaizen.sprint.enums.SprintRecurrenceCreationMode;
import com.korur.oba.apps.kaizen.sprint.enums.SprintRecurrenceRunStatus;
import com.korur.oba.apps.kaizen.sprint.enums.SprintRecurrenceTicketAction;
import com.korur.oba.apps.kaizen.sprint.enums.SprintStatus;
import com.korur.oba.apps.kaizen.sprint.helper.SprintLifecycleHelper;
import com.korur.oba.apps.kaizen.sprint.helper.SprintRecurrenceCalculator;
import com.korur.oba.apps.kaizen.sprint.repository.SprintRepository;
import com.korur.oba.apps.kaizen.sprint.repository.SprintSeriesRepository;
import com.korur.oba.apps.kaizen.sprint.validator.SprintValidator;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.apps.kaizen.ticket.enums.TicketRelocationAction;
import com.korur.oba.apps.kaizen.ticket.enums.TicketStatus;
import com.korur.oba.apps.kaizen.ticket.helper.TicketHelper;
import com.korur.oba.apps.kaizen.ticket.repository.TicketRepository;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Runs the processor over the real lifecycle helper and calculator, so a test
 * sees the state a run leaves on the series and its sprints. The repositories
 * and the ticket helper are mocks.
 */
@ExtendWith(MockitoExtension.class)
class SprintRecurrenceProcessorUnitTest {

    @Mock
    private SprintRepository sprintRepository;

    @Mock
    private SprintSeriesRepository sprintSeriesRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private BoardSprintRepository boardSprintRepository;

    @Mock
    private SprintValidator sprintValidator;

    @Mock
    private BacklogValidator backlogValidator;

    @Mock
    private TicketHelper ticketHelper;

    @Mock
    private LoggingService loggingService;

    private SprintRecurrenceProcessor processor;

    private final Board alpha = board("Alpha");
    private final Board beta = board("Beta");

    @BeforeEach
    void setUp() {
        SprintLifecycleHelper lifecycleHelper = new SprintLifecycleHelper(
                sprintRepository, sprintSeriesRepository, boardSprintRepository, ticketRepository,
                sprintValidator, backlogValidator, ticketHelper, loggingService);
        processor = new SprintRecurrenceProcessor(sprintSeriesRepository, sprintRepository, ticketRepository,
                lifecycleHelper, new SprintRecurrenceCalculator(), loggingService);
        lenient().when(sprintSeriesRepository.save(any(SprintSeries.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(sprintRepository.save(any(Sprint.class))).thenAnswer(invocation -> {
            Sprint saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
        lenient().when(sprintRepository.saveAndFlush(any(Sprint.class))).thenAnswer(invocation -> {
            Sprint saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
    }

    /**
     * Returns the series from the job's lock and the given sprints, highest
     * round first, from the series query.
     */
    private void arrange(SprintSeries series, Sprint... seriesSprints) {
        when(sprintSeriesRepository.findByIdForUpdateSkipLocked(series.getId())).thenReturn(Optional.of(series));
        when(sprintRepository.findBySeries_IdOrderByRoundDesc(series.getId())).thenReturn(List.of(seriesSprints));
    }

    private void blockBoard(Board board, Sprint activeSprint) {
        when(sprintRepository.findIdsByBoardIdsAndStatus(List.of(board.getId()), SprintStatus.ACTIVE))
                .thenReturn(List.of(activeSprint.getId()));
    }

    /**
     * The sprint a run saved as new under the given name.
     */
    private Sprint createdSprint(String name) {
        ArgumentCaptor<Sprint> saved = ArgumentCaptor.forClass(Sprint.class);
        verify(sprintRepository, atLeastOnce()).saveAndFlush(saved.capture());
        return saved.getAllValues().stream()
                .filter(candidate -> name.equals(candidate.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no sprint named " + name + " was saved"));
    }

    private Sprint createdSprint() {
        return createdSprint("Atasprint 2");
    }

    private static List<Board> boardsOf(Sprint sprint) {
        return sprint.getBoardSprints().stream().map(BoardSprint::getBoard).toList();
    }

    private Sprint activeRoundOne(SprintSeries series, Board... boards) {
        return inSeries(linkedTo(sprint(SprintStatus.ACTIVE, utc("2026-01-01"), utc("2026-01-08")), boards), series, 1);
    }

    private ActiveFixture activeSeriesOnAlpha() {
        SprintSeries series = atEndSeries(alpha);
        Sprint closing = activeRoundOne(series, alpha);
        arrange(series, closing);
        return new ActiveFixture(closing, series);
    }

    private record ActiveFixture(Sprint closing, SprintSeries series) {
    }

    @Nested
    class Rollover {

        @Test
        void unfinishedTicketsMoveToNextSprint_closesAndStartsTheNextRound() {
            ActiveFixture fixture = activeSeriesOnAlpha();
            List<Ticket> unfinished = List.of(
                    ticket(alpha, fixture.closing(), TicketStatus.TODO),
                    ticket(alpha, fixture.closing(), TicketStatus.IN_PROGRESS));
            when(ticketRepository.findBySprint_IdAndStatusNot(fixture.closing().getId(), TicketStatus.DONE))
                    .thenReturn(unfinished);

            processor.runInNewTransaction(fixture.series().getId(), utc("2026-01-08"));

            Sprint next = createdSprint();
            assertEquals(SprintStatus.COMPLETED, fixture.closing().getStatus());
            assertEquals(SprintStatus.ACTIVE, next.getStatus());
            assertEquals(2, next.getRound());
            assertSame(fixture.series(), next.getSeries());
            assertEquals(utc("2026-01-08"), next.getStartDate());
            assertEquals(utc("2026-01-15"), next.getEndDate());
            assertEquals(3, fixture.series().getNextRound());
            verify(ticketHelper).relocateTickets(unfinished, TicketRelocationAction.MOVE_TO_SPRINT,
                    null, next, null, fixture.closing().getId());
            assertEquals(SprintRecurrenceRunStatus.OK, fixture.series().getLastRunStatus());
            assertEquals(utc("2026-01-08"), fixture.series().getLastRunAt());
            assertNull(fixture.series().getNextCreationAt());
        }

        @Test
        void waitingForThirdPartyTicket_movesToNextSprintLikeAnyNotDoneTicket() {
            ActiveFixture fixture = activeSeriesOnAlpha();
            List<Ticket> unfinished = List.of(
                    ticket(alpha, fixture.closing(), TicketStatus.WAITING_FOR_THIRD_PARTY));
            when(ticketRepository.findBySprint_IdAndStatusNot(fixture.closing().getId(), TicketStatus.DONE))
                    .thenReturn(unfinished);

            processor.runInNewTransaction(fixture.series().getId(), utc("2026-01-08"));

            Sprint next = createdSprint();
            assertEquals(SprintStatus.COMPLETED, fixture.closing().getStatus());
            verify(ticketHelper).relocateTickets(unfinished, TicketRelocationAction.MOVE_TO_SPRINT,
                    null, next, null, fixture.closing().getId());
        }

        @Test
        void doneTicketsStayBehind_onlyNotDoneAreQueried() {
            ActiveFixture fixture = activeSeriesOnAlpha();
            when(ticketRepository.findBySprint_IdAndStatusNot(fixture.closing().getId(), TicketStatus.DONE))
                    .thenReturn(List.of());

            processor.runInNewTransaction(fixture.series().getId(), utc("2026-01-08"));

            verify(ticketRepository).findBySprint_IdAndStatusNot(fixture.closing().getId(), TicketStatus.DONE);
            verify(ticketHelper, never()).relocateTickets(any(), any(), any(), any(), any(), any());
            assertEquals(SprintStatus.COMPLETED, fixture.closing().getStatus());
        }

        @Test
        void unfinishedTicketsMoveToBacklog_eachBoardGetsItsOwnBacklog() {
            SprintSeries series = series(SprintRecurrenceTicketAction.MOVE_TO_BACKLOG,
                    SprintRecurrenceCreationMode.AT_END, null, 7, alpha, beta);
            Sprint closing = activeRoundOne(series, alpha, beta);
            arrange(series, closing);
            Ticket onAlpha = ticket(alpha, closing, TicketStatus.TODO);
            Ticket onBeta = ticket(beta, closing, TicketStatus.IN_PROGRESS);
            Ticket secondOnAlpha = ticket(alpha, closing, TicketStatus.TODO);
            when(ticketRepository.findBySprint_IdAndStatusNot(closing.getId(), TicketStatus.DONE))
                    .thenReturn(List.of(onAlpha, onBeta, secondOnAlpha));
            Backlog alphaBacklog = Backlog.builder().build();
            Backlog betaBacklog = Backlog.builder().build();
            when(backlogValidator.ensureBacklogExistsByBoardId(alpha.getId())).thenReturn(alphaBacklog);
            when(backlogValidator.ensureBacklogExistsByBoardId(beta.getId())).thenReturn(betaBacklog);

            processor.runInNewTransaction(series.getId(), utc("2026-01-08"));

            verify(ticketHelper).relocateTickets(List.of(onAlpha, secondOnAlpha),
                    TicketRelocationAction.MOVE_TO_BACKLOG, null, null, alphaBacklog, closing.getId());
            verify(ticketHelper).relocateTickets(List.of(onBeta),
                    TicketRelocationAction.MOVE_TO_BACKLOG, null, null, betaBacklog, closing.getId());
            verify(ticketHelper, never()).relocateTickets(any(), eq(TicketRelocationAction.MOVE_TO_SPRINT),
                    any(), any(), any(), any());
            assertEquals(SprintStatus.COMPLETED, closing.getStatus());
            assertEquals(SprintStatus.ACTIVE, createdSprint().getStatus());
        }

        @Test
        void seriesSharedByParentAndChildBoard_nextSprintIsLinkedToBoth() {
            Board child = board("Child");
            child.setParentBoard(alpha);
            alpha.getChildBoards().add(child);
            SprintSeries series = atEndSeries(alpha, child);
            Sprint closing = activeRoundOne(series, alpha, child);
            arrange(series, closing);

            processor.runInNewTransaction(series.getId(), utc("2026-01-08"));

            assertEquals(List.of(alpha, child), boardsOf(createdSprint()));
        }

        @Test
        void seriesBoards_nextSprintIsLinkedInSeriesPositionOrder() {
            SprintSeries series = atEndSeries(beta, alpha);
            Sprint closing = activeRoundOne(series, alpha, beta);
            arrange(series, closing);

            processor.runInNewTransaction(series.getId(), utc("2026-01-08"));

            assertEquals(List.of(beta, alpha), boardsOf(createdSprint()));
        }

        @Test
        void deletedSeriesBoard_isLeftOutOfTheNextSprint() {
            Board deleted = board("Gone");
            deleted.setDeleted(true);
            SprintSeries series = atEndSeries(alpha, deleted);
            Sprint closing = activeRoundOne(series, alpha);
            arrange(series, closing);

            processor.runInNewTransaction(series.getId(), utc("2026-01-08"));

            assertEquals(List.of(alpha), boardsOf(createdSprint()));
        }

        @Test
        void nextSprintRunsForTheSeriesLength() {
            SprintSeries series = series(SprintRecurrenceTicketAction.MOVE_TO_NEXT_SPRINT,
                    SprintRecurrenceCreationMode.AT_END, null, 14, alpha);
            Sprint closing = activeRoundOne(series, alpha);
            arrange(series, closing);

            processor.runInNewTransaction(series.getId(), utc("2026-01-08"));

            assertEquals(utc("2026-01-22"), createdSprint().getEndDate());
        }

        @Test
        void daysBeforeSeries_schedulesTheCreationFromTheNewSprint() {
            SprintSeries series = series(SprintRecurrenceTicketAction.MOVE_TO_BACKLOG,
                    SprintRecurrenceCreationMode.DAYS_BEFORE, 2, 7, alpha);
            Sprint closing = activeRoundOne(series, alpha);
            arrange(series, closing);

            processor.runInNewTransaction(series.getId(), utc("2026-01-08"));

            assertEquals(SprintStatus.ACTIVE, createdSprint().getStatus());
            assertEquals(utc("2026-01-13"), series.getNextCreationAt());
        }

        @Test
        void blockedStatusFromAnEarlierRun_isReplacedByTheRollover() {
            ActiveFixture fixture = activeSeriesOnAlpha();
            fixture.series().setLastRunStatus(SprintRecurrenceRunStatus.BLOCKED);
            fixture.series().setLastRunMessage("Another sprint is active on: Alpha");

            processor.runInNewTransaction(fixture.series().getId(), utc("2026-01-08"));

            assertEquals(SprintRecurrenceRunStatus.OK, fixture.series().getLastRunStatus());
            assertNull(fixture.series().getLastRunMessage());
        }

        @Test
        void createdAheadSprint_isStartedKeepingItsOwnEndDate() {
            SprintSeries series = series(SprintRecurrenceTicketAction.MOVE_TO_NEXT_SPRINT,
                    SprintRecurrenceCreationMode.DAYS_BEFORE, 2, 7, alpha);
            series.setNextRound(3);
            Sprint closing = activeRoundOne(series, alpha);
            Sprint planned = inSeries(linkedTo(
                    sprint(SprintStatus.PLANNED, utc("2026-01-08"), utc("2026-01-20")), alpha), series, 2);
            arrange(series, planned, closing);
            List<Ticket> unfinished = List.of(ticket(alpha, closing, TicketStatus.TODO));
            when(ticketRepository.findBySprint_IdAndStatusNot(closing.getId(), TicketStatus.DONE))
                    .thenReturn(unfinished);

            processor.runInNewTransaction(series.getId(), utc("2026-01-08"));

            assertEquals(SprintStatus.ACTIVE, planned.getStatus());
            assertEquals(utc("2026-01-08"), planned.getStartDate());
            assertEquals(utc("2026-01-20"), planned.getEndDate());
            verify(ticketHelper).relocateTickets(unfinished, TicketRelocationAction.MOVE_TO_SPRINT,
                    null, planned, null, closing.getId());
            verify(sprintRepository, never()).saveAndFlush(any(Sprint.class));
            assertEquals(3, series.getNextRound());
            assertEquals(utc("2026-01-18"), series.getNextCreationAt());
        }

        @Test
        void createdAheadSprintOnFewerBoards_isLinkedToEverySeriesBoard() {
            SprintSeries series = atEndSeries(alpha, beta);
            Sprint closing = activeRoundOne(series, alpha, beta);
            Sprint planned = inSeries(linkedTo(
                    sprint(SprintStatus.PLANNED, utc("2026-01-08"), utc("2026-01-15")), alpha), series, 2);
            arrange(series, planned, closing);
            when(boardSprintRepository.findDeletedByBoardIdAndSprintId(beta.getId(), planned.getId()))
                    .thenReturn(Optional.empty());

            processor.runInNewTransaction(series.getId(), utc("2026-01-08"));

            assertEquals(List.of(alpha, beta), boardsOf(planned));
        }

        @Test
        void createdAheadSprintDeleted_aNewSprintGetsTheNextUnusedRound() {
            // Round 2 was created ahead and deleted; rounds are never reused.
            SprintSeries series = atEndSeries(alpha);
            series.setNextRound(3);
            Sprint closing = activeRoundOne(series, alpha);
            arrange(series, closing);

            processor.runInNewTransaction(series.getId(), utc("2026-01-08"));

            Sprint next = createdSprint("Atasprint 3");
            assertEquals(SprintStatus.ACTIVE, next.getStatus());
            assertEquals(3, next.getRound());
            assertEquals(4, series.getNextRound());
        }

        @Test
        void downOverThreePeriods_rollsOverOnceIntoCurrentPeriod() {
            ActiveFixture fixture = activeSeriesOnAlpha();

            processor.runInNewTransaction(fixture.series().getId(), Instant.parse("2026-01-30T10:00:00Z"));

            Sprint next = createdSprint();
            assertEquals(utc("2026-01-29"), next.getStartDate());
            assertEquals(utc("2026-02-05"), next.getEndDate());
            assertEquals(SprintStatus.ACTIVE, next.getStatus());
            assertEquals(SprintStatus.COMPLETED, fixture.closing().getStatus());
        }

        @Test
        void catchUpWithCreatedAheadSprint_resetsItsEndToOneLengthAfterTheNewStart() {
            SprintSeries series = atEndSeries(alpha);
            Sprint closing = activeRoundOne(series, alpha);
            Sprint planned = inSeries(linkedTo(
                    sprint(SprintStatus.PLANNED, utc("2026-01-08"), utc("2026-01-20")), alpha), series, 2);
            arrange(series, planned, closing);

            processor.runInNewTransaction(series.getId(), Instant.parse("2026-01-30T10:00:00Z"));

            assertEquals(utc("2026-01-29"), planned.getStartDate());
            assertEquals(utc("2026-02-05"), planned.getEndDate());
        }

        @Test
        void retryLaterInMidnightHour_stillRollsOverToTheEndDate() {
            ActiveFixture fixture = activeSeriesOnAlpha();

            processor.runInNewTransaction(fixture.series().getId(), Instant.parse("2026-01-08T00:45:00Z"));

            assertEquals(utc("2026-01-08"), createdSprint().getStartDate());
        }

        @Test
        void daysBeforeDoesNotFitCreatedAheadSprint_noCreationTimeAndWarns() {
            SprintSeries series = series(SprintRecurrenceTicketAction.MOVE_TO_NEXT_SPRINT,
                    SprintRecurrenceCreationMode.DAYS_BEFORE, 10, 14, alpha);
            Sprint closing = inSeries(linkedTo(
                    sprint(SprintStatus.ACTIVE, utc("2026-01-01"), utc("2026-01-15")), alpha), series, 1);
            Sprint planned = inSeries(linkedTo(
                    sprint(SprintStatus.PLANNED, utc("2026-01-15"), utc("2026-01-18")), alpha), series, 2);
            arrange(series, planned, closing);

            processor.runInNewTransaction(series.getId(), utc("2026-01-15"));

            assertEquals(SprintStatus.ACTIVE, planned.getStatus());
            assertNull(series.getNextCreationAt());
            verify(loggingService).warn(any(), any(), any(), any(Object[].class));
        }
    }

    @Nested
    class Blocked {

        @Test
        void linkedBoardHasAnotherActiveSprint_changesNothingAndRecordsBlocked() {
            SprintSeries series = atEndSeries(alpha, beta);
            Sprint closing = activeRoundOne(series, alpha, beta);
            arrange(series, closing);
            Sprint other = sprint(SprintStatus.ACTIVE, utc("2026-01-01"), utc("2026-01-30"));
            when(sprintRepository.findIdsByBoardIdsAndStatus(List.of(alpha.getId()), SprintStatus.ACTIVE))
                    .thenReturn(List.of(closing.getId()));
            blockBoard(beta, other);

            processor.runInNewTransaction(series.getId(), utc("2026-01-08"));

            assertEquals(SprintStatus.ACTIVE, closing.getStatus());
            verify(sprintRepository, never()).save(any(Sprint.class));
            verify(sprintRepository, never()).saveAndFlush(any(Sprint.class));
            verify(ticketHelper, never()).relocateTickets(any(), any(), any(), any(), any(), any());
            assertEquals(SprintRecurrenceRunStatus.BLOCKED, series.getLastRunStatus());
            assertEquals("Another sprint is active on: Beta", series.getLastRunMessage());
            assertEquals(2, series.getNextRound());
        }

        @Test
        void severalBlockingBoards_messageNamesThemAll() {
            SprintSeries series = atEndSeries(alpha, beta);
            Sprint closing = activeRoundOne(series, alpha, beta);
            arrange(series, closing);
            Sprint other = sprint(SprintStatus.ACTIVE, utc("2026-01-01"), utc("2026-01-30"));
            blockBoard(alpha, other);
            blockBoard(beta, other);

            processor.runInNewTransaction(series.getId(), utc("2026-01-08"));

            assertEquals("Another sprint is active on: Alpha, Beta", series.getLastRunMessage());
        }

        @Test
        void sameBlockOnRetry_writesNothingAgain() {
            ActiveFixture fixture = activeSeriesOnAlpha();
            fixture.series().setLastRunStatus(SprintRecurrenceRunStatus.BLOCKED);
            fixture.series().setLastRunMessage("Another sprint is active on: Alpha");
            fixture.series().setLastRunAt(utc("2026-01-08"));
            blockBoard(alpha, sprint(SprintStatus.ACTIVE, utc("2026-01-01"), utc("2026-01-30")));

            processor.runInNewTransaction(fixture.series().getId(), Instant.parse("2026-01-08T00:15:00Z"));

            verify(sprintSeriesRepository, never()).save(any());
            assertEquals(utc("2026-01-08"), fixture.series().getLastRunAt());
        }

        @Test
        void blockOnBoardOfCreatedAheadSprint_blocksRolloverBeforeAnyChange() {
            SprintSeries series = atEndSeries(alpha);
            Sprint closing = activeRoundOne(series, alpha);
            Sprint planned = inSeries(linkedTo(
                    sprint(SprintStatus.PLANNED, utc("2026-01-08"), utc("2026-01-15")), alpha, beta), series, 2);
            arrange(series, planned, closing);
            when(sprintRepository.findIdsByBoardIdsAndStatus(List.of(alpha.getId()), SprintStatus.ACTIVE))
                    .thenReturn(List.of(closing.getId()));
            blockBoard(beta, sprint(SprintStatus.ACTIVE, utc("2026-01-01"), utc("2026-01-30")));

            processor.runInNewTransaction(series.getId(), utc("2026-01-08"));

            assertEquals(SprintStatus.ACTIVE, closing.getStatus());
            assertEquals(SprintStatus.PLANNED, planned.getStatus());
            assertEquals("Another sprint is active on: Beta", series.getLastRunMessage());
            verify(ticketHelper, never()).relocateTickets(any(), any(), any(), any(), any(), any());
        }

        @Test
        void blockOnSeriesBoardTheClosingSprintLacks_blocksTheNewSprint() {
            SprintSeries series = atEndSeries(alpha, beta);
            Sprint closing = activeRoundOne(series, alpha);
            arrange(series, closing);
            blockBoard(alpha, closing);
            blockBoard(beta, sprint(SprintStatus.ACTIVE, utc("2026-01-01"), utc("2026-01-30")));

            processor.runInNewTransaction(series.getId(), utc("2026-01-08"));

            assertEquals(SprintStatus.ACTIVE, closing.getStatus());
            verify(sprintRepository, never()).saveAndFlush(any(Sprint.class));
            assertEquals("Another sprint is active on: Beta", series.getLastRunMessage());
        }

        @Test
        void plannedSprintStartBlocked_staysPlannedAndRecordsBlocked() {
            SprintSeries series = atEndSeries(alpha);
            Sprint planned = inSeries(linkedTo(
                    sprint(SprintStatus.PLANNED, utc("2026-01-08"), utc("2026-01-15")), alpha), series, 1);
            arrange(series, planned);
            blockBoard(alpha, sprint(SprintStatus.ACTIVE, utc("2026-01-01"), utc("2026-01-30")));

            processor.runInNewTransaction(series.getId(), utc("2026-01-08"));

            assertEquals(SprintStatus.PLANNED, planned.getStatus());
            assertEquals(SprintRecurrenceRunStatus.BLOCKED, series.getLastRunStatus());
            assertEquals("Another sprint is active on: Alpha", series.getLastRunMessage());
        }
    }

    @Nested
    class StartStep {

        @Test
        void plannedSprintAtStartDate_startsAndComputesCreationTime() {
            // The sprint created ahead after an early manual close of its predecessor.
            SprintSeries series = series(SprintRecurrenceTicketAction.MOVE_TO_NEXT_SPRINT,
                    SprintRecurrenceCreationMode.DAYS_BEFORE, 2, 7, alpha);
            Sprint done = inSeries(linkedTo(
                    sprint(SprintStatus.COMPLETED, utc("2026-01-01"), utc("2026-01-06")), alpha), series, 1);
            Sprint planned = inSeries(linkedTo(
                    sprint(SprintStatus.PLANNED, utc("2026-01-08"), utc("2026-01-15")), alpha), series, 2);
            arrange(series, planned, done);

            processor.runInNewTransaction(series.getId(), utc("2026-01-08"));

            assertEquals(SprintStatus.ACTIVE, planned.getStatus());
            assertEquals(utc("2026-01-13"), series.getNextCreationAt());
            assertEquals(SprintRecurrenceRunStatus.OK, series.getLastRunStatus());
            verify(sprintRepository, never()).saveAndFlush(any(Sprint.class));
        }

        @Test
        void blockedBeforeThenFree_startsAndReplacesBlockedStatus() {
            SprintSeries series = atEndSeries(alpha);
            series.setLastRunStatus(SprintRecurrenceRunStatus.BLOCKED);
            series.setLastRunMessage("Another sprint is active on: Alpha");
            Sprint planned = inSeries(linkedTo(
                    sprint(SprintStatus.PLANNED, utc("2026-01-08"), utc("2026-01-15")), alpha), series, 1);
            arrange(series, planned);

            processor.runInNewTransaction(series.getId(), utc("2026-01-09"));

            assertEquals(SprintStatus.ACTIVE, planned.getStatus());
            assertEquals(SprintRecurrenceRunStatus.OK, series.getLastRunStatus());
            assertNull(series.getLastRunMessage());
        }

        @Test
        void plannedSprintStartedAWholePeriodLate_alsoRollsOverInTheSameRun() {
            SprintSeries series = atEndSeries(alpha);
            Sprint planned = inSeries(linkedTo(
                    sprint(SprintStatus.PLANNED, utc("2026-01-01"), utc("2026-01-08")), alpha), series, 1);
            arrange(series, planned);

            processor.runInNewTransaction(series.getId(), utc("2026-01-09"));

            assertEquals(SprintStatus.COMPLETED, planned.getStatus());
            Sprint next = createdSprint();
            assertEquals(SprintStatus.ACTIVE, next.getStatus());
            assertEquals(utc("2026-01-08"), next.getStartDate());
        }

        @Test
        void plannedSprintStartedPastItsCreationTime_alsoCreatesTheNextSprintAhead() {
            SprintSeries series = series(SprintRecurrenceTicketAction.MOVE_TO_NEXT_SPRINT,
                    SprintRecurrenceCreationMode.DAYS_BEFORE, 2, 7, alpha);
            Sprint planned = inSeries(linkedTo(
                    sprint(SprintStatus.PLANNED, utc("2026-01-08"), utc("2026-01-15")), alpha), series, 1);
            arrange(series, planned);

            processor.runInNewTransaction(series.getId(), utc("2026-01-14"));

            assertEquals(SprintStatus.ACTIVE, planned.getStatus());
            Sprint next = createdSprint();
            assertEquals(SprintStatus.PLANNED, next.getStatus());
            assertEquals(utc("2026-01-15"), next.getStartDate());
            assertEquals(utc("2026-01-22"), next.getEndDate());
            assertNull(series.getNextCreationAt());
        }

        @Test
        void plannedSprintBeforeStartDate_isNotTouched() {
            SprintSeries series = atEndSeries(alpha);
            Sprint planned = inSeries(linkedTo(
                    sprint(SprintStatus.PLANNED, utc("2026-01-08"), utc("2026-01-15")), alpha), series, 1);
            arrange(series, planned);

            processor.runInNewTransaction(series.getId(), utc("2026-01-07"));

            assertEquals(SprintStatus.PLANNED, planned.getStatus());
            verify(sprintSeriesRepository, never()).save(any());
        }
    }

    @Nested
    class CreationStep {

        private SprintSeries daysBeforeSeries(int lengthDays, Board... boards) {
            SprintSeries series = series(SprintRecurrenceTicketAction.MOVE_TO_NEXT_SPRINT,
                    SprintRecurrenceCreationMode.DAYS_BEFORE, 2, lengthDays, boards);
            series.setNextCreationAt(utc("2026-01-06"));
            return series;
        }

        @Test
        void daysBeforeReached_createsPlannedNextSprintAheadAndKeepsCurrentActive() {
            SprintSeries series = daysBeforeSeries(7, alpha, beta);
            Sprint current = activeRoundOne(series, alpha, beta);
            arrange(series, current);

            processor.runInNewTransaction(series.getId(), utc("2026-01-06"));

            Sprint next = createdSprint();
            assertEquals(SprintStatus.PLANNED, next.getStatus());
            assertEquals(2, next.getRound());
            assertEquals(utc("2026-01-08"), next.getStartDate());
            assertEquals(utc("2026-01-15"), next.getEndDate());
            assertEquals(List.of(alpha, beta), boardsOf(next));
            assertEquals(SprintStatus.ACTIVE, current.getStatus());
            assertEquals(3, series.getNextRound());
            assertNull(series.getNextCreationAt());
            assertEquals(SprintRecurrenceRunStatus.OK, series.getLastRunStatus());
            verify(ticketHelper, never()).relocateTickets(any(), any(), any(), any(), any(), any());
        }

        @Test
        void createdSprintRunsForTheSeriesLength() {
            SprintSeries series = daysBeforeSeries(14, alpha);
            Sprint current = activeRoundOne(series, alpha);
            arrange(series, current);

            processor.runInNewTransaction(series.getId(), utc("2026-01-06"));

            assertEquals(utc("2026-01-22"), createdSprint().getEndDate());
        }

        @Test
        void renamedSeries_namesTheCreatedSprintAfterTheNewName() {
            SprintSeries series = daysBeforeSeries(7, alpha);
            series.setName("Korursprint");
            series.setNextRound(3);
            Sprint current = inSeries(linkedTo(
                    sprint(SprintStatus.ACTIVE, utc("2026-01-01"), utc("2026-01-08")), alpha), series, 2);
            arrange(series, current);

            processor.runInNewTransaction(series.getId(), utc("2026-01-06"));

            assertEquals(3, createdSprint("Korursprint 3").getRound());
        }

        @Test
        void existingPlannedNextSprint_isReusedNotDuplicated() {
            SprintSeries series = daysBeforeSeries(7, alpha);
            series.setNextRound(3);
            Sprint current = activeRoundOne(series, alpha);
            Sprint planned = inSeries(linkedTo(
                    sprint(SprintStatus.PLANNED, utc("2026-01-08"), utc("2026-01-15")), alpha), series, 2);
            arrange(series, planned, current);

            processor.runInNewTransaction(series.getId(), utc("2026-01-06"));

            verify(sprintRepository, never()).save(any(Sprint.class));
            verify(sprintRepository, never()).saveAndFlush(any(Sprint.class));
            assertEquals(3, series.getNextRound());
            assertNull(series.getNextCreationAt());
        }

        @Test
        void activeSprintNotYetDue_isNotTouched() {
            SprintSeries series = daysBeforeSeries(7, alpha);
            Sprint current = activeRoundOne(series, alpha);
            arrange(series, current);

            processor.runInNewTransaction(series.getId(), utc("2026-01-05"));

            verify(sprintSeriesRepository, never()).save(any());
            verify(sprintRepository, never()).save(any(Sprint.class));
        }
    }

    @Nested
    class SkippedRuns {

        @Test
        void seriesLockedByAnotherRun_isSkipped() {
            UUID id = UUID.randomUUID();
            when(sprintSeriesRepository.findByIdForUpdateSkipLocked(id)).thenReturn(Optional.empty());

            processor.runInNewTransaction(id, utc("2026-01-08"));

            verify(sprintRepository, never()).findBySeries_IdOrderByRoundDesc(any());
            verify(sprintSeriesRepository, never()).save(any());
        }

        @Test
        void switchedOffMeanwhile_isSkipped() {
            SprintSeries series = atEndSeries(alpha);
            series.setEnabled(false);
            when(sprintSeriesRepository.findByIdForUpdateSkipLocked(series.getId())).thenReturn(Optional.of(series));

            processor.runInNewTransaction(series.getId(), utc("2026-01-08"));

            verify(sprintRepository, never()).findBySeries_IdOrderByRoundDesc(any());
            verify(sprintSeriesRepository, never()).save(any());
        }

        @Test
        void onlyCompletedSprints_isSkipped() {
            SprintSeries series = atEndSeries(alpha);
            Sprint done = inSeries(linkedTo(
                    sprint(SprintStatus.COMPLETED, utc("2026-01-01"), utc("2026-01-08")), alpha), series, 1);
            arrange(series, done);

            processor.runInNewTransaction(series.getId(), utc("2026-01-20"));

            verify(sprintRepository, never()).save(any(Sprint.class));
            verify(sprintSeriesRepository, never()).save(any());
        }

        @Test
        void outdatedCreationTime_isClearedSoTheSeriesIsNoLongerDue() {
            // The sprint the time was computed from was completed by hand.
            SprintSeries series = series(SprintRecurrenceTicketAction.MOVE_TO_NEXT_SPRINT,
                    SprintRecurrenceCreationMode.DAYS_BEFORE, 2, 7, alpha);
            series.setNextCreationAt(utc("2026-01-06"));
            Sprint done = inSeries(linkedTo(
                    sprint(SprintStatus.COMPLETED, utc("2026-01-01"), utc("2026-01-08")), alpha), series, 1);
            arrange(series, done);

            processor.runInNewTransaction(series.getId(), utc("2026-01-06"));

            assertNull(series.getNextCreationAt());
            verify(sprintSeriesRepository).save(series);
            verify(sprintRepository, never()).saveAndFlush(any(Sprint.class));
            assertNull(series.getLastRunStatus());
        }

        @Test
        void sprintWithoutDates_isSkipped() {
            SprintSeries series = atEndSeries(alpha);
            Sprint undated = inSeries(linkedTo(sprint(SprintStatus.ACTIVE, null, null), alpha), series, 1);
            arrange(series, undated);

            processor.runInNewTransaction(series.getId(), utc("2026-01-08"));

            verify(sprintSeriesRepository, never()).save(any());
        }
    }

    @Nested
    class Failures {

        @Test
        void seriesWithNoBoardsLeft_isSwitchedOffWithFailedNoBoards() {
            SprintSeries series = atEndSeries();
            series.setNextCreationAt(utc("2026-01-06"));
            Sprint closing = activeRoundOne(series, alpha);
            arrange(series, closing);

            processor.runInNewTransaction(series.getId(), utc("2026-01-08"));

            assertFalse(series.isEnabled());
            assertNull(series.getNextCreationAt());
            assertEquals(SprintRecurrenceRunStatus.FAILED, series.getLastRunStatus());
            assertEquals("no boards", series.getLastRunMessage());
            assertEquals(SprintStatus.ACTIVE, closing.getStatus());
        }

        @Test
        void seriesWithNoSprintsLeft_isSwitchedOffWithFailedNoSprints() {
            SprintSeries series = atEndSeries(alpha);
            series.setNextCreationAt(utc("2026-01-06"));
            arrange(series);

            processor.runInNewTransaction(series.getId(), utc("2026-01-08"));

            assertFalse(series.isEnabled());
            assertNull(series.getNextCreationAt());
            assertEquals(SprintRecurrenceRunStatus.FAILED, series.getLastRunStatus());
            assertEquals("no sprints", series.getLastRunMessage());
        }

        @Test
        void relocationFails_exceptionPropagatesAndSprintStaysActive() {
            ActiveFixture fixture = activeSeriesOnAlpha();
            List<Ticket> unfinished = List.of(ticket(alpha, fixture.closing(), TicketStatus.TODO));
            when(ticketRepository.findBySprint_IdAndStatusNot(fixture.closing().getId(), TicketStatus.DONE))
                    .thenReturn(unfinished);
            when(ticketHelper.relocateTickets(any(), any(), any(), any(), any(), any()))
                    .thenThrow(new IllegalStateException("boom"));

            assertThrows(IllegalStateException.class,
                    () -> processor.runInNewTransaction(fixture.series().getId(), utc("2026-01-08")));

            assertEquals(SprintStatus.ACTIVE, fixture.closing().getStatus());
            assertNull(fixture.series().getLastRunStatus());
        }

        @Test
        void roundAlreadyTaken_databaseRefusalPropagatesBeforeAnySprintIsClosed() {
            // A second pod or a retried run already created round 2.
            ActiveFixture fixture = activeSeriesOnAlpha();
            doThrow(new DataIntegrityViolationException("sprints_series_id_round_uk"))
                    .when(sprintRepository).saveAndFlush(any(Sprint.class));

            assertThrows(DataIntegrityViolationException.class,
                    () -> processor.runInNewTransaction(fixture.series().getId(), utc("2026-01-08")));

            assertEquals(SprintStatus.ACTIVE, fixture.closing().getStatus());
            verify(ticketHelper, never()).relocateTickets(any(), any(), any(), any(), any(), any());
            assertNull(fixture.series().getLastRunStatus());
        }

        @Test
        void recordFailure_storesFailedWithExceptionClassAndMessage() {
            SprintSeries series = atEndSeries(alpha);
            when(sprintSeriesRepository.findByIdForUpdateSkipLocked(series.getId())).thenReturn(Optional.of(series));

            processor.recordFailureInNewTransaction(series.getId(), utc("2026-01-08"),
                    new IllegalStateException("boom"));

            assertEquals(SprintRecurrenceRunStatus.FAILED, series.getLastRunStatus());
            assertEquals("IllegalStateException: boom", series.getLastRunMessage());
            assertEquals(utc("2026-01-08"), series.getLastRunAt());
            assertTrue(series.isEnabled());
        }

        @Test
        void recordFailure_versionConflict_storesConflictMessage() {
            SprintSeries series = atEndSeries(alpha);
            when(sprintSeriesRepository.findByIdForUpdateSkipLocked(series.getId())).thenReturn(Optional.of(series));

            processor.recordFailureInNewTransaction(series.getId(), utc("2026-01-08"),
                    new ObjectOptimisticLockingFailureException(Sprint.class, UUID.randomUUID()));

            assertEquals(SprintRecurrenceRunStatus.FAILED, series.getLastRunStatus());
            assertEquals(SprintRecurrenceConstant.RUN_MESSAGE_CONCURRENT_UPDATE, series.getLastRunMessage());
        }

        @Test
        void isConcurrentUpdate_optimisticLockFailureAsCause_returnsTrue() {
            RuntimeException wrapped = new IllegalStateException("outer",
                    new jakarta.persistence.OptimisticLockException("stale"));

            assertTrue(SprintRecurrenceProcessor.isConcurrentUpdate(wrapped));
            assertFalse(SprintRecurrenceProcessor.isConcurrentUpdate(new IllegalStateException("boom")));
        }

        @Test
        void recordFailure_truncatesLongMessageToColumnLength() {
            SprintSeries series = atEndSeries(alpha);
            when(sprintSeriesRepository.findByIdForUpdateSkipLocked(series.getId())).thenReturn(Optional.of(series));

            processor.recordFailureInNewTransaction(series.getId(), utc("2026-01-08"),
                    new IllegalStateException("x".repeat(900)));

            assertEquals(500, series.getLastRunMessage().length());
        }

        @Test
        void recordFailure_seriesGoneOrLocked_doesNothing() {
            UUID id = UUID.randomUUID();
            when(sprintSeriesRepository.findByIdForUpdateSkipLocked(id)).thenReturn(Optional.empty());

            processor.recordFailureInNewTransaction(id, utc("2026-01-08"), new IllegalStateException("boom"));

            verify(sprintSeriesRepository, never()).save(any());
        }
    }
}
