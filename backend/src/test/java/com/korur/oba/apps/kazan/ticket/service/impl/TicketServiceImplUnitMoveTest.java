package com.korur.oba.apps.kazan.ticket.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

// 2. Third-party
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import jakarta.persistence.EntityManager;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.backlog.entity.Backlog;
import com.korur.oba.apps.kazan.backlog.validator.BacklogValidator;
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.enums.BoardMemberRole;
import com.korur.oba.apps.kazan.board.repository.BoardRepository;
import com.korur.oba.apps.kazan.board.service.BoardAccessService;
import com.korur.oba.apps.kazan.board.service.BoardMemberQueryService;
import com.korur.oba.apps.kazan.board.service.BoardQueryService;
import com.korur.oba.apps.kazan.board.validator.BoardValidator;
import com.korur.oba.apps.kazan.comment.repository.CommentRepository;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.label.validator.LabelValidator;
import com.korur.oba.apps.kazan.milestone.repository.MilestoneDependencyRepository;
import com.korur.oba.apps.kazan.milestone.repository.MilestoneRepository;
import com.korur.oba.apps.kazan.milestone.validator.MilestoneValidator;
import com.korur.oba.apps.kazan.project.validator.ProjectValidator;
import com.korur.oba.apps.kazan.sprint.entity.Sprint;
import com.korur.oba.apps.kazan.sprint.service.SprintService;
import com.korur.oba.apps.kazan.sprint.validator.SprintValidator;
import com.korur.oba.apps.kazan.ticket.dto.request.TicketBulkMoveRequest;
import com.korur.oba.apps.kazan.ticket.dto.request.TicketMoveRequest;
import com.korur.oba.apps.kazan.ticket.dto.response.TicketBulkMoveResponse;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.enums.SubTicketMoveMode;
import com.korur.oba.apps.kazan.ticket.enums.TicketRelocationAction;
import com.korur.oba.apps.kazan.ticket.enums.TicketStatus;
import com.korur.oba.apps.kazan.ticket.enums.TicketType;
import com.korur.oba.apps.kazan.ticket.helper.TicketHelper;
import com.korur.oba.apps.kazan.ticket.helper.TicketStatusNormalizer;
import com.korur.oba.apps.kazan.ticket.helper.TicketTypeNormalizer;
import com.korur.oba.apps.kazan.ticket.mapper.TicketMapper;
import com.korur.oba.apps.kazan.ticket.repository.TicketAssigneeRepository;
import com.korur.oba.apps.kazan.ticket.repository.TicketLabelRepository;
import com.korur.oba.apps.kazan.ticket.repository.TicketRepository;
import com.korur.oba.apps.kazan.ticket.repository.TicketReviewerRepository;
import com.korur.oba.apps.kazan.ticket.validator.TicketValidator;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.agent.validator.AgentValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.service.UserQueryService;

/**
 * Runs move and bulk move through the real {@link TicketHelper}, so the sub-ticket mode of the
 * request decides what moves, and a refused selection leaves the sprint link, the order shift
 * and every ticket untouched.
 */
@ExtendWith(MockitoExtension.class)
class TicketServiceImplUnitMoveTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private BoardAccessService boardAccessService;

    @Mock
    private SprintService sprintService;

    @Mock
    private SprintValidator sprintValidator;

    @Mock
    private BacklogValidator backlogValidator;

    @Mock
    private TicketValidator ticketValidator;

    @Mock
    private TicketMapper ticketMapper;

    @Mock
    private LoggingService loggingService;

    @Mock
    private EntityManager entityManager;

    private TicketServiceImpl ticketService;

    private final List<Ticket> graph = new ArrayList<>();

    private User user;
    private Board board;
    private Backlog backlog;
    private Sprint sprintA;
    private Sprint sprintB;
    private Ticket parent;
    private Ticket unfinishedSub;
    private Ticket doneSub;

    @BeforeEach
    void setUp() {
        TicketHelper ticketHelper = new TicketHelper(
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
        ticketService = new TicketServiceImpl(
                ticketRepository,
                mock(TicketAssigneeRepository.class),
                mock(TicketReviewerRepository.class),
                mock(TicketLabelRepository.class),
                mock(CommentRepository.class),
                boardRepository,
                boardAccessService,
                mock(BoardMemberQueryService.class),
                mock(BoardQueryService.class),
                mock(BoardValidator.class),
                mock(AppAccessValidator.class),
                mock(AgentValidator.class),
                sprintService,
                sprintValidator,
                backlogValidator,
                mock(ProjectValidator.class),
                ticketValidator,
                mock(LabelValidator.class),
                mock(MilestoneValidator.class),
                ticketMapper,
                ticketHelper,
                new TicketStatusNormalizer(new LoggingService()),
                new TicketTypeNormalizer(new LoggingService()),
                mock(UserQueryService.class),
                loggingService,
                entityManager,
                mock(ApplicationEventPublisher.class));

        user = User.builder().build();
        user.setId(UUID.randomUUID());
        board = Board.builder().name("Board").prefix("B").build();
        board.setId(UUID.randomUUID());
        backlog = Backlog.builder().board(board).build();
        backlog.setId(UUID.randomUUID());
        board.setBacklog(backlog);
        sprintA = sprint("Sprint A");
        sprintB = sprint("Sprint B");

        parent = ticket("B-1", null, TicketStatus.IN_PROGRESS, 10);
        unfinishedSub = ticket("B-2", parent, TicketStatus.IN_PROGRESS, 11);
        doneSub = ticket("B-3", parent, TicketStatus.DONE, 12);

        when(boardAccessService.verifyBoardAccess(organizationId, board.getId(), user.getId(), BoardMemberRole.MEMBER))
                .thenReturn(board);
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

    private Ticket ticket(String key, Ticket parentTicket, TicketStatus status, int displayOrder) {
        Ticket ticket = Ticket.builder().title(key).key(key).type(TicketType.FEATURE).status(status)
                .board(board).parentTicket(parentTicket).createdBy(user).displayOrder(displayOrder).build();
        ticket.setId(UUID.randomUUID());
        ticket.setSprint(sprintA);
        graph.add(ticket);
        return ticket;
    }

    private void assertNothingWritten() {
        verify(ticketRepository, never()).saveAll(anyList());
        verify(ticketRepository, never()).shiftDisplayOrderUp(any(), anyInt(), anyInt(), any());
        verify(ticketRepository, never()).shiftDisplayOrderDown(any(), anyInt(), anyInt(), any());
        verifyNoInteractions(entityManager);
    }

    @Nested
    class MoveTicket {

        private void ticketAndSprintBExist(Ticket ticket) {
            when(ticketValidator.ensureTicketExists(board.getId(), ticket.getId())).thenReturn(ticket);
            when(sprintValidator.ensureSprintExistsInOrganization(organizationId, sprintB.getId())).thenReturn(sprintB);
        }

        @Test
        void subTicketToSprint_keepsParentAndLeavesParentAndSiblingsInPlace() {
            // Arrange
            ticketAndSprintBExist(unfinishedSub);

            // Act
            ticketService.moveTicket(user, organizationId, board.getId(), unfinishedSub.getId(),
                    new TicketMoveRequest(sprintB.getId(), null, null, null, null));

            // Assert
            assertThat(unfinishedSub.getSprint()).isEqualTo(sprintB);
            assertThat(unfinishedSub.getParentTicket()).isEqualTo(parent);
            assertThat(parent.getSprint()).isEqualTo(sprintA);
            assertThat(doneSub.getSprint()).isEqualTo(sprintA);
            verify(sprintService).linkSprintToBoardIfNeeded(sprintB, board);
        }

        @Test
        void parentWithoutMode_carriesOnlyUnfinishedSubTickets() {
            // Arrange
            ticketAndSprintBExist(parent);

            // Act
            ticketService.moveTicket(user, organizationId, board.getId(), parent.getId(),
                    new TicketMoveRequest(sprintB.getId(), null, null, null, null));

            // Assert
            assertThat(parent.getSprint()).isEqualTo(sprintB);
            assertThat(unfinishedSub.getSprint()).isEqualTo(sprintB);
            assertThat(doneSub.getSprint()).isEqualTo(sprintA);
        }

        @Test
        void parentWithNone_movesOnlyTheParent() {
            // Arrange
            ticketAndSprintBExist(parent);

            // Act
            ticketService.moveTicket(user, organizationId, board.getId(), parent.getId(),
                    new TicketMoveRequest(sprintB.getId(), null, null, SubTicketMoveMode.NONE, null));

            // Assert
            assertThat(parent.getSprint()).isEqualTo(sprintB);
            assertThat(unfinishedSub.getSprint()).isEqualTo(sprintA);
        }

        @Test
        void parentWithSelected_movesTheTickedSubTicket() {
            // Arrange
            ticketAndSprintBExist(parent);

            // Act
            ticketService.moveTicket(user, organizationId, board.getId(), parent.getId(),
                    new TicketMoveRequest(sprintB.getId(), null, null, SubTicketMoveMode.SELECTED,
                            List.of(doneSub.getId())));

            // Assert
            assertThat(doneSub.getSprint()).isEqualTo(sprintB);
            assertThat(unfinishedSub.getSprint()).isEqualTo(sprintA);
        }

        @Test
        void subTicketIdsWithoutSelected_throwsKaiVal057BeforeSprintLinkAndOrderShift() {
            // Arrange
            ticketAndSprintBExist(parent);
            TicketMoveRequest request = new TicketMoveRequest(sprintB.getId(), null, 2,
                    SubTicketMoveMode.UNFINISHED, List.of(doneSub.getId()));

            // Act
            ValidationException exception = assertThrows(ValidationException.class,
                    () -> ticketService.moveTicket(user, organizationId, board.getId(), parent.getId(), request));

            // Assert
            assertThat(exception.getErrorCode())
                    .isEqualTo(ExceptionDomainKazanError.VAL_SUB_TICKET_IDS_WITHOUT_SELECTED.getCode());
            verify(sprintService, never()).linkSprintToBoardIfNeeded(any(), any());
            assertNothingWritten();
            assertThat(parent.getSprint()).isEqualTo(sprintA);
            assertThat(parent.getDisplayOrder()).isEqualTo(10);
        }

        @Test
        void selectedForeignTicket_throwsKaiVal058BeforeSprintLinkAndOrderShift() {
            // Arrange
            Ticket unrelated = ticket("B-9", null, TicketStatus.TODO, 20);
            ticketAndSprintBExist(parent);
            TicketMoveRequest request = new TicketMoveRequest(sprintB.getId(), null, 2,
                    SubTicketMoveMode.SELECTED, List.of(unrelated.getId()));

            // Act
            ValidationException exception = assertThrows(ValidationException.class,
                    () -> ticketService.moveTicket(user, organizationId, board.getId(), parent.getId(), request));

            // Assert
            assertThat(exception.getErrorCode())
                    .isEqualTo(ExceptionDomainKazanError.VAL_SUB_TICKET_NOT_DESCENDANT.getCode());
            verify(sprintService, never()).linkSprintToBoardIfNeeded(any(), any());
            assertNothingWritten();
            assertThat(unrelated.getSprint()).isEqualTo(sprintA);
        }

        @Test
        void subTicketToBacklogWithOrder_shiftsAfterTheMoveAndKeepsParent() {
            // Arrange
            when(ticketValidator.ensureTicketExists(board.getId(), unfinishedSub.getId())).thenReturn(unfinishedSub);
            when(backlogValidator.ensureBacklogExistsByBoard(backlog.getId(), board.getId())).thenReturn(backlog);

            // Act
            ticketService.moveTicket(user, organizationId, board.getId(), unfinishedSub.getId(),
                    new TicketMoveRequest(null, backlog.getId(), 3, null, null));

            // Assert
            assertThat(unfinishedSub.getBacklog()).isEqualTo(backlog);
            assertThat(unfinishedSub.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
            assertThat(unfinishedSub.getParentTicket()).isEqualTo(parent);
            assertThat(unfinishedSub.getDisplayOrder()).isEqualTo(3);
            verify(ticketRepository).shiftDisplayOrderUp(board.getId(), 3, 11, unfinishedSub.getId());
            verify(ticketRepository).saveAll(List.of(unfinishedSub));
        }
    }

    @Nested
    class BulkMoveTickets {

        private TicketBulkMoveResponse bulkMoveToSprintB(List<Ticket> tickets, SubTicketMoveMode mode,
                List<UUID> subTicketIds) {
            List<UUID> ids = tickets.stream().map(Ticket::getId).toList();
            when(ticketRepository.findAllById(ids)).thenReturn(tickets);
            when(sprintValidator.ensureSprintExists(sprintB.getId())).thenReturn(sprintB);
            return ticketService.bulkMoveTickets(user, organizationId, board.getId(), new TicketBulkMoveRequest(
                    ids, TicketRelocationAction.MOVE_TO_SPRINT, sprintB.getId(), null, mode, subTicketIds, null));
        }

        @Test
        void loneSubTicket_keepsParentAndParentStays() {
            // Act
            TicketBulkMoveResponse response = bulkMoveToSprintB(List.of(unfinishedSub), null, null);

            // Assert
            assertThat(response.movedCount()).isEqualTo(1);
            assertThat(unfinishedSub.getSprint()).isEqualTo(sprintB);
            assertThat(unfinishedSub.getParentTicket()).isEqualTo(parent);
            assertThat(parent.getSprint()).isEqualTo(sprintA);
        }

        @Test
        void parentWithNone_movesOnlyTheParent() {
            // Act
            bulkMoveToSprintB(List.of(parent), SubTicketMoveMode.NONE, null);

            // Assert
            assertThat(parent.getSprint()).isEqualTo(sprintB);
            assertThat(unfinishedSub.getSprint()).isEqualTo(sprintA);
            assertThat(doneSub.getSprint()).isEqualTo(sprintA);
        }

        @Test
        void parentWithUnfinished_carriesUnfinishedSubTicketsAndCountsRequestedTicketsOnly() {
            // Act
            TicketBulkMoveResponse response = bulkMoveToSprintB(List.of(parent), SubTicketMoveMode.UNFINISHED, null);

            // Assert
            assertThat(response.movedCount()).isEqualTo(1);
            assertThat(unfinishedSub.getSprint()).isEqualTo(sprintB);
            assertThat(doneSub.getSprint()).isEqualTo(sprintA);
        }

        @Test
        void selectedListsARequestedSubTicket_movesWithoutKaiVal058() {
            // Act
            bulkMoveToSprintB(List.of(parent, doneSub), SubTicketMoveMode.SELECTED, List.of(doneSub.getId()));

            // Assert
            assertThat(parent.getSprint()).isEqualTo(sprintB);
            assertThat(doneSub.getSprint()).isEqualTo(sprintB);
            assertThat(unfinishedSub.getSprint()).isEqualTo(sprintA);
        }

        @Test
        void subTicketIdsWithNone_throwsKaiVal057AndMovesNothing() {
            // Act
            ValidationException exception = assertThrows(ValidationException.class,
                    () -> bulkMoveToSprintB(List.of(parent), SubTicketMoveMode.NONE, List.of(unfinishedSub.getId())));

            // Assert
            assertThat(exception.getErrorCode())
                    .isEqualTo(ExceptionDomainKazanError.VAL_SUB_TICKET_IDS_WITHOUT_SELECTED.getCode());
            assertNothingWritten();
        }

        @Test
        void subTicketToOtherBoardWithoutParent_throwsKaiVal059AndMovesNothing() {
            // Arrange
            Board targetBoard = Board.builder().name("Target").prefix("T").build();
            targetBoard.setId(UUID.randomUUID());
            Backlog targetBacklog = Backlog.builder().board(targetBoard).build();
            targetBacklog.setId(UUID.randomUUID());
            when(ticketRepository.findAllById(List.of(unfinishedSub.getId()))).thenReturn(List.of(unfinishedSub));
            when(boardAccessService.verifyBoardAccess(organizationId, targetBoard.getId(), user.getId(), BoardMemberRole.MEMBER))
                    .thenReturn(targetBoard);
            when(backlogValidator.ensureBacklogExistsByBoardId(targetBoard.getId())).thenReturn(targetBacklog);
            TicketBulkMoveRequest request = new TicketBulkMoveRequest(List.of(unfinishedSub.getId()),
                    TicketRelocationAction.MOVE_TO_BACKLOG, null, targetBoard.getId(), null, null, null);

            // Act
            ValidationException exception = assertThrows(ValidationException.class,
                    () -> ticketService.bulkMoveTickets(user, organizationId, board.getId(), request));

            // Assert
            assertThat(exception.getErrorCode())
                    .isEqualTo(ExceptionDomainKazanError.VAL_SUB_TICKET_BOARD_CHANGE.getCode());
            assertNothingWritten();
            verify(boardRepository, never()).incrementAndGetTicketCounter(any());
            assertThat(unfinishedSub.getBoard()).isEqualTo(board);
            assertThat(unfinishedSub.getSprint()).isEqualTo(sprintA);
        }

        @Test
        void parentToOtherBoardWithNone_carriesWholeSubtree() {
            // Arrange
            Board targetBoard = Board.builder().name("Target").prefix("T").build();
            targetBoard.setId(UUID.randomUUID());
            Backlog targetBacklog = Backlog.builder().board(targetBoard).build();
            targetBacklog.setId(UUID.randomUUID());
            when(ticketRepository.findAllById(List.of(parent.getId()))).thenReturn(List.of(parent));
            when(boardAccessService.verifyBoardAccess(organizationId, targetBoard.getId(), user.getId(), BoardMemberRole.MEMBER))
                    .thenReturn(targetBoard);
            when(backlogValidator.ensureBacklogExistsByBoardId(targetBoard.getId())).thenReturn(targetBacklog);
            when(boardRepository.incrementAndGetTicketCounter(targetBoard.getId())).thenReturn(1L, 2L, 3L);
            when(ticketRepository.maxDisplayOrderByBoard_Id(targetBoard.getId())).thenReturn(0);

            // Act
            ticketService.bulkMoveTickets(user, organizationId, board.getId(), new TicketBulkMoveRequest(List.of(parent.getId()),
                    TicketRelocationAction.MOVE_TO_BACKLOG, null, targetBoard.getId(), SubTicketMoveMode.NONE, null, null));

            // Assert
            for (Ticket ticket : List.of(parent, unfinishedSub, doneSub)) {
                assertThat(ticket.getBoard()).isEqualTo(targetBoard);
                assertThat(ticket.getBacklog()).isEqualTo(targetBacklog);
            }
            assertThat(doneSub.getParentTicket()).isEqualTo(parent);
        }

        @Test
        void parentToOtherBoardDetachingSubTickets_leavesThemTopLevelOnTheirBoard() {
            // Arrange
            Board targetBoard = Board.builder().name("Target").prefix("T").build();
            targetBoard.setId(UUID.randomUUID());
            Backlog targetBacklog = Backlog.builder().board(targetBoard).build();
            targetBacklog.setId(UUID.randomUUID());
            unfinishedSub.setSprint(null);
            unfinishedSub.setBacklog(backlog);
            when(ticketRepository.findAllById(List.of(parent.getId()))).thenReturn(List.of(parent));
            when(boardAccessService.verifyBoardAccess(organizationId, targetBoard.getId(), user.getId(), BoardMemberRole.MEMBER))
                    .thenReturn(targetBoard);
            when(backlogValidator.ensureBacklogExistsByBoardId(targetBoard.getId())).thenReturn(targetBacklog);
            when(boardRepository.incrementAndGetTicketCounter(targetBoard.getId())).thenReturn(1L);
            when(ticketRepository.maxDisplayOrderByBoard_Id(targetBoard.getId())).thenReturn(0);

            // Act
            ticketService.bulkMoveTickets(user, organizationId, board.getId(), new TicketBulkMoveRequest(List.of(parent.getId()),
                    TicketRelocationAction.MOVE_TO_BACKLOG, null, targetBoard.getId(), null, null, true));

            // Assert
            assertThat(parent.getBoard()).isEqualTo(targetBoard);
            assertThat(parent.getKey()).isEqualTo("T-1");
            for (Ticket subTicket : List.of(unfinishedSub, doneSub)) {
                assertThat(subTicket.getParentTicket()).isNull();
                assertThat(subTicket.getBoard()).isEqualTo(board);
            }
            assertThat(doneSub.getSprint()).isEqualTo(sprintA);
            assertThat(doneSub.getStatus()).isEqualTo(TicketStatus.DONE);
            assertThat(unfinishedSub.getBacklog()).isEqualTo(backlog);
            assertThat(unfinishedSub.getStatus()).isEqualTo(TicketStatus.TODO);
        }

        @Test
        void detachSubTicketsWithinTheBoard_keepsEveryParent() {
            // Arrange
            when(ticketRepository.findAllById(List.of(parent.getId()))).thenReturn(List.of(parent));
            when(sprintValidator.ensureSprintExists(sprintB.getId())).thenReturn(sprintB);

            // Act
            ticketService.bulkMoveTickets(user, organizationId, board.getId(), new TicketBulkMoveRequest(List.of(parent.getId()),
                    TicketRelocationAction.MOVE_TO_SPRINT, sprintB.getId(), null, SubTicketMoveMode.NONE, null, true));

            // Assert
            assertThat(unfinishedSub.getParentTicket()).isEqualTo(parent);
            assertThat(doneSub.getParentTicket()).isEqualTo(parent);
            verify(ticketRepository, never()).saveAllAndFlush(anyList());
        }
    }
}
