package com.korur.oba.apps.kazan.ticket.helper;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.backlog.entity.Backlog;
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.repository.BoardRepository;
import com.korur.oba.apps.kazan.comment.repository.CommentRepository;
import com.korur.oba.apps.kazan.milestone.entity.Milestone;
import com.korur.oba.apps.kazan.milestone.repository.MilestoneDependencyRepository;
import com.korur.oba.apps.kazan.milestone.repository.MilestoneRepository;
import com.korur.oba.apps.kazan.milestone.validator.MilestoneValidator;
import com.korur.oba.apps.kazan.sprint.entity.Sprint;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.enums.TicketRelocationAction;
import com.korur.oba.apps.kazan.ticket.enums.TicketStatus;
import com.korur.oba.apps.kazan.ticket.enums.TicketType;
import com.korur.oba.apps.kazan.ticket.repository.TicketAssigneeRepository;
import com.korur.oba.apps.kazan.ticket.repository.TicketLabelRepository;
import com.korur.oba.apps.kazan.ticket.repository.TicketRepository;
import com.korur.oba.apps.kazan.ticket.repository.TicketReviewerRepository;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class TicketHelperUnitTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private TicketAssigneeRepository ticketAssigneeRepository;

    @Mock
    private TicketReviewerRepository ticketReviewerRepository;

    @Mock
    private TicketLabelRepository ticketLabelRepository;

    @Mock
    private LoggingService loggingService;

    // Real instance: the backlog status rule is behaviour under test, not a collaborator to stub.
    @Spy
    private TicketStatusNormalizer ticketStatusNormalizer = new TicketStatusNormalizer(new LoggingService());

    // Real instance: whether a move keeps the milestone is behaviour under test.
    @Spy
    private MilestoneValidator milestoneValidator =
            new MilestoneValidator(mock(MilestoneRepository.class), mock(MilestoneDependencyRepository.class),
                    new LoggingService());

    @InjectMocks
    private TicketHelper ticketHelper;

    @Nested
    class RelocateTickets {

        @Test
        void emptyList_returnsEarlyWithNoAction() {
            // Act
            ticketHelper.relocateTickets(
                    Collections.emptyList(), TicketRelocationAction.DELETE,
                    null, null, null, UUID.randomUUID());

            // Assert
            verify(ticketRepository, never()).saveAll(anyList());
            verify(ticketRepository, never()).deleteAll(anyList());
        }

        @Test
        void MOVE_TO_BACKLOG_sameBoard_movesTicketsToBacklog() {
            // Arrange
            UUID sprintId = UUID.randomUUID();
            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(UUID.randomUUID());

            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Backlog backlog = Backlog.builder().board(board).build();
            backlog.setId(UUID.randomUUID());

            Ticket ticket = Ticket.builder().title("T").key("B-1").type(TicketType.FEATURE).board(board).createdBy(user).build();
            ticket.setId(UUID.randomUUID());
            List<Ticket> tickets = List.of(ticket);

            when(ticketRepository.saveAll(anyList())).thenReturn(tickets);

            // Act
            ticketHelper.relocateTickets(
                    tickets, TicketRelocationAction.MOVE_TO_BACKLOG,
                    null, null, backlog, sprintId);

            // Assert
            verify(ticketRepository).saveAll(tickets);
            assertThat(ticket.getBacklog()).isEqualTo(backlog);
            assertThat(ticket.getSprint()).isNull();
        }

        @Test
        void MOVE_TO_BACKLOG_nonTodoTicket_resetsStatusToTodo() {
            // Arrange
            UUID sprintId = UUID.randomUUID();
            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(UUID.randomUUID());

            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Backlog backlog = Backlog.builder().board(board).build();
            backlog.setId(UUID.randomUUID());

            Sprint sprint = Sprint.builder().name("Sprint").build();
            sprint.setId(sprintId);

            Ticket ticket = Ticket.builder().title("T").key("B-1").type(TicketType.FEATURE)
                    .status(TicketStatus.IN_PROGRESS).sprint(sprint).board(board).createdBy(user).build();
            ticket.setId(UUID.randomUUID());
            List<Ticket> tickets = List.of(ticket);

            when(ticketRepository.saveAll(anyList())).thenReturn(tickets);

            // Act
            ticketHelper.relocateTickets(
                    tickets, TicketRelocationAction.MOVE_TO_BACKLOG,
                    null, null, backlog, sprintId);

            // Assert
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.TODO);
        }

        @Test
        void MOVE_TO_SPRINT_nonTodoTicket_keepsStatus() {
            // Arrange
            UUID sourceId = UUID.randomUUID();
            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(UUID.randomUUID());

            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Sprint targetSprint = Sprint.builder().name("Target Sprint").build();
            targetSprint.setId(UUID.randomUUID());

            Ticket ticket = Ticket.builder().title("T").key("B-1").type(TicketType.FEATURE)
                    .status(TicketStatus.DONE).board(board).createdBy(user).build();
            ticket.setId(UUID.randomUUID());
            List<Ticket> tickets = List.of(ticket);

            when(ticketRepository.saveAll(anyList())).thenReturn(tickets);

            // Act
            ticketHelper.relocateTickets(
                    tickets, TicketRelocationAction.MOVE_TO_SPRINT,
                    null, targetSprint, null, sourceId);

            // Assert
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.DONE);
        }

        @Test
        void MOVE_TO_SPRINT_sameBoard_movesTicketsToSprint() {
            // Arrange
            UUID sourceId = UUID.randomUUID();
            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(UUID.randomUUID());

            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Sprint targetSprint = Sprint.builder().name("Target Sprint").build();
            targetSprint.setId(UUID.randomUUID());

            Ticket ticket = Ticket.builder().title("T").key("B-1").type(TicketType.FEATURE).board(board).createdBy(user).build();
            ticket.setId(UUID.randomUUID());
            List<Ticket> tickets = List.of(ticket);

            when(ticketRepository.saveAll(anyList())).thenReturn(tickets);

            // Act
            ticketHelper.relocateTickets(
                    tickets, TicketRelocationAction.MOVE_TO_SPRINT,
                    null, targetSprint, null, sourceId);

            // Assert
            verify(ticketRepository).saveAll(tickets);
            assertThat(ticket.getSprint()).isEqualTo(targetSprint);
            assertThat(ticket.getBacklog()).isNull();
        }

        @Test
        void DELETE_deletesAllTickets() {
            // Arrange
            UUID sourceId = UUID.randomUUID();
            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(UUID.randomUUID());

            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Ticket ticket = Ticket.builder().title("T").key("B-1").type(TicketType.FEATURE).board(board).createdBy(user).build();
            ticket.setId(UUID.randomUUID());
            List<Ticket> tickets = List.of(ticket);

            // Act
            ticketHelper.relocateTickets(
                    tickets, TicketRelocationAction.DELETE,
                    null, null, null, sourceId);

            // Assert
            verify(ticketRepository).deleteAll(tickets);
            verify(ticketRepository, never()).saveAll(anyList());
        }

        @Test
        void MOVE_TO_BACKLOG_crossBoard_appliesCrossBoardFields() {
            // Arrange
            UUID sourceId = UUID.randomUUID();
            UUID sourceBoardId = UUID.randomUUID();
            UUID targetBoardId = UUID.randomUUID();

            Board sourceBoard = Board.builder().name("Source").prefix("S").build();
            sourceBoard.setId(sourceBoardId);

            Board targetBoard = Board.builder().name("Target").prefix("T").build();
            targetBoard.setId(targetBoardId);

            Backlog targetBacklog = Backlog.builder().board(targetBoard).build();
            targetBacklog.setId(UUID.randomUUID());

            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Ticket ticket = Ticket.builder().title("T").key("S-1").type(TicketType.FEATURE).board(sourceBoard).createdBy(user).build();
            ticket.setId(UUID.randomUUID());
            List<Ticket> tickets = List.of(ticket);

            when(boardRepository.incrementAndGetTicketCounter(targetBoardId)).thenReturn(1L);
            when(ticketRepository.maxDisplayOrderByBoard_Id(targetBoardId)).thenReturn(0);
            when(ticketRepository.saveAll(anyList())).thenReturn(tickets);

            // Act
            ticketHelper.relocateTickets(
                    tickets, TicketRelocationAction.MOVE_TO_BACKLOG,
                    targetBoard, null, targetBacklog, sourceId);

            // Assert
            verify(ticketRepository).saveAll(tickets);
            assertThat(ticket.getKey()).isEqualTo("T-1");
            assertThat(ticket.getBoard()).isEqualTo(targetBoard);
            assertThat(ticket.getProject()).isNull();
            assertThat(ticket.getDisplayOrder()).isEqualTo(1);
        }

        @Test
        void crossBoard_toUnrelatedBoard_clearsMilestoneAndReportsNewKey() {
            // Arrange
            Board sourceBoard = Board.builder().name("Source").prefix("S").build();
            sourceBoard.setId(UUID.randomUUID());
            Board targetBoard = Board.builder().name("Target").prefix("T").build();
            targetBoard.setId(UUID.randomUUID());
            Backlog targetBacklog = Backlog.builder().board(targetBoard).build();

            Milestone milestone = Milestone.builder().name("M1").board(sourceBoard)
                    .targetDate(LocalDate.of(2026, 12, 1)).build();
            milestone.setId(UUID.randomUUID());

            Ticket ticket = Ticket.builder().title("T").key("S-1").type(TicketType.FEATURE)
                    .board(sourceBoard).milestone(milestone).build();
            ticket.setId(UUID.randomUUID());
            List<Ticket> tickets = List.of(ticket);

            when(boardRepository.incrementAndGetTicketCounter(targetBoard.getId())).thenReturn(7L);
            when(ticketRepository.maxDisplayOrderByBoard_Id(targetBoard.getId())).thenReturn(0);

            // Act
            List<String> cleared = ticketHelper.relocateTickets(
                    tickets, TicketRelocationAction.MOVE_TO_BACKLOG,
                    targetBoard, null, targetBacklog, sourceBoard.getId());

            // Assert
            assertThat(ticket.getMilestone()).isNull();
            assertThat(cleared).containsExactly("T-7");
        }

        @Test
        void crossBoard_toChildOfMilestoneBoard_keepsMilestone() {
            // Arrange
            Board parentBoard = Board.builder().name("Parent").prefix("P").build();
            parentBoard.setId(UUID.randomUUID());
            Board childBoard = Board.builder().name("Child").prefix("C").parentBoard(parentBoard).build();
            childBoard.setId(UUID.randomUUID());
            Backlog targetBacklog = Backlog.builder().board(childBoard).build();

            Milestone milestone = Milestone.builder().name("M1").board(parentBoard)
                    .targetDate(LocalDate.of(2026, 12, 1)).build();
            milestone.setId(UUID.randomUUID());

            Ticket ticket = Ticket.builder().title("T").key("P-1").type(TicketType.FEATURE)
                    .board(parentBoard).milestone(milestone).build();
            ticket.setId(UUID.randomUUID());
            List<Ticket> tickets = List.of(ticket);

            when(boardRepository.incrementAndGetTicketCounter(childBoard.getId())).thenReturn(1L);
            when(ticketRepository.maxDisplayOrderByBoard_Id(childBoard.getId())).thenReturn(0);

            // Act
            List<String> cleared = ticketHelper.relocateTickets(
                    tickets, TicketRelocationAction.MOVE_TO_BACKLOG,
                    childBoard, null, targetBacklog, parentBoard.getId());

            // Assert
            assertThat(ticket.getMilestone()).isEqualTo(milestone);
            assertThat(cleared).isEmpty();
        }

        @Test
        void MOVE_TO_SPRINT_crossBoard_appliesCrossBoardFields() {
            // Arrange
            UUID sourceId = UUID.randomUUID();
            UUID sourceBoardId = UUID.randomUUID();
            UUID targetBoardId = UUID.randomUUID();

            Board sourceBoard = Board.builder().name("Source").prefix("S").build();
            sourceBoard.setId(sourceBoardId);

            Board targetBoard = Board.builder().name("Target").prefix("T").build();
            targetBoard.setId(targetBoardId);

            Sprint targetSprint = Sprint.builder().name("Target Sprint").build();
            targetSprint.setId(UUID.randomUUID());

            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Ticket ticket = Ticket.builder().title("T").key("S-1").type(TicketType.FEATURE).board(sourceBoard).createdBy(user).build();
            ticket.setId(UUID.randomUUID());
            List<Ticket> tickets = List.of(ticket);

            when(boardRepository.incrementAndGetTicketCounter(targetBoardId)).thenReturn(3L);
            when(ticketRepository.maxDisplayOrderByBoard_Id(targetBoardId)).thenReturn(5);
            when(ticketRepository.saveAll(anyList())).thenReturn(tickets);

            // Act
            ticketHelper.relocateTickets(
                    tickets, TicketRelocationAction.MOVE_TO_SPRINT,
                    targetBoard, targetSprint, null, sourceId);

            // Assert
            verify(ticketRepository).saveAll(tickets);
            assertThat(ticket.getKey()).isEqualTo("T-3");
            assertThat(ticket.getBoard()).isEqualTo(targetBoard);
            assertThat(ticket.getProject()).isNull();
            assertThat(ticket.getDisplayOrder()).isEqualTo(6);
            assertThat(ticket.getSprint()).isEqualTo(targetSprint);
            assertThat(ticket.getBacklog()).isNull();
        }

        @Test
        void MOVE_TO_BACKLOG_sameBoard_movesOnlyTheGivenTickets() {
            // Arrange
            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(UUID.randomUUID());
            User user = User.builder().build();
            user.setId(UUID.randomUUID());
            Backlog backlog = Backlog.builder().board(board).build();
            backlog.setId(UUID.randomUUID());
            Sprint sprint = Sprint.builder().name("Sprint").build();
            sprint.setId(UUID.randomUUID());

            Ticket parent = Ticket.builder().title("Parent").key("B-1").type(TicketType.FEATURE).board(board).createdBy(user).build();
            parent.setId(UUID.randomUUID());
            parent.setSprint(sprint);
            Ticket child = Ticket.builder().title("Child").key("B-2").type(TicketType.FEATURE).board(board).createdBy(user)
                    .parentTicket(parent).build();
            child.setId(UUID.randomUUID());
            child.setSprint(sprint);

            // Act
            ticketHelper.relocateTickets(
                    List.of(parent), TicketRelocationAction.MOVE_TO_BACKLOG,
                    null, null, backlog, UUID.randomUUID());

            // Assert
            assertThat(parent.getBacklog()).isEqualTo(backlog);
            assertThat(parent.getSprint()).isNull();
            assertThat(child.getSprint()).isEqualTo(sprint);
            assertThat(child.getBacklog()).isNull();
            verify(ticketRepository, never()).findByParentTicket_IdIn(anyList());
        }

        @Test
        void MOVE_TO_BACKLOG_crossBoardMultiLevelNesting_carriesAllDescendants() {
            // Arrange
            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(UUID.randomUUID());
            Board targetBoard = Board.builder().name("Target").prefix("T").build();
            targetBoard.setId(UUID.randomUUID());
            User user = User.builder().build();
            user.setId(UUID.randomUUID());
            Backlog targetBacklog = Backlog.builder().board(targetBoard).build();
            targetBacklog.setId(UUID.randomUUID());

            Ticket parent = Ticket.builder().title("Parent").key("B-1").type(TicketType.FEATURE).board(board).createdBy(user).build();
            parent.setId(UUID.randomUUID());
            Ticket child = Ticket.builder().title("Child").key("B-2").type(TicketType.FEATURE).board(board).createdBy(user)
                    .parentTicket(parent).build();
            child.setId(UUID.randomUUID());
            Ticket grandchild = Ticket.builder().title("Grandchild").key("B-3").type(TicketType.FEATURE).board(board)
                    .createdBy(user).parentTicket(child).build();
            grandchild.setId(UUID.randomUUID());

            when(ticketRepository.findByParentTicket_IdIn(List.of(parent.getId()))).thenReturn(List.of(child));
            when(ticketRepository.findByParentTicket_IdIn(List.of(child.getId()))).thenReturn(List.of(grandchild));
            when(ticketRepository.findByParentTicket_IdIn(List.of(grandchild.getId()))).thenReturn(Collections.emptyList());

            // Act
            ticketHelper.relocateTickets(
                    List.of(parent), TicketRelocationAction.MOVE_TO_BACKLOG,
                    targetBoard, null, targetBacklog, UUID.randomUUID());

            // Assert
            assertThat(parent.getBoard()).isEqualTo(targetBoard);
            assertThat(child.getBoard()).isEqualTo(targetBoard);
            assertThat(grandchild.getBoard()).isEqualTo(targetBoard);
            assertThat(grandchild.getBacklog()).isEqualTo(targetBacklog);
            assertThat(grandchild.getParentTicket()).isEqualTo(child);
        }

        @Test
        void DELETE_doesNotExpandSubTickets() {
            // Arrange
            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(UUID.randomUUID());
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Ticket ticket = Ticket.builder().title("T").key("B-1").type(TicketType.FEATURE).board(board).createdBy(user).build();
            ticket.setId(UUID.randomUUID());

            // Act
            ticketHelper.relocateTickets(
                    List.of(ticket), TicketRelocationAction.DELETE,
                    null, null, null, UUID.randomUUID());

            // Assert
            verify(ticketRepository).deleteAll(List.of(ticket));
            verify(ticketRepository, never()).findByParentTicket_IdIn(anyList());
        }
    }

    /**
     * Shared fixture: a board with a backlog and two sprints, and a top-level parent in
     * sprintA with one sub-ticket, both IN_PROGRESS.
     */
    abstract class HierarchyFixture {

        protected Board board;
        protected User user;
        protected Backlog backlog;
        protected Sprint sprintA;
        protected Sprint sprintB;
        protected Ticket parent;
        protected Ticket subTicket;

        @BeforeEach
        void setUpHierarchy() {
            board = Board.builder().name("Board").prefix("B").build();
            board.setId(UUID.randomUUID());
            user = User.builder().build();
            user.setId(UUID.randomUUID());
            backlog = Backlog.builder().board(board).build();
            backlog.setId(UUID.randomUUID());
            sprintA = Sprint.builder().name("Sprint A").build();
            sprintA.setId(UUID.randomUUID());
            sprintB = Sprint.builder().name("Sprint B").build();
            sprintB.setId(UUID.randomUUID());
            parent = ticket("B-1", null);
            parent.setSprint(sprintA);
            subTicket = ticket("B-2", parent);
            subTicket.setSprint(sprintA);
        }

        protected Ticket ticket(String key, Ticket parentTicket) {
            Ticket ticket = Ticket.builder().title(key).key(key).type(TicketType.FEATURE)
                    .status(TicketStatus.IN_PROGRESS).board(board).parentTicket(parentTicket)
                    .createdBy(user).build();
            ticket.setId(UUID.randomUUID());
            return ticket;
        }
    }

    @Nested
    class SubTicketPlacement extends HierarchyFixture {

        @Test
        void relocateTickets_subTicketAloneToBacklog_keepsParentAndStatus() {
            // The completeSprint case: the parent is DONE and stays in the sprint, while its
            // unfinished sub-ticket is moved to the backlog on its own.

            // Arrange
            parent.setStatus(TicketStatus.DONE);

            // Act
            ticketHelper.relocateTickets(
                    List.of(subTicket), TicketRelocationAction.MOVE_TO_BACKLOG,
                    null, null, backlog, sprintA.getId());

            // Assert
            assertThat(subTicket.getParentTicket()).isEqualTo(parent);
            assertThat(subTicket.getBacklog()).isEqualTo(backlog);
            assertThat(subTicket.getSprint()).isNull();
            assertThat(subTicket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
            assertThat(parent.getSprint()).isEqualTo(sprintA);
            assertThat(parent.getStatus()).isEqualTo(TicketStatus.DONE);
        }

        @Test
        void relocateTickets_subTicketAloneToOtherSprint_keepsParent() {
            // Act
            ticketHelper.relocateTickets(
                    List.of(subTicket), TicketRelocationAction.MOVE_TO_SPRINT,
                    null, sprintB, null, board.getId());

            // Assert
            assertThat(subTicket.getParentTicket()).isEqualTo(parent);
            assertThat(subTicket.getSprint()).isEqualTo(sprintB);
            assertThat(subTicket.getBacklog()).isNull();
            assertThat(subTicket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
            assertThat(parent.getSprint()).isEqualTo(sprintA);
        }

        @Test
        void relocateTickets_subTicketMovedWithParent_keepsParent() {
            // The sprint delete, unlink and board delete case: every ticket of the sprint moves.

            // Act
            ticketHelper.relocateTickets(
                    List.of(parent, subTicket), TicketRelocationAction.MOVE_TO_BACKLOG,
                    null, null, backlog, sprintA.getId());

            // Assert
            assertThat(subTicket.getParentTicket()).isEqualTo(parent);
            assertThat(subTicket.getBacklog()).isEqualTo(backlog);
            assertThat(subTicket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
            assertThat(parent.getBacklog()).isEqualTo(backlog);
            assertThat(parent.getStatus()).isEqualTo(TicketStatus.TODO);
        }

        @Test
        void moveTickets_parentWithDefaultMode_carriesUnfinishedSubTicketPlacedWithIt() {
            // The single moveTicket case of a parent: a client that sends no mode gets UNFINISHED.

            // Arrange
            when(ticketRepository.findByParentTicket_IdIn(List.of(parent.getId()))).thenReturn(List.of(subTicket));

            // Act
            ticketHelper.moveTickets(
                    List.of(parent), null, null, TicketRelocationAction.MOVE_TO_SPRINT,
                    null, sprintB, null, board.getId());

            // Assert
            assertThat(subTicket.getParentTicket()).isEqualTo(parent);
            assertThat(subTicket.getSprint()).isEqualTo(sprintB);
            assertThat(parent.getSprint()).isEqualTo(sprintB);
        }

        @Test
        void relocateTickets_subTicketAloneToOtherBoard_goesToOwnBoardBacklog() {
            // Arrange: a cross-board system relocation that selected the sub-ticket only.
            board.setBacklog(backlog);
            Board targetBoard = Board.builder().name("Target").prefix("T").build();
            targetBoard.setId(UUID.randomUUID());
            Backlog targetBacklog = Backlog.builder().board(targetBoard).build();
            targetBacklog.setId(UUID.randomUUID());

            // Act
            ticketHelper.relocateTickets(
                    List.of(subTicket), TicketRelocationAction.MOVE_TO_BACKLOG,
                    targetBoard, null, targetBacklog, board.getId());

            // Assert
            assertThat(subTicket.getParentTicket()).isEqualTo(parent);
            assertThat(subTicket.getBoard()).isEqualTo(board);
            assertThat(subTicket.getKey()).isEqualTo("B-2");
            assertThat(subTicket.getBacklog()).isEqualTo(backlog);
            assertThat(subTicket.getSprint()).isNull();
            assertThat(subTicket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        }
    }

    @Nested
    class Unparent extends HierarchyFixture {

        @Test
        void unparent_subTicket_keepsOwnPlacement() {
            // Arrange
            subTicket.setSprint(null);
            subTicket.setBacklog(backlog);

            // Act
            ticketHelper.unparent(subTicket);

            // Assert
            assertThat(subTicket.getParentTicket()).isNull();
            assertThat(subTicket.getBacklog()).isEqualTo(backlog);
            assertThat(subTicket.getSprint()).isNull();
            verify(ticketRepository, never()).saveAll(anyList());
        }

        @Test
        void unparent_topLevelTicket_leavesItUnchanged() {
            // Act
            ticketHelper.unparent(parent);

            // Assert
            assertThat(parent.getSprint()).isEqualTo(sprintA);
            verify(ticketRepository, never()).findByParentTicket_IdIn(anyList());
        }
    }

    @Nested
    class AssignParent extends HierarchyFixture {

        @Test
        void assignParent_parentInSprint_keepsOwnPlacement() {
            // Arrange
            Ticket ticket = ticket("B-3", null);
            ticket.setBacklog(backlog);

            // Act
            ticketHelper.assignParent(ticket, parent);

            // Assert
            assertThat(ticket.getParentTicket()).isEqualTo(parent);
            assertThat(ticket.getBacklog()).isEqualTo(backlog);
            assertThat(ticket.getSprint()).isNull();
            verify(ticketRepository, never()).saveAll(anyList());
        }
    }

    @Nested
    class RestoreTicketChildren {

        @Test
        void noSubTickets_restoresDirectChildrenOnly() {
            // Arrange
            UUID ticketId = UUID.randomUUID();

            when(commentRepository.restoreAllByTicketId(ticketId)).thenReturn(2);
            when(ticketAssigneeRepository.restoreAllByTicketId(ticketId)).thenReturn(1);
            when(ticketReviewerRepository.restoreAllByTicketId(ticketId)).thenReturn(1);
            when(ticketLabelRepository.restoreAllByTicketId(ticketId)).thenReturn(0);
            when(ticketRepository.findDeletedByParentTicketId(ticketId)).thenReturn(Collections.emptyList());

            // Act
            ticketHelper.restoreTicketChildren(ticketId);

            // Assert
            verify(commentRepository).restoreAllByTicketId(ticketId);
            verify(ticketAssigneeRepository).restoreAllByTicketId(ticketId);
            verify(ticketReviewerRepository).restoreAllByTicketId(ticketId);
            verify(ticketLabelRepository).restoreAllByTicketId(ticketId);
            verify(ticketRepository).findDeletedByParentTicketId(ticketId);
        }

        @Test
        void withSubTickets_restoresRecursively() {
            // Arrange
            UUID parentTicketId = UUID.randomUUID();
            UUID subTicketId = UUID.randomUUID();

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(UUID.randomUUID());

            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Ticket subTicket = Ticket.builder().title("Sub").key("B-2").type(TicketType.FEATURE).board(board).createdBy(user).build();
            subTicket.setId(subTicketId);

            // Parent ticket restore
            when(commentRepository.restoreAllByTicketId(parentTicketId)).thenReturn(0);
            when(ticketAssigneeRepository.restoreAllByTicketId(parentTicketId)).thenReturn(0);
            when(ticketReviewerRepository.restoreAllByTicketId(parentTicketId)).thenReturn(0);
            when(ticketLabelRepository.restoreAllByTicketId(parentTicketId)).thenReturn(0);
            when(ticketRepository.findDeletedByParentTicketId(parentTicketId)).thenReturn(List.of(subTicket));

            // Sub-ticket restore (recursive call)
            when(commentRepository.restoreAllByTicketId(subTicketId)).thenReturn(0);
            when(ticketAssigneeRepository.restoreAllByTicketId(subTicketId)).thenReturn(0);
            when(ticketReviewerRepository.restoreAllByTicketId(subTicketId)).thenReturn(0);
            when(ticketLabelRepository.restoreAllByTicketId(subTicketId)).thenReturn(0);
            when(ticketRepository.findDeletedByParentTicketId(subTicketId)).thenReturn(Collections.emptyList());

            // Act
            ticketHelper.restoreTicketChildren(parentTicketId);

            // Assert
            verify(ticketRepository).restoreById(subTicketId);
            verify(commentRepository).restoreAllByTicketId(subTicketId);
            verify(ticketAssigneeRepository).restoreAllByTicketId(subTicketId);
            verify(ticketReviewerRepository).restoreAllByTicketId(subTicketId);
            verify(ticketLabelRepository).restoreAllByTicketId(subTicketId);
        }
    }
}
