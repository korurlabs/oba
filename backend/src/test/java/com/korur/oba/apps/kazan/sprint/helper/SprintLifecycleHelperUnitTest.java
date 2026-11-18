package com.korur.oba.apps.kazan.sprint.helper;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.korur.oba.apps.kazan.sprint.mock_data.SprintRecurrenceMocks.board;
import static com.korur.oba.apps.kazan.sprint.mock_data.SprintRecurrenceMocks.linkedTo;
import static com.korur.oba.apps.kazan.sprint.mock_data.SprintRecurrenceMocks.sprint;
import static com.korur.oba.apps.kazan.sprint.mock_data.SprintRecurrenceMocks.ticket;
import static com.korur.oba.apps.kazan.sprint.mock_data.SprintRecurrenceMocks.utc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 2. Third-party
import org.hibernate.exception.ConstraintViolationException;

// 3. Java SDK
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.backlog.entity.Backlog;
import com.korur.oba.apps.kazan.backlog.validator.BacklogValidator;
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.entity.BoardSprint;
import com.korur.oba.apps.kazan.board.repository.BoardSprintRepository;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.sprint.constant.SprintConstant;
import com.korur.oba.apps.kazan.sprint.entity.Sprint;
import com.korur.oba.apps.kazan.sprint.entity.SprintSeries;
import com.korur.oba.apps.kazan.sprint.helper.SprintLifecycleHelper.TakeoverResult;
import com.korur.oba.apps.kazan.sprint.enums.SprintStatus;
import com.korur.oba.apps.kazan.sprint.repository.SprintRepository;
import com.korur.oba.apps.kazan.sprint.repository.SprintSeriesRepository;
import com.korur.oba.apps.kazan.sprint.validator.SprintValidator;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.enums.TicketRelocationAction;
import com.korur.oba.apps.kazan.ticket.enums.TicketStatus;
import com.korur.oba.apps.kazan.ticket.helper.TicketHelper;
import com.korur.oba.apps.kazan.ticket.repository.TicketRepository;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class SprintLifecycleHelperUnitTest {

    @Mock
    private SprintRepository sprintRepository;

    @Mock
    private SprintSeriesRepository sprintSeriesRepository;

    @Mock
    private BoardSprintRepository boardSprintRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private BacklogValidator backlogValidator;

    @Mock
    private SprintValidator sprintValidator;

    @Mock
    private TicketHelper ticketHelper;

    @Mock
    private LoggingService loggingService;

    private SprintLifecycleHelper helper;

    @BeforeEach
    void setUp() {
        helper = new SprintLifecycleHelper(sprintRepository, sprintSeriesRepository, boardSprintRepository,
                ticketRepository, sprintValidator, backlogValidator, ticketHelper, loggingService);
        lenient().when(sprintRepository.save(any(Sprint.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(sprintRepository.saveAndFlush(any(Sprint.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Nested
    class CreateSprint {

        @Test
        void newSprintIsPlannedAndLinkedToBoardsInOrder() {
            Board first = board("Alpha");
            Board second = board("Beta");
            Sprint unsaved = Sprint.builder().name("Planning").status(SprintStatus.ACTIVE).build();

            Sprint saved = helper.createSprint(unsaved, List.of(first, second));

            assertEquals(SprintStatus.PLANNED, saved.getStatus());
            assertEquals(List.of(first, second),
                    saved.getBoardSprints().stream().map(BoardSprint::getBoard).toList());
            verify(sprintRepository).saveAndFlush(unsaved);
        }
    }

    @Nested
    class LinkSprintToBoardIfNeeded {

        @Test
        void alreadyLinked_doesNothing() {
            Board board = board("Alpha");
            Sprint sprint = linkedTo(sprint(SprintStatus.PLANNED, null, null), board);

            helper.linkSprintToBoardIfNeeded(sprint, board);

            verify(sprintRepository, never()).save(any());
            verify(sprintRepository, never()).saveAndFlush(any());
            verify(sprintValidator, never()).ensureSprintCanBeLinked(any(), any());
        }

        @Test
        void deletedLinkExists_restoresIt() {
            Board board = board("Alpha");
            Sprint sprint = sprint(SprintStatus.PLANNED, null, null);
            BoardSprint deleted = BoardSprint.builder().board(board).sprint(sprint).build();
            deleted.setId(UUID.randomUUID());
            when(boardSprintRepository.findDeletedByBoardIdAndSprintId(board.getId(), sprint.getId()))
                    .thenReturn(Optional.of(deleted));

            helper.linkSprintToBoardIfNeeded(sprint, board);

            // The fixture sprint is named "Sprint", whose slug is a fixed board segment.
            verify(boardSprintRepository).restoreByIdWithSlug(deleted.getId(), "sprint-1");
            verify(sprintRepository, never()).saveAndFlush(any());
        }

        @Test
        void newBoard_checksRelationAndLinks() {
            Board linked = board("Alpha");
            Board other = board("Beta");
            Sprint sprint = linkedTo(sprint(SprintStatus.PLANNED, null, null), linked);
            when(boardSprintRepository.findDeletedByBoardIdAndSprintId(other.getId(), sprint.getId()))
                    .thenReturn(Optional.empty());

            helper.linkSprintToBoardIfNeeded(sprint, other);

            verify(sprintValidator).ensureSprintCanBeLinked(sprint, other);
            assertEquals(2, sprint.getBoardSprints().size());
            verify(sprintRepository).saveAndFlush(sprint);
        }
    }

    @Nested
    class FindLinkedBoardsWithOtherActiveSprint {

        @Test
        void noActiveSprintOnBoards_returnsEmpty() {
            Board alpha = board("Alpha");
            Sprint sprint = linkedTo(sprint(SprintStatus.PLANNED, null, null), alpha);
            when(sprintRepository.findIdsByBoardIdsAndStatus(List.of(alpha.getId()), SprintStatus.ACTIVE))
                    .thenReturn(List.of());

            assertTrue(helper.findLinkedBoardsWithOtherActiveSprint(sprint, null).isEmpty());
        }

        @Test
        void otherActiveSprint_returnsBlockingBoardsInLinkOrder() {
            Board alpha = board("Alpha");
            Board beta = board("Beta");
            Board gamma = board("Gamma");
            Sprint sprint = linkedTo(sprint(SprintStatus.PLANNED, null, null), alpha, beta, gamma);
            Sprint other = sprint(SprintStatus.ACTIVE, null, null);
            when(sprintRepository.findIdsByBoardIdsAndStatus(List.of(alpha.getId()), SprintStatus.ACTIVE))
                    .thenReturn(List.of(other.getId()));
            when(sprintRepository.findIdsByBoardIdsAndStatus(List.of(beta.getId()), SprintStatus.ACTIVE))
                    .thenReturn(List.of());
            when(sprintRepository.findIdsByBoardIdsAndStatus(List.of(gamma.getId()), SprintStatus.ACTIVE))
                    .thenReturn(List.of(other.getId()));

            assertEquals(List.of(alpha, gamma), helper.findLinkedBoardsWithOtherActiveSprint(sprint, null));
        }

        @Test
        void sprintItselfActive_doesNotBlock() {
            Board alpha = board("Alpha");
            Sprint sprint = linkedTo(sprint(SprintStatus.ACTIVE, null, null), alpha);
            when(sprintRepository.findIdsByBoardIdsAndStatus(List.of(alpha.getId()), SprintStatus.ACTIVE))
                    .thenReturn(List.of(sprint.getId()));

            assertTrue(helper.findLinkedBoardsWithOtherActiveSprint(sprint, null).isEmpty());
        }

        @Test
        void ignoredSprint_doesNotBlock() {
            Board alpha = board("Alpha");
            Sprint closing = sprint(SprintStatus.ACTIVE, null, null);
            Sprint next = linkedTo(sprint(SprintStatus.PLANNED, null, null), alpha);
            when(sprintRepository.findIdsByBoardIdsAndStatus(List.of(alpha.getId()), SprintStatus.ACTIVE))
                    .thenReturn(List.of(closing.getId()));

            assertTrue(helper.findLinkedBoardsWithOtherActiveSprint(next, closing.getId()).isEmpty());
        }
    }

    @Nested
    class FindLinkedBoardsWithTwoActiveSprints {

        @Test
        void boardWithTwoActiveSprints_isReportedWithoutException() {
            Board alpha = board("Alpha");
            Sprint sprint = linkedTo(sprint(SprintStatus.PLANNED, null, null), alpha);
            when(sprintRepository.findIdsByBoardIdsAndStatus(List.of(alpha.getId()), SprintStatus.ACTIVE))
                    .thenReturn(List.of(UUID.randomUUID(), UUID.randomUUID()));

            assertEquals(List.of(alpha), helper.findLinkedBoardsWithOtherActiveSprint(sprint, null));
        }

        @Test
        void twoActiveSprintsOneIgnored_otherStillBlocks() {
            Board alpha = board("Alpha");
            Sprint sprint = linkedTo(sprint(SprintStatus.PLANNED, null, null), alpha);
            UUID ignored = UUID.randomUUID();
            when(sprintRepository.findIdsByBoardIdsAndStatus(List.of(alpha.getId()), SprintStatus.ACTIVE))
                    .thenReturn(List.of(ignored, UUID.randomUUID()));

            assertEquals(List.of(alpha), helper.findLinkedBoardsWithOtherActiveSprint(sprint, ignored));
        }
    }

    @Nested
    class FindOtherActiveSprintsOnLinkedBoards {

        @Test
        void boardWithTwoActiveSprints_returnsBothAndExcludesTheSprintItself() {
            Board alpha = board("Alpha");
            Board beta = board("Beta");
            Sprint taking = linkedTo(sprint(SprintStatus.PLANNED, null, null), alpha, beta);
            Sprint first = sprint(SprintStatus.ACTIVE, null, null);
            Sprint second = sprint(SprintStatus.ACTIVE, null, null);
            when(sprintRepository.findIdsByBoardIdsAndStatus(List.of(alpha.getId(), beta.getId()), SprintStatus.ACTIVE))
                    .thenReturn(List.of(first.getId(), second.getId(), taking.getId()));
            when(sprintRepository.findAllById(List.of(first.getId(), second.getId())))
                    .thenReturn(List.of(first, second));

            List<Sprint> result = helper.findOtherActiveSprintsOnLinkedBoards(taking);

            assertEquals(List.of(first, second), result);
        }

        @Test
        void onlyTheSprintItselfActive_returnsEmptyWithoutLoading() {
            Board alpha = board("Alpha");
            Sprint taking = linkedTo(sprint(SprintStatus.ACTIVE, null, null), alpha);
            when(sprintRepository.findIdsByBoardIdsAndStatus(List.of(alpha.getId()), SprintStatus.ACTIVE))
                    .thenReturn(List.of(taking.getId()));

            assertTrue(helper.findOtherActiveSprintsOnLinkedBoards(taking).isEmpty());
            verify(sprintRepository, never()).findAllById(any());
        }

        @Test
        void sprintWithoutLinks_returnsEmptyWithoutQuery() {
            Sprint taking = sprint(SprintStatus.PLANNED, null, null);

            assertTrue(helper.findOtherActiveSprintsOnLinkedBoards(taking).isEmpty());
            verifyNoInteractions(sprintRepository);
        }
    }

    @Nested
    class TakeOverAndStart {

        @Test
        void noBlockingSprint_justStarts() {
            Sprint taking = linkedTo(sprint(SprintStatus.PLANNED, null, null), board("Alpha"));

            TakeoverResult result = helper.takeOverAndStart(taking, List.of());

            assertEquals(SprintStatus.ACTIVE, result.sprint().getStatus());
            assertEquals(0, result.closedSprintCount());
            assertEquals(0, result.movedTicketCount());
            assertEquals(0, result.backlogTicketCount());
            verifyNoInteractions(sprintSeriesRepository, ticketRepository, ticketHelper);
        }

        @Test
        void unfinishedTicketsOnLinkedBoard_moveToTakingSprintKeepingBoard() {
            Board alpha = board("Alpha");
            Sprint taking = linkedTo(sprint(SprintStatus.PLANNED, null, null), alpha);
            Sprint blocking = linkedTo(sprint(SprintStatus.ACTIVE, null, null), alpha);
            Ticket open = ticket(alpha, blocking, TicketStatus.IN_PROGRESS);
            when(ticketRepository.findBySprint_IdAndStatusNot(blocking.getId(), TicketStatus.DONE))
                    .thenReturn(List.of(open));

            TakeoverResult result = helper.takeOverAndStart(taking, List.of(blocking));

            verify(ticketHelper).relocateTickets(List.of(open), TicketRelocationAction.MOVE_TO_SPRINT,
                    null, taking, null, blocking.getId());
            verify(backlogValidator, never()).ensureBacklogExistsByBoardId(any());
            assertEquals(SprintStatus.COMPLETED, blocking.getStatus());
            assertEquals(SprintStatus.ACTIVE, taking.getStatus());
            assertEquals(1, result.closedSprintCount());
            assertEquals(1, result.movedTicketCount());
            assertEquals(0, result.backlogTicketCount());
        }

        @Test
        void doneTicketsAreNotQueriedForMove_soTheyStayInTheClosedSprint() {
            Board alpha = board("Alpha");
            Sprint taking = linkedTo(sprint(SprintStatus.PLANNED, null, null), alpha);
            Sprint blocking = linkedTo(sprint(SprintStatus.ACTIVE, null, null), alpha);
            when(ticketRepository.findBySprint_IdAndStatusNot(blocking.getId(), TicketStatus.DONE))
                    .thenReturn(List.of());

            helper.takeOverAndStart(taking, List.of(blocking));

            verify(ticketRepository).findBySprint_IdAndStatusNot(blocking.getId(), TicketStatus.DONE);
            verify(ticketHelper, never()).relocateTickets(any(), any(), any(), any(), any(), any());
            assertEquals(SprintStatus.COMPLETED, blocking.getStatus());
        }

        @Test
        void ticketOnUnlinkedBoard_goesToItsOwnBacklog() {
            Board alpha = board("Alpha");
            Board other = board("Other");
            Sprint taking = linkedTo(sprint(SprintStatus.PLANNED, null, null), alpha);
            Sprint blocking = linkedTo(sprint(SprintStatus.ACTIVE, null, null), alpha, other);
            Ticket onAlpha = ticket(alpha, blocking, TicketStatus.TODO);
            Ticket onOther = ticket(other, blocking, TicketStatus.IN_PROGRESS);
            Backlog otherBacklog = Backlog.builder().build();
            when(ticketRepository.findBySprint_IdAndStatusNot(blocking.getId(), TicketStatus.DONE))
                    .thenReturn(List.of(onAlpha, onOther));
            when(backlogValidator.ensureBacklogExistsByBoardId(other.getId())).thenReturn(otherBacklog);

            TakeoverResult result = helper.takeOverAndStart(taking, List.of(blocking));

            verify(ticketHelper).relocateTickets(List.of(onOther), TicketRelocationAction.MOVE_TO_BACKLOG,
                    null, null, otherBacklog, blocking.getId());
            verify(ticketHelper).relocateTickets(List.of(onAlpha), TicketRelocationAction.MOVE_TO_SPRINT,
                    null, taking, null, blocking.getId());
            assertEquals(1, result.movedTicketCount());
            assertEquals(1, result.backlogTicketCount());
        }

        @Test
        void parentAndChildBlockingSprints_bothClosedAndCounted() {
            Board parent = board("Parent");
            Board child = board("Child");
            Sprint taking = linkedTo(sprint(SprintStatus.PLANNED, null, null), parent, child);
            Sprint parentSprint = linkedTo(sprint(SprintStatus.ACTIVE, null, null), parent);
            Sprint childSprint = linkedTo(sprint(SprintStatus.ACTIVE, null, null), child);
            Ticket parentTicket = ticket(parent, parentSprint, TicketStatus.TODO);
            Ticket childTicket = ticket(child, childSprint, TicketStatus.IN_REVIEW);
            when(ticketRepository.findBySprint_IdAndStatusNot(parentSprint.getId(), TicketStatus.DONE))
                    .thenReturn(List.of(parentTicket));
            when(ticketRepository.findBySprint_IdAndStatusNot(childSprint.getId(), TicketStatus.DONE))
                    .thenReturn(List.of(childTicket));

            TakeoverResult result = helper.takeOverAndStart(taking, List.of(parentSprint, childSprint));

            assertEquals(SprintStatus.COMPLETED, parentSprint.getStatus());
            assertEquals(SprintStatus.COMPLETED, childSprint.getStatus());
            assertEquals(SprintStatus.ACTIVE, taking.getStatus());
            assertEquals(2, result.closedSprintCount());
            assertEquals(2, result.movedTicketCount());
        }

        @Test
        void seriesRowIsLockedBeforeTheSprintIsWritten() {
            Board alpha = board("Alpha");
            Sprint taking = linkedTo(sprint(SprintStatus.PLANNED, null, null), alpha);
            Sprint blocking = linkedTo(sprint(SprintStatus.ACTIVE, null, null), alpha);
            SprintSeries series = SprintSeries.builder().name("Atasprint").nextRound(2).build();
            series.setId(UUID.randomUUID());
            blocking.setSeries(series);
            blocking.setRound(1);
            when(ticketRepository.findBySprint_IdAndStatusNot(blocking.getId(), TicketStatus.DONE))
                    .thenReturn(List.of());

            helper.takeOverAndStart(taking, List.of(blocking));

            InOrder order = inOrder(sprintSeriesRepository, ticketRepository, sprintRepository);
            order.verify(sprintSeriesRepository).findByIdForUpdate(series.getId());
            order.verify(ticketRepository).findBySprint_IdAndStatusNot(blocking.getId(), TicketStatus.DONE);
            order.verify(sprintRepository).save(blocking);
            order.verify(sprintRepository).save(taking);
        }
    }

    @Nested
    class MoveToEachBoardBacklog {

        @Test
        void ticketsOfTwoBoards_areMovedPerBoardToTheirOwnBacklog() {
            Board alpha = board("Alpha");
            Board beta = board("Beta");
            Sprint source = sprint(SprintStatus.ACTIVE, null, null);
            Ticket a1 = ticket(alpha, source, TicketStatus.TODO);
            Ticket a2 = ticket(alpha, source, TicketStatus.IN_PROGRESS);
            Ticket b1 = ticket(beta, source, TicketStatus.TODO);
            Backlog alphaBacklog = Backlog.builder().build();
            Backlog betaBacklog = Backlog.builder().build();
            when(backlogValidator.ensureBacklogExistsByBoardId(alpha.getId())).thenReturn(alphaBacklog);
            when(backlogValidator.ensureBacklogExistsByBoardId(beta.getId())).thenReturn(betaBacklog);

            helper.moveToEachBoardBacklog(List.of(a1, b1, a2), source.getId());

            verify(ticketHelper).relocateTickets(List.of(a1, a2), TicketRelocationAction.MOVE_TO_BACKLOG,
                    null, null, alphaBacklog, source.getId());
            verify(ticketHelper).relocateTickets(List.of(b1), TicketRelocationAction.MOVE_TO_BACKLOG,
                    null, null, betaBacklog, source.getId());
        }

        @Test
        void noTickets_doesNothing() {
            helper.moveToEachBoardBacklog(List.of(), UUID.randomUUID());

            verifyNoInteractions(backlogValidator, ticketHelper);
        }
    }

    @Nested
    class StartSprint {

        @Test
        void plannedSprint_becomesActive() {
            Sprint sprint = sprint(SprintStatus.PLANNED, utc("2026-01-01"), utc("2026-01-08"));

            Sprint result = helper.startSprint(sprint);

            assertEquals(SprintStatus.ACTIVE, result.getStatus());
            verify(sprintRepository).save(sprint);
        }
    }

    @Nested
    class CompleteSprint {

        @Test
        void withTickets_relocatesThemAndCompletes() {
            Board alpha = board("Alpha");
            Sprint sprint = sprint(SprintStatus.ACTIVE, utc("2026-01-01"), utc("2026-01-08"));
            Sprint target = sprint(SprintStatus.PLANNED, utc("2026-01-08"), utc("2026-01-15"));
            List<Ticket> tickets = List.of(ticket(alpha, sprint, TicketStatus.IN_PROGRESS));

            Sprint result = helper.completeSprint(sprint, tickets, TicketRelocationAction.MOVE_TO_SPRINT,
                    null, target, null);

            verify(ticketHelper).relocateTickets(tickets, TicketRelocationAction.MOVE_TO_SPRINT,
                    null, target, null, sprint.getId());
            assertEquals(SprintStatus.COMPLETED, result.getStatus());
        }

        @Test
        void withoutTickets_completesWithoutRelocation() {
            Sprint sprint = sprint(SprintStatus.ACTIVE, utc("2026-01-01"), utc("2026-01-08"));

            Sprint result = helper.completeSprint(sprint, List.of(), TicketRelocationAction.MOVE_TO_BACKLOG,
                    null, null, Backlog.builder().build());

            verify(ticketHelper, never()).relocateTickets(any(), any(), any(), any(), any(), any());
            assertSame(sprint, result);
            assertEquals(SprintStatus.COMPLETED, result.getStatus());
        }
    }

    @Nested
    class SlugForBoard {

        @Test
        void freeBase_returnsBaseSlug() {
            UUID boardId = UUID.randomUUID();

            String slug = helper.slugForBoard("Release Train", boardId, null);

            assertEquals("release-train", slug);
        }

        @Test
        void takenOnTheBoard_returnsFirstFreeSuffix() {
            UUID boardId = UUID.randomUUID();
            when(boardSprintRepository.existsByBoard_IdAndSlug(boardId, "release")).thenReturn(true);
            when(boardSprintRepository.existsByBoard_IdAndSlug(boardId, "release-1")).thenReturn(true);

            String slug = helper.slugForBoard("Release", boardId, null);

            assertEquals("release-2", slug);
        }

        @Test
        void existingLink_ignoresItsOwnSlug() {
            UUID boardId = UUID.randomUUID();
            UUID linkId = UUID.randomUUID();
            when(boardSprintRepository.existsByBoard_IdAndSlugAndIdNot(boardId, "release", linkId)).thenReturn(false);

            String slug = helper.slugForBoard("Release", boardId, linkId);

            assertEquals("release", slug);
            verify(boardSprintRepository, never()).existsByBoard_IdAndSlug(any(), any());
        }

        @Test
        void everyReservedBoardSegment_getsASuffix() {
            UUID boardId = UUID.randomUUID();

            for (String reserved : SprintConstant.RESERVED_SLUGS) {
                String slug = helper.slugForBoard(reserved, boardId, null);

                assertEquals(reserved + "-1", slug, "A sprint named " + reserved + " must not take the board segment");
            }
        }

        @Test
        void reservedWordWithTakenSuffix_skipsToNextSuffix() {
            UUID boardId = UUID.randomUUID();
            when(boardSprintRepository.existsByBoard_IdAndSlug(boardId, "backlog-1")).thenReturn(true);

            String slug = helper.slugForBoard("Backlog", boardId, null);

            assertEquals("backlog-2", slug);
        }

        @Test
        void reservedWordInDifferentCase_getsASuffix() {
            String slug = helper.slugForBoard("ROADMAP", UUID.randomUUID(), null);

            assertEquals("roadmap-1", slug);
        }

        @Test
        void nameWithoutLettersOrDigits_usesTheReservedFallbackWithSuffix() {
            String slug = helper.slugForBoard("🚀", UUID.randomUUID(), null);

            assertEquals("sprint-1", slug);
        }

        @Test
        void sameNameOnTwoBoards_getsSlugPerBoard() {
            UUID firstBoard = UUID.randomUUID();
            UUID secondBoard = UUID.randomUUID();
            when(boardSprintRepository.existsByBoard_IdAndSlug(firstBoard, "alpha")).thenReturn(true);

            assertEquals("alpha-1", helper.slugForBoard("Alpha", firstBoard, null));
            assertEquals("alpha", helper.slugForBoard("Alpha", secondBoard, null));
        }
    }

    @Nested
    class AddBoardLink {

        @Test
        void newLink_carriesTheSlugFreeOnItsBoard() {
            Board board = board("Alpha");
            Sprint sprint = Sprint.builder().name("Sprint Two").build();
            when(boardSprintRepository.existsByBoard_IdAndSlug(board.getId(), "sprint-two")).thenReturn(true);

            helper.addBoardLink(sprint, board);

            assertEquals(1, sprint.getBoardSprints().size());
            BoardSprint link = sprint.getBoardSprints().iterator().next();
            assertEquals("sprint-two-1", link.getSlug());
            assertSame(board, link.getBoard());
            assertSame(sprint, link.getSprint());
        }
    }

    @Nested
    class RestoreBoardLink {

        @Test
        void restoresWithSlugFromTheCurrentName() {
            Board board = board("Alpha");
            Sprint sprint = Sprint.builder().name("Renamed Sprint").build();
            BoardSprint deleted = BoardSprint.builder().board(board).sprint(sprint).slug("old-name").build();
            deleted.setId(UUID.randomUUID());

            helper.restoreBoardLink(sprint, deleted);

            verify(boardSprintRepository).restoreByIdWithSlug(deleted.getId(), "renamed-sprint");
        }

        @Test
        void slugIndexViolation_throwsSlugTakenConflict() {
            Board board = board("Alpha");
            Sprint sprint = Sprint.builder().name("Planning").build();
            BoardSprint deleted = BoardSprint.builder().board(board).sprint(sprint).build();
            deleted.setId(UUID.randomUUID());
            when(boardSprintRepository.restoreByIdWithSlug(deleted.getId(), "planning"))
                    .thenThrow(violation(SprintConstant.SLUG_UNIQUE_INDEX));

            ConflictException exception = assertThrows(ConflictException.class,
                    () -> helper.restoreBoardLink(sprint, deleted));

            assertEquals(ExceptionDomainKazanError.STA_SLUG_TAKEN.getCode(), exception.getErrorCode());
            assertEquals("Planning", exception.getErrorValues().get(SprintConstant.FIELD_NAME));
        }

        @Test
        void otherIntegrityViolation_isRethrownUnchanged() {
            Board board = board("Alpha");
            Sprint sprint = Sprint.builder().name("Planning").build();
            BoardSprint deleted = BoardSprint.builder().board(board).sprint(sprint).build();
            deleted.setId(UUID.randomUUID());
            DataIntegrityViolationException original = violation("board_sprints_pkey");
            when(boardSprintRepository.restoreByIdWithSlug(deleted.getId(), "planning")).thenThrow(original);

            DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                    () -> helper.restoreBoardLink(sprint, deleted));

            assertSame(original, thrown);
        }
    }

    @Nested
    class RestoreLinksOfBoard {

        @Test
        void slugFree_restoresLinkKeepingItsSlug() {
            UUID boardId = UUID.randomUUID();
            UUID linkId = UUID.randomUUID();
            when(boardSprintRepository.findDeletedIdsByBoardId(boardId)).thenReturn(List.of(linkId));
            when(boardSprintRepository.restoreByIdIfSlugFree(linkId, SprintConstant.RESERVED_SLUGS)).thenReturn(1);

            helper.restoreLinksOfBoard(boardId);

            verify(boardSprintRepository, never()).restoreByIdWithSlug(any(), any());
        }

        @Test
        void slugTakenOrReserved_restoresWithNextFreeSlugForTheCurrentName() {
            UUID boardId = UUID.randomUUID();
            UUID linkId = UUID.randomUUID();
            when(boardSprintRepository.findDeletedIdsByBoardId(boardId)).thenReturn(List.of(linkId));
            when(boardSprintRepository.restoreByIdIfSlugFree(linkId, SprintConstant.RESERVED_SLUGS)).thenReturn(0);
            when(boardSprintRepository.findSprintNameByBoardSprintId(linkId)).thenReturn("Sprint Alpha");
            when(boardSprintRepository.existsByBoard_IdAndSlug(boardId, "sprint-alpha")).thenReturn(true);

            helper.restoreLinksOfBoard(boardId);

            verify(boardSprintRepository).restoreByIdWithSlug(linkId, "sprint-alpha-1");
        }

        @Test
        void linksAreRestoredInTheOrderTheRepositoryReturnsThem() {
            UUID boardId = UUID.randomUUID();
            UUID newest = UUID.randomUUID();
            UUID oldest = UUID.randomUUID();
            when(boardSprintRepository.findDeletedIdsByBoardId(boardId)).thenReturn(List.of(newest, oldest));
            when(boardSprintRepository.restoreByIdIfSlugFree(any(), any())).thenReturn(1);

            helper.restoreLinksOfBoard(boardId);

            InOrder order = inOrder(boardSprintRepository);
            order.verify(boardSprintRepository).restoreByIdIfSlugFree(newest, SprintConstant.RESERVED_SLUGS);
            order.verify(boardSprintRepository).restoreByIdIfSlugFree(oldest, SprintConstant.RESERVED_SLUGS);
        }

        @Test
        void noDeletedLinks_restoresNothing() {
            UUID boardId = UUID.randomUUID();
            when(boardSprintRepository.findDeletedIdsByBoardId(boardId)).thenReturn(List.of());

            helper.restoreLinksOfBoard(boardId);

            verify(boardSprintRepository, never()).restoreByIdIfSlugFree(any(), any());
        }

        @Test
        void slugIndexViolation_throwsSlugTakenConflictNamingTheSprint() {
            UUID boardId = UUID.randomUUID();
            UUID linkId = UUID.randomUUID();
            when(boardSprintRepository.findDeletedIdsByBoardId(boardId)).thenReturn(List.of(linkId));
            when(boardSprintRepository.findSprintNameByBoardSprintId(linkId)).thenReturn("Planning");
            when(boardSprintRepository.restoreByIdIfSlugFree(linkId, SprintConstant.RESERVED_SLUGS))
                    .thenThrow(violation(SprintConstant.SLUG_UNIQUE_INDEX));

            ConflictException exception = assertThrows(ConflictException.class,
                    () -> helper.restoreLinksOfBoard(boardId));

            assertEquals(ExceptionDomainKazanError.STA_SLUG_TAKEN.getCode(), exception.getErrorCode());
            assertEquals("Planning", exception.getErrorValues().get(SprintConstant.FIELD_NAME));
        }

        @Test
        void otherIntegrityViolation_isRethrownUnchanged() {
            UUID boardId = UUID.randomUUID();
            UUID linkId = UUID.randomUUID();
            DataIntegrityViolationException original = violation("board_sprints_pkey");
            when(boardSprintRepository.findDeletedIdsByBoardId(boardId)).thenReturn(List.of(linkId));
            when(boardSprintRepository.findSprintNameByBoardSprintId(linkId)).thenReturn("Planning");
            when(boardSprintRepository.restoreByIdIfSlugFree(linkId, SprintConstant.RESERVED_SLUGS))
                    .thenThrow(original);

            DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                    () -> helper.restoreLinksOfBoard(boardId));

            assertSame(original, thrown);
        }
    }

    @Nested
    class SyncSlugsWithName {

        @Test
        void renameKeepingTheBase_keepsEveryLinkSlug() {
            Board board = board("Alpha");
            Sprint sprint = Sprint.builder().name("PLANNING!").build();
            sprint.getBoardSprints().add(BoardSprint.builder().board(board).sprint(sprint).slug("planning-2").build());

            helper.syncSlugsWithName(sprint);

            assertEquals("planning-2", sprint.getBoardSprints().iterator().next().getSlug());
            verifyNoInteractions(boardSprintRepository);
        }

        @Test
        void renameToNewBase_givesEachBoardItsOwnFreeSlug() {
            Board first = board("Alpha");
            Board second = board("Beta");
            Sprint sprint = Sprint.builder().name("Hardening").build();
            BoardSprint firstLink = BoardSprint.builder().board(first).sprint(sprint).slug("planning").build();
            firstLink.setId(UUID.randomUUID());
            BoardSprint secondLink = BoardSprint.builder().board(second).sprint(sprint).slug("planning").build();
            secondLink.setId(UUID.randomUUID());
            sprint.getBoardSprints().add(firstLink);
            sprint.getBoardSprints().add(secondLink);
            when(boardSprintRepository.existsByBoard_IdAndSlugAndIdNot(first.getId(), "hardening", firstLink.getId()))
                    .thenReturn(true);

            helper.syncSlugsWithName(sprint);

            assertEquals("hardening-1", firstLink.getSlug());
            assertEquals("hardening", secondLink.getSlug());
        }

        @Test
        void renameToReservedWord_getsASuffix() {
            Board board = board("Alpha");
            Sprint sprint = Sprint.builder().name("Backlog").build();
            BoardSprint link = BoardSprint.builder().board(board).sprint(sprint).slug("planning").build();
            link.setId(UUID.randomUUID());
            sprint.getBoardSprints().add(link);

            helper.syncSlugsWithName(sprint);

            assertEquals("backlog-1", link.getSlug());
        }
    }

    @Nested
    class SaveWithSlugs {

        @Test
        void success_returnsTheFlushedSprint() {
            Sprint sprint = Sprint.builder().name("Planning").build();

            Sprint saved = helper.saveWithSlugs(sprint);

            assertSame(sprint, saved);
        }

        @Test
        void slugIndexViolation_throwsSlugTakenConflictNamingTheSprint() {
            Sprint sprint = Sprint.builder().name("Planning").build();
            when(sprintRepository.saveAndFlush(sprint)).thenThrow(violation(SprintConstant.SLUG_UNIQUE_INDEX));

            ConflictException exception = assertThrows(ConflictException.class,
                    () -> helper.saveWithSlugs(sprint));

            assertEquals(ExceptionDomainKazanError.STA_SLUG_TAKEN.getCode(), exception.getErrorCode());
            assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
            assertEquals("Planning", exception.getErrorValues().get(SprintConstant.FIELD_NAME));
        }

        @Test
        void otherIntegrityViolation_isRethrownUnchanged() {
            Sprint sprint = Sprint.builder().name("Planning").build();
            DataIntegrityViolationException original = violation("sprints_number_uk");
            when(sprintRepository.saveAndFlush(sprint)).thenThrow(original);

            DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                    () -> helper.saveWithSlugs(sprint));

            assertSame(original, thrown);
        }
    }

    private static DataIntegrityViolationException violation(String constraintName) {
        return new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("duplicate key", new SQLException("duplicate key"),
                        constraintName));
    }
}
