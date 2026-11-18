package com.korur.oba.apps.kazan.ticket.helper;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 2. Third-party
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.backlog.entity.Backlog;
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.repository.BoardRepository;
import com.korur.oba.apps.kazan.comment.repository.CommentRepository;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.milestone.repository.MilestoneDependencyRepository;
import com.korur.oba.apps.kazan.milestone.repository.MilestoneRepository;
import com.korur.oba.apps.kazan.milestone.validator.MilestoneValidator;
import com.korur.oba.apps.kazan.sprint.entity.Sprint;
import com.korur.oba.apps.kazan.ticket.constant.TicketConstant;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.enums.SubTicketMoveMode;
import com.korur.oba.apps.kazan.ticket.enums.TicketRelocationAction;
import com.korur.oba.apps.kazan.ticket.enums.TicketStatus;
import com.korur.oba.apps.kazan.ticket.enums.TicketType;
import com.korur.oba.apps.kazan.ticket.repository.TicketAssigneeRepository;
import com.korur.oba.apps.kazan.ticket.repository.TicketLabelRepository;
import com.korur.oba.apps.kazan.ticket.repository.TicketRepository;
import com.korur.oba.apps.kazan.ticket.repository.TicketReviewerRepository;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Covers the placement rules of the ticket hierarchy on a real ticket graph: the user move
 * (moveTickets, rules P2, P3 and P6) and the system relocations of sprint complete, delete,
 * unlink, the recurring rollover and board delete (relocateTickets, rules P2, P4 and P7).
 *
 * The repository answers the sub-ticket lookup from the graph the test builds, so the helper
 * walks the same hierarchy it would load from the database.
 */
@ExtendWith(MockitoExtension.class)
class TicketHelperUnitMoveTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private LoggingService loggingService;

    private TicketHelper ticketHelper;

    private final List<Ticket> graph = new ArrayList<>();

    private User user;
    private Board board;
    private Backlog backlog;
    private Board targetBoard;
    private Backlog targetBacklog;
    private Sprint sprintA;
    private Sprint sprintB;
    private Sprint sprintC;

    @BeforeEach
    void setUp() {
        ticketHelper = new TicketHelper(
                ticketRepository,
                boardRepository,
                mock(CommentRepository.class),
                mock(TicketAssigneeRepository.class),
                mock(TicketReviewerRepository.class),
                mock(TicketLabelRepository.class),
                new TicketStatusNormalizer(new LoggingService()),
                new MilestoneValidator(mock(MilestoneRepository.class), mock(MilestoneDependencyRepository.class),
                        new LoggingService()),
                loggingService);

        user = User.builder().build();
        user.setId(UUID.randomUUID());
        board = Board.builder().name("Board").prefix("B").build();
        board.setId(UUID.randomUUID());
        backlog = Backlog.builder().board(board).build();
        backlog.setId(UUID.randomUUID());
        board.setBacklog(backlog);
        targetBoard = Board.builder().name("Target").prefix("T").build();
        targetBoard.setId(UUID.randomUUID());
        targetBacklog = Backlog.builder().board(targetBoard).build();
        targetBacklog.setId(UUID.randomUUID());
        targetBoard.setBacklog(targetBacklog);
        sprintA = sprint("Sprint A");
        sprintB = sprint("Sprint B");
        sprintC = sprint("Sprint C");

        // The sub-ticket lookup returns the direct children of the given parents in the graph.
        lenient().when(ticketRepository.findByParentTicket_IdIn(anyList())).thenAnswer(invocation -> {
            List<UUID> parentIds = invocation.getArgument(0);
            return graph.stream()
                    .filter((Ticket ticket) -> ticket.getParentTicket() != null
                            && parentIds.contains(ticket.getParentTicket().getId()))
                    .toList();
        });
    }

    private Sprint sprint(String name) {
        Sprint sprint = Sprint.builder().name(name).build();
        sprint.setId(UUID.randomUUID());
        return sprint;
    }

    private Ticket inSprint(String key, Ticket parentTicket, TicketStatus status, Sprint sprint) {
        Ticket ticket = ticket(key, parentTicket, status);
        ticket.setSprint(sprint);
        return ticket;
    }

    private Ticket inBacklog(String key, Ticket parentTicket, TicketStatus status) {
        Ticket ticket = ticket(key, parentTicket, status);
        ticket.setBacklog(backlog);
        return ticket;
    }

    private Ticket ticket(String key, Ticket parentTicket, TicketStatus status) {
        Ticket ticket = Ticket.builder().title(key).key(key).type(TicketType.FEATURE)
                .status(status).board(board).parentTicket(parentTicket).createdBy(user).build();
        ticket.setId(UUID.randomUUID());
        graph.add(ticket);
        return ticket;
    }

    private List<String> moveToSprintB(List<Ticket> tickets, SubTicketMoveMode mode, List<UUID> subTicketIds) {
        return ticketHelper.moveTickets(tickets, mode, subTicketIds, TicketRelocationAction.MOVE_TO_SPRINT,
                null, sprintB, null, board.getId());
    }

    private List<Ticket> savedTickets() {
        ArgumentCaptor<List<Ticket>> captor = ArgumentCaptor.captor();
        verify(ticketRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private void assertNothingWritten() {
        verify(ticketRepository, never()).saveAll(anyList());
        verify(ticketRepository, never()).deleteAll(anyList());
        verify(boardRepository, never()).incrementAndGetTicketCounter(any());
    }

    @Nested
    class MoveTicketsModes {

        private Ticket parent;
        private Ticket unfinishedWithParent;
        private Ticket doneWithParent;
        private Ticket unfinishedInLaterSprint;
        private Ticket unfinishedInBacklog;

        @BeforeEach
        void setUpParent() {
            // A parent in sprint A with four sub-tickets placed in different ways.
            parent = inSprint("B-1", null, TicketStatus.IN_PROGRESS, sprintA);
            unfinishedWithParent = inSprint("B-2", parent, TicketStatus.IN_PROGRESS, sprintA);
            doneWithParent = inSprint("B-3", parent, TicketStatus.DONE, sprintA);
            unfinishedInLaterSprint = inSprint("B-4", parent, TicketStatus.TODO, sprintC);
            unfinishedInBacklog = inBacklog("B-5", parent, TicketStatus.TODO);
        }

        @Test
        void none_movesOnlyTheParent() {
            // Act
            moveToSprintB(List.of(parent), SubTicketMoveMode.NONE, null);

            // Assert
            assertThat(parent.getSprint()).isEqualTo(sprintB);
            assertThat(unfinishedWithParent.getSprint()).isEqualTo(sprintA);
            assertThat(doneWithParent.getSprint()).isEqualTo(sprintA);
            assertThat(unfinishedInLaterSprint.getSprint()).isEqualTo(sprintC);
            assertThat(unfinishedInBacklog.getBacklog()).isEqualTo(backlog);
            assertThat(savedTickets()).containsExactly(parent);
        }

        @ParameterizedTest
        @NullSource
        @EnumSource(value = SubTicketMoveMode.class, names = "UNFINISHED")
        void unfinishedOrNullMode_movesUnfinishedSubTicketsPlacedWithTheParentOnly(SubTicketMoveMode mode) {
            // Act
            moveToSprintB(List.of(parent), mode, null);

            // Assert
            assertThat(parent.getSprint()).isEqualTo(sprintB);
            assertThat(unfinishedWithParent.getSprint()).isEqualTo(sprintB);
            assertThat(unfinishedWithParent.getParentTicket()).isEqualTo(parent);
            assertThat(doneWithParent.getSprint()).isEqualTo(sprintA);
            assertThat(unfinishedInLaterSprint.getSprint()).isEqualTo(sprintC);
            assertThat(unfinishedInBacklog.getBacklog()).isEqualTo(backlog);
            assertThat(unfinishedInBacklog.getSprint()).isNull();
            assertThat(savedTickets()).containsExactly(parent, unfinishedWithParent);
        }

        @Test
        void unfinished_parentInBacklog_comparesBacklogPlacement() {
            // Arrange: the parent sits in the backlog, so only sub-tickets in that backlog are
            // placed with it; the one in sprint A is not.
            parent.setSprint(null);
            parent.setBacklog(backlog);

            // Act
            moveToSprintB(List.of(parent), SubTicketMoveMode.UNFINISHED, null);

            // Assert
            assertThat(parent.getSprint()).isEqualTo(sprintB);
            assertThat(unfinishedInBacklog.getSprint()).isEqualTo(sprintB);
            assertThat(unfinishedInBacklog.getBacklog()).isNull();
            assertThat(unfinishedWithParent.getSprint()).isEqualTo(sprintA);
            assertThat(savedTickets()).containsExactly(parent, unfinishedInBacklog);
        }

        @Test
        void selected_movesExactlyTheListedSubTicketsWherePlaced() {
            // Act: a DONE sub-ticket and one from a later sprint are listed; the unfinished one
            // placed with the parent is not.
            moveToSprintB(List.of(parent), SubTicketMoveMode.SELECTED,
                    List.of(doneWithParent.getId(), unfinishedInLaterSprint.getId()));

            // Assert
            assertThat(parent.getSprint()).isEqualTo(sprintB);
            assertThat(doneWithParent.getSprint()).isEqualTo(sprintB);
            assertThat(doneWithParent.getStatus()).isEqualTo(TicketStatus.DONE);
            assertThat(unfinishedInLaterSprint.getSprint()).isEqualTo(sprintB);
            assertThat(unfinishedWithParent.getSprint()).isEqualTo(sprintA);
            assertThat(unfinishedInBacklog.getBacklog()).isEqualTo(backlog);
            assertThat(savedTickets()).containsExactlyInAnyOrder(parent, doneWithParent, unfinishedInLaterSprint);
            assertThat(List.of(doneWithParent, unfinishedInLaterSprint))
                    .allMatch((Ticket ticket) -> ticket.getParentTicket() == parent);
        }

        @ParameterizedTest
        @NullAndEmptySource
        void selectedWithoutIds_movesOnlyTheParent(List<UUID> subTicketIds) {
            // Act
            moveToSprintB(List.of(parent), SubTicketMoveMode.SELECTED, subTicketIds);

            // Assert
            assertThat(parent.getSprint()).isEqualTo(sprintB);
            assertThat(unfinishedWithParent.getSprint()).isEqualTo(sprintA);
            assertThat(savedTickets()).containsExactly(parent);
        }

        @Test
        void selected_listsATicketThatIsAlsoRequested_movesBothWithoutError() {
            // A bulk move that requests a parent and its sub-ticket and also ticks the sub-ticket.

            // Act
            moveToSprintB(List.of(parent, unfinishedInLaterSprint), SubTicketMoveMode.SELECTED,
                    List.of(unfinishedInLaterSprint.getId()));

            // Assert
            assertThat(parent.getSprint()).isEqualTo(sprintB);
            assertThat(unfinishedInLaterSprint.getSprint()).isEqualTo(sprintB);
            assertThat(unfinishedInLaterSprint.getParentTicket()).isEqualTo(parent);
            assertThat(unfinishedWithParent.getSprint()).isEqualTo(sprintA);
            assertThat(savedTickets()).containsExactly(parent, unfinishedInLaterSprint);
        }

        @Test
        void loneSubTicket_keepsItsParentAndSiblingsStay() {
            // Act
            ticketHelper.moveTickets(List.of(unfinishedWithParent), null, null,
                    TicketRelocationAction.MOVE_TO_BACKLOG, null, null, backlog, board.getId());

            // Assert
            assertThat(unfinishedWithParent.getParentTicket()).isEqualTo(parent);
            assertThat(unfinishedWithParent.getBacklog()).isEqualTo(backlog);
            assertThat(unfinishedWithParent.getSprint()).isNull();
            // A sub-ticket keeps its status in the backlog.
            assertThat(unfinishedWithParent.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
            assertThat(parent.getSprint()).isEqualTo(sprintA);
            assertThat(doneWithParent.getSprint()).isEqualTo(sprintA);
            assertThat(savedTickets()).containsExactly(unfinishedWithParent);
        }

        @Test
        void topLevelTicketToBacklog_resetsStatusToTodo() {
            // Act
            ticketHelper.moveTickets(List.of(parent), SubTicketMoveMode.NONE, null,
                    TicketRelocationAction.MOVE_TO_BACKLOG, null, null, backlog, board.getId());

            // Assert
            assertThat(parent.getStatus()).isEqualTo(TicketStatus.TODO);
            assertThat(parent.getBacklog()).isEqualTo(backlog);
        }
    }

    @Nested
    class MoveTicketsValidation {

        private Ticket parent;
        private Ticket subTicket;

        @BeforeEach
        void setUpParent() {
            parent = inSprint("B-1", null, TicketStatus.IN_PROGRESS, sprintA);
            subTicket = inSprint("B-2", parent, TicketStatus.IN_PROGRESS, sprintA);
        }

        @ParameterizedTest
        @NullSource
        @EnumSource(value = SubTicketMoveMode.class, names = {"NONE", "UNFINISHED"})
        void subTicketIdsWithoutSelected_throwsKaiVal057BeforeAnyReadOrWrite(SubTicketMoveMode mode) {
            // Act
            ValidationException exception = assertThrows(ValidationException.class,
                    () -> moveToSprintB(List.of(parent), mode, List.of(subTicket.getId())));

            // Assert
            assertThat(exception.getErrorCode())
                    .isEqualTo(ExceptionDomainKazanError.VAL_SUB_TICKET_IDS_WITHOUT_SELECTED.getCode());
            assertThat(exception.getErrorValues())
                    .containsEntry(TicketConstant.FIELD_SUB_TICKET_MODE,
                            mode != null ? mode : SubTicketMoveMode.UNFINISHED);
            verify(ticketRepository, never()).findByParentTicket_IdIn(anyList());
            assertNothingWritten();
            assertThat(parent.getSprint()).isEqualTo(sprintA);
        }

        @Test
        void emptySubTicketIdsWithUnfinished_isAccepted() {
            // A client that always sends an empty list keeps working.

            // Act
            moveToSprintB(List.of(parent), SubTicketMoveMode.UNFINISHED, List.of());

            // Assert
            assertThat(parent.getSprint()).isEqualTo(sprintB);
            assertThat(subTicket.getSprint()).isEqualTo(sprintB);
        }

        @Test
        void selectedIdNotADescendant_throwsKaiVal058WithTheOffendingIdsBeforeAnyWrite() {
            // Arrange
            Ticket unrelated = inSprint("B-9", null, TicketStatus.TODO, sprintA);

            // Act
            ValidationException exception = assertThrows(ValidationException.class,
                    () -> moveToSprintB(List.of(parent), SubTicketMoveMode.SELECTED,
                            List.of(subTicket.getId(), unrelated.getId(), unrelated.getId())));

            // Assert
            assertThat(exception.getErrorCode())
                    .isEqualTo(ExceptionDomainKazanError.VAL_SUB_TICKET_NOT_DESCENDANT.getCode());
            assertThat(exception.getErrorValues())
                    .containsEntry(TicketConstant.FIELD_SUB_TICKET_IDS, List.of(unrelated.getId()));
            assertNothingWritten();
            assertThat(parent.getSprint()).isEqualTo(sprintA);
            assertThat(subTicket.getSprint()).isEqualTo(sprintA);
        }

        @Test
        void selectedIdIsTheParentOfTheMovedSubTicket_throwsKaiVal058() {
            // Act
            ValidationException exception = assertThrows(ValidationException.class,
                    () -> moveToSprintB(List.of(subTicket), SubTicketMoveMode.SELECTED, List.of(parent.getId())));

            // Assert
            assertThat(exception.getErrorCode())
                    .isEqualTo(ExceptionDomainKazanError.VAL_SUB_TICKET_NOT_DESCENDANT.getCode());
            assertNothingWritten();
        }

        @Test
        void subTicketToOtherBoardWithoutParent_throwsKaiVal059WithKeysBeforeAnyWrite() {
            // Act
            ValidationException exception = assertThrows(ValidationException.class,
                    () -> ticketHelper.moveTickets(List.of(subTicket), null, null,
                            TicketRelocationAction.MOVE_TO_BACKLOG, targetBoard, null, targetBacklog, board.getId()));

            // Assert
            assertThat(exception.getErrorCode())
                    .isEqualTo(ExceptionDomainKazanError.VAL_SUB_TICKET_BOARD_CHANGE.getCode());
            assertThat(exception.getErrorValues()).containsEntry(TicketConstant.FIELD_TICKET_KEYS, List.of("B-2"));
            assertNothingWritten();
            assertThat(subTicket.getBoard()).isEqualTo(board);
            assertThat(subTicket.getKey()).isEqualTo("B-2");
            assertThat(subTicket.getSprint()).isEqualTo(sprintA);
        }

        @Test
        void validSelectionWithOneStrandedSubTicket_throwsKaiVal059AndMovesNothing() {
            // Arrange: the top-level ticket alone could move; the sub-ticket in the same request
            // cannot, so neither moves.
            Ticket topLevel = inSprint("B-3", null, TicketStatus.TODO, sprintA);

            // Act
            assertThrows(ValidationException.class,
                    () -> ticketHelper.moveTickets(List.of(topLevel, subTicket), SubTicketMoveMode.NONE, null,
                            TicketRelocationAction.MOVE_TO_BACKLOG, targetBoard, null, targetBacklog, board.getId()));

            // Assert
            assertNothingWritten();
            assertThat(topLevel.getBoard()).isEqualTo(board);
            assertThat(topLevel.getSprint()).isEqualTo(sprintA);
        }
    }

    @Nested
    class MoveTicketsAcrossBoards {

        @Test
        void parentToOtherBoard_carriesWholeSubtreeWhateverTheMode() {
            // Arrange
            Ticket parent = inSprint("B-1", null, TicketStatus.IN_PROGRESS, sprintA);
            Ticket doneSub = inSprint("B-2", parent, TicketStatus.DONE, sprintA);
            Ticket subElsewhere = inSprint("B-3", parent, TicketStatus.TODO, sprintC);
            Ticket grandchild = inBacklog("B-4", subElsewhere, TicketStatus.IN_PROGRESS);
            when(boardRepository.incrementAndGetTicketCounter(targetBoard.getId())).thenReturn(1L, 2L, 3L, 4L);
            when(ticketRepository.maxDisplayOrderByBoard_Id(targetBoard.getId())).thenReturn(0);

            // Act
            ticketHelper.moveTickets(List.of(parent), SubTicketMoveMode.NONE, null,
                    TicketRelocationAction.MOVE_TO_BACKLOG, targetBoard, null, targetBacklog, board.getId());

            // Assert
            for (Ticket ticket : List.of(parent, doneSub, subElsewhere, grandchild)) {
                assertThat(ticket.getBoard()).isEqualTo(targetBoard);
                assertThat(ticket.getBacklog()).isEqualTo(targetBacklog);
                assertThat(ticket.getSprint()).isNull();
                assertThat(ticket.getKey()).startsWith("T-");
            }
            assertThat(doneSub.getParentTicket()).isEqualTo(parent);
            assertThat(subElsewhere.getParentTicket()).isEqualTo(parent);
            assertThat(grandchild.getParentTicket()).isEqualTo(subElsewhere);
            // Sub-tickets keep their status in the backlog; the top-level ticket becomes TODO.
            assertThat(parent.getStatus()).isEqualTo(TicketStatus.TODO);
            assertThat(doneSub.getStatus()).isEqualTo(TicketStatus.DONE);
            assertThat(grandchild.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        }

        @Test
        void parentAndItsSubTicketRequestedToOtherBoard_bothMoveAndKeepTheLink() {
            // Arrange
            Ticket parent = inSprint("B-1", null, TicketStatus.IN_PROGRESS, sprintA);
            Ticket subTicket = inSprint("B-2", parent, TicketStatus.IN_PROGRESS, sprintC);
            when(boardRepository.incrementAndGetTicketCounter(targetBoard.getId())).thenReturn(1L, 2L);
            when(ticketRepository.maxDisplayOrderByBoard_Id(targetBoard.getId())).thenReturn(0);

            // Act
            ticketHelper.moveTickets(List.of(parent, subTicket), null, null,
                    TicketRelocationAction.MOVE_TO_BACKLOG, targetBoard, null, targetBacklog, board.getId());

            // Assert
            assertThat(parent.getBoard()).isEqualTo(targetBoard);
            assertThat(subTicket.getBoard()).isEqualTo(targetBoard);
            assertThat(subTicket.getParentTicket()).isEqualTo(parent);
            assertThat(subTicket.getBacklog()).isEqualTo(targetBacklog);
        }

        @Test
        void ticketAlreadyOnTargetBoard_appliesTheModeAndKeepsItsKey() {
            // A bulk move from several boards targets the URL board; a ticket already on it is a
            // same-board move for which the mode decides.

            // Arrange
            Ticket parent = inSprint("B-1", null, TicketStatus.IN_PROGRESS, sprintA);
            Ticket subTicket = inSprint("B-2", parent, TicketStatus.IN_PROGRESS, sprintA);

            // Act
            ticketHelper.moveTickets(List.of(parent), SubTicketMoveMode.NONE, null,
                    TicketRelocationAction.MOVE_TO_SPRINT, board, sprintB, null, board.getId());

            // Assert
            assertThat(parent.getSprint()).isEqualTo(sprintB);
            assertThat(parent.getKey()).isEqualTo("B-1");
            assertThat(subTicket.getSprint()).isEqualTo(sprintA);
            verify(boardRepository, never()).incrementAndGetTicketCounter(any());
        }
    }

    @Nested
    class MoveTicketsNesting {

        @Test
        void unfinished_depthTwo_movesUnfinishedGrandchildPlacedWithTheParent() {
            // Arrange: the DONE child stays, its unfinished grandchild in the parent's sprint goes.
            Ticket parent = inSprint("B-1", null, TicketStatus.IN_PROGRESS, sprintA);
            Ticket doneChild = inSprint("B-2", parent, TicketStatus.DONE, sprintA);
            Ticket grandchild = inSprint("B-3", doneChild, TicketStatus.IN_PROGRESS, sprintA);
            Ticket grandchildElsewhere = inSprint("B-4", doneChild, TicketStatus.IN_PROGRESS, sprintC);

            // Act
            moveToSprintB(List.of(parent), SubTicketMoveMode.UNFINISHED, null);

            // Assert
            assertThat(parent.getSprint()).isEqualTo(sprintB);
            assertThat(doneChild.getSprint()).isEqualTo(sprintA);
            assertThat(grandchild.getSprint()).isEqualTo(sprintB);
            assertThat(grandchild.getParentTicket()).isEqualTo(doneChild);
            assertThat(grandchildElsewhere.getSprint()).isEqualTo(sprintC);
            assertThat(savedTickets()).containsExactly(parent, grandchild);
        }

        @Test
        void unfinished_nestedRequest_comparesWithTheNearestRequestedAncestor() {
            // Arrange: the child is requested too and sits in sprint C, so its grandchild in
            // sprint C is placed with it, although not with the top parent.
            Ticket parent = inSprint("B-1", null, TicketStatus.IN_PROGRESS, sprintA);
            Ticket child = inSprint("B-2", parent, TicketStatus.IN_PROGRESS, sprintC);
            Ticket grandchild = inSprint("B-3", child, TicketStatus.TODO, sprintC);
            Ticket grandchildWithTopParent = inSprint("B-4", child, TicketStatus.TODO, sprintA);

            // Act
            moveToSprintB(List.of(parent, child), SubTicketMoveMode.UNFINISHED, null);

            // Assert
            assertThat(child.getSprint()).isEqualTo(sprintB);
            assertThat(grandchild.getSprint()).isEqualTo(sprintB);
            assertThat(grandchildWithTopParent.getSprint()).isEqualTo(sprintA);
            assertThat(grandchild.getParentTicket()).isEqualTo(child);
        }

        @Test
        void selected_depthTwo_movesTheListedGrandchildOnly() {
            // Arrange
            Ticket parent = inSprint("B-1", null, TicketStatus.IN_PROGRESS, sprintA);
            Ticket child = inSprint("B-2", parent, TicketStatus.IN_PROGRESS, sprintA);
            Ticket grandchild = inSprint("B-3", child, TicketStatus.TODO, sprintC);

            // Act
            moveToSprintB(List.of(parent), SubTicketMoveMode.SELECTED, List.of(grandchild.getId()));

            // Assert
            assertThat(grandchild.getSprint()).isEqualTo(sprintB);
            assertThat(grandchild.getParentTicket()).isEqualTo(child);
            assertThat(child.getSprint()).isEqualTo(sprintA);
            assertThat(savedTickets()).containsExactly(parent, grandchild);
        }

        @Test
        void parentCycle_endsTheWalkAndMovesTheRequestedTicket() {
            // Arrange: bad data with a parent cycle must not loop forever.
            Ticket first = inSprint("B-1", null, TicketStatus.IN_PROGRESS, sprintA);
            Ticket second = inSprint("B-2", first, TicketStatus.IN_PROGRESS, sprintA);
            first.setParentTicket(second);

            // Act
            moveToSprintB(List.of(first), SubTicketMoveMode.UNFINISHED, null);

            // Assert
            assertThat(first.getSprint()).isEqualTo(sprintB);
            assertThat(second.getSprint()).isEqualTo(sprintB);
            assertThat(first.getParentTicket()).isEqualTo(second);
        }
    }

    @Nested
    class SystemRelocations {

        @Test
        void completeToBacklog_doneParentStaysAndItsUnfinishedSubTicketMoves() {
            // Sprint complete passes only the unfinished tickets of the sprint.

            // Arrange
            Ticket doneParent = inSprint("B-1", null, TicketStatus.DONE, sprintA);
            Ticket unfinishedSub = inSprint("B-2", doneParent, TicketStatus.IN_PROGRESS, sprintA);

            // Act
            ticketHelper.relocateTickets(List.of(unfinishedSub), TicketRelocationAction.MOVE_TO_BACKLOG,
                    null, null, backlog, sprintA.getId());

            // Assert
            assertThat(doneParent.getSprint()).isEqualTo(sprintA);
            assertThat(doneParent.getStatus()).isEqualTo(TicketStatus.DONE);
            assertThat(unfinishedSub.getBacklog()).isEqualTo(backlog);
            assertThat(unfinishedSub.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
            assertThat(unfinishedSub.getParentTicket()).isEqualTo(doneParent);
        }

        @Test
        void completeToSprint_unfinishedParentMovesAndItsDoneSubTicketsStay() {
            // Arrange
            Ticket parent = inSprint("B-1", null, TicketStatus.IN_PROGRESS, sprintA);
            Ticket doneSub = inSprint("B-2", parent, TicketStatus.DONE, sprintA);
            Ticket unfinishedSub = inSprint("B-3", parent, TicketStatus.TODO, sprintA);

            // Act
            ticketHelper.relocateTickets(List.of(parent, unfinishedSub), TicketRelocationAction.MOVE_TO_SPRINT,
                    null, sprintB, null, sprintA.getId());

            // Assert
            assertThat(parent.getSprint()).isEqualTo(sprintB);
            assertThat(unfinishedSub.getSprint()).isEqualTo(sprintB);
            assertThat(doneSub.getSprint()).isEqualTo(sprintA);
            assertThat(doneSub.getParentTicket()).isEqualTo(parent);
            assertThat(unfinishedSub.getParentTicket()).isEqualTo(parent);
            verify(ticketRepository, never()).findByParentTicket_IdIn(anyList());
            assertThat(savedTickets()).containsExactly(parent, unfinishedSub);
        }

        @Test
        void sprintDeleteToSprint_movesEveryGivenTicketAndNoSubTicketPlacedElsewhere() {
            // Sprint delete passes every ticket of the sprint, DONE ones included.

            // Arrange
            Ticket parent = inSprint("B-1", null, TicketStatus.DONE, sprintA);
            Ticket subInSprint = inSprint("B-2", parent, TicketStatus.DONE, sprintA);
            Ticket subElsewhere = inSprint("B-3", parent, TicketStatus.TODO, sprintC);

            // Act
            ticketHelper.relocateTickets(List.of(parent, subInSprint), TicketRelocationAction.MOVE_TO_SPRINT,
                    null, sprintB, null, sprintA.getId());

            // Assert
            assertThat(parent.getSprint()).isEqualTo(sprintB);
            assertThat(subInSprint.getSprint()).isEqualTo(sprintB);
            assertThat(subElsewhere.getSprint()).isEqualTo(sprintC);
            assertThat(parent.getStatus()).isEqualTo(TicketStatus.DONE);
        }

        @Test
        void recurringRolloverToBacklog_resetsTopLevelOnlyAndKeepsParents() {
            // The rollover moves each board's unfinished tickets to that board's backlog.

            // Arrange
            Ticket parent = inSprint("B-1", null, TicketStatus.IN_PROGRESS, sprintA);
            Ticket unfinishedSub = inSprint("B-2", parent, TicketStatus.IN_REVIEW, sprintA);
            Ticket doneSub = inSprint("B-3", parent, TicketStatus.DONE, sprintA);

            // Act
            ticketHelper.relocateTickets(List.of(parent, unfinishedSub), TicketRelocationAction.MOVE_TO_BACKLOG,
                    null, null, backlog, sprintA.getId());

            // Assert
            assertThat(parent.getStatus()).isEqualTo(TicketStatus.TODO);
            assertThat(unfinishedSub.getStatus()).isEqualTo(TicketStatus.IN_REVIEW);
            assertThat(unfinishedSub.getBacklog()).isEqualTo(backlog);
            assertThat(unfinishedSub.getParentTicket()).isEqualTo(parent);
            assertThat(doneSub.getSprint()).isEqualTo(sprintA);
        }

        @Test
        void crossBoardComplete_movedParentCarriesSubtreeAndLoneSubTicketGoesToOwnBacklog() {
            // Rule P7 in one call: P1 changes board with its whole subtree; S2, whose DONE parent
            // stays, goes to its own board's backlog with its given child.

            // Arrange
            Ticket movedParent = inSprint("B-1", null, TicketStatus.IN_PROGRESS, sprintA);
            Ticket doneSubOfMovedParent = inSprint("B-2", movedParent, TicketStatus.DONE, sprintA);
            Ticket doneParent = inSprint("B-3", null, TicketStatus.DONE, sprintA);
            Ticket loneSub = inSprint("B-4", doneParent, TicketStatus.IN_PROGRESS, sprintA);
            Ticket loneSubChild = inSprint("B-5", loneSub, TicketStatus.IN_PROGRESS, sprintA);
            when(boardRepository.incrementAndGetTicketCounter(targetBoard.getId())).thenReturn(1L, 2L);
            when(ticketRepository.maxDisplayOrderByBoard_Id(targetBoard.getId())).thenReturn(0);

            // Act
            ticketHelper.relocateTickets(List.of(movedParent, loneSub, loneSubChild),
                    TicketRelocationAction.MOVE_TO_BACKLOG, targetBoard, null, targetBacklog, sprintA.getId());

            // Assert
            assertThat(movedParent.getBoard()).isEqualTo(targetBoard);
            assertThat(movedParent.getBacklog()).isEqualTo(targetBacklog);
            assertThat(doneSubOfMovedParent.getBoard()).isEqualTo(targetBoard);
            assertThat(doneSubOfMovedParent.getBacklog()).isEqualTo(targetBacklog);
            assertThat(doneSubOfMovedParent.getParentTicket()).isEqualTo(movedParent);
            for (Ticket ticket : List.of(loneSub, loneSubChild)) {
                assertThat(ticket.getBoard()).isEqualTo(board);
                assertThat(ticket.getBacklog()).isEqualTo(backlog);
                assertThat(ticket.getSprint()).isNull();
                assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
            }
            assertThat(loneSub.getKey()).isEqualTo("B-4");
            assertThat(loneSub.getParentTicket()).isEqualTo(doneParent);
            assertThat(loneSubChild.getParentTicket()).isEqualTo(loneSub);
            assertThat(doneParent.getSprint()).isEqualTo(sprintA);
        }

        @Test
        void crossBoardUnlinkToSprint_loneSubTicketGoesToOwnBacklogNotTheTargetSprint() {
            // Arrange
            Ticket parent = inSprint("B-1", null, TicketStatus.IN_PROGRESS, sprintC);
            Ticket loneSub = inSprint("B-2", parent, TicketStatus.IN_PROGRESS, sprintA);

            // Act
            ticketHelper.relocateTickets(List.of(loneSub), TicketRelocationAction.MOVE_TO_SPRINT,
                    targetBoard, sprintB, null, sprintA.getId());

            // Assert
            assertThat(loneSub.getBoard()).isEqualTo(board);
            assertThat(loneSub.getSprint()).isNull();
            assertThat(loneSub.getBacklog()).isEqualTo(backlog);
            assertThat(loneSub.getParentTicket()).isEqualTo(parent);
            assertThat(parent.getSprint()).isEqualTo(sprintC);
            verify(boardRepository, never()).incrementAndGetTicketCounter(any());
        }

        @Test
        void boardDeleteToOtherBoard_movesTheWholeHierarchyAndKeepsEveryParent() {
            // Board delete passes every ticket of the board, so no sub-ticket is left behind.

            // Arrange
            Ticket parent = inSprint("B-1", null, TicketStatus.IN_PROGRESS, sprintA);
            Ticket subInBacklog = inBacklog("B-2", parent, TicketStatus.IN_PROGRESS);
            Ticket grandchild = inSprint("B-3", subInBacklog, TicketStatus.DONE, sprintC);
            when(boardRepository.incrementAndGetTicketCounter(targetBoard.getId())).thenReturn(1L, 2L, 3L);
            when(ticketRepository.maxDisplayOrderByBoard_Id(targetBoard.getId())).thenReturn(0);

            // Act
            ticketHelper.relocateTickets(List.of(parent, subInBacklog, grandchild),
                    TicketRelocationAction.MOVE_TO_BACKLOG, targetBoard, null, targetBacklog, board.getId());

            // Assert
            for (Ticket ticket : List.of(parent, subInBacklog, grandchild)) {
                assertThat(ticket.getBoard()).isEqualTo(targetBoard);
                assertThat(ticket.getBacklog()).isEqualTo(targetBacklog);
            }
            assertThat(subInBacklog.getParentTicket()).isEqualTo(parent);
            assertThat(grandchild.getParentTicket()).isEqualTo(subInBacklog);
            assertThat(savedTickets()).hasSize(3);
        }

        @Test
        void sprintUnlinkWithDelete_deletesOnlyTheGivenTickets() {
            // Arrange
            Ticket parent = inSprint("B-1", null, TicketStatus.IN_PROGRESS, sprintA);
            inSprint("B-2", parent, TicketStatus.TODO, sprintC);

            // Act
            ticketHelper.relocateTickets(List.of(parent), TicketRelocationAction.DELETE,
                    null, null, null, sprintA.getId());

            // Assert: the sub-ticket elsewhere goes through the JPA cascade, not the helper.
            verify(ticketRepository).deleteAll(List.of(parent));
            verify(ticketRepository, never()).findByParentTicket_IdIn(anyList());
        }
    }
}
