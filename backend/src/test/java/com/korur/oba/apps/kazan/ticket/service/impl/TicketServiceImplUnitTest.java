package com.korur.oba.apps.kazan.ticket.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 2. Third-party
import jakarta.persistence.EntityManager;

// 3. Java SDK
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

// 4. Internal
import com.korur.oba.apps.kazan.backlog.entity.Backlog;
import com.korur.oba.apps.kazan.backlog.validator.BacklogValidator;
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.entity.BoardSprint;
import com.korur.oba.apps.kazan.board.enums.BoardMemberRole;
import com.korur.oba.apps.kazan.board.repository.BoardRepository;
import com.korur.oba.apps.kazan.board.service.BoardAccessService;
import com.korur.oba.apps.kazan.board.service.BoardMemberQueryService;
import com.korur.oba.apps.kazan.comment.repository.CommentRepository;
import com.korur.oba.apps.kazan.board.validator.BoardValidator;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.label.entity.Label;
import com.korur.oba.apps.kazan.label.validator.LabelValidator;
import com.korur.oba.apps.kazan.milestone.entity.Milestone;
import com.korur.oba.apps.kazan.milestone.validator.MilestoneValidator;
import com.korur.oba.apps.kazan.project.entity.Project;
import com.korur.oba.apps.kazan.project.validator.ProjectValidator;
import com.korur.oba.apps.kazan.sprint.entity.Sprint;
import com.korur.oba.apps.kazan.sprint.service.SprintService;
import com.korur.oba.apps.kazan.sprint.validator.SprintValidator;
import com.korur.oba.apps.kazan.ticket.dto.request.TicketBulkMoveRequest;
import com.korur.oba.apps.kazan.ticket.dto.request.TicketCreateRequest;
import com.korur.oba.apps.kazan.ticket.dto.request.TicketMoveRequest;
import com.korur.oba.apps.kazan.ticket.dto.request.TicketOrderUpdateRequest;
import com.korur.oba.apps.kazan.ticket.dto.request.TicketUpdateRequest;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.entity.TicketAssignee;
import com.korur.oba.apps.kazan.ticket.entity.TicketReviewer;
import com.korur.oba.apps.kazan.ticket.enums.MissionRank;
import com.korur.oba.apps.kazan.ticket.enums.TicketPriority;
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
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.exception.ExceptionDomainAccessError;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.service.UserQueryService;

@ExtendWith(MockitoExtension.class)
class TicketServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketAssigneeRepository ticketAssigneeRepository;

    @Mock
    private TicketReviewerRepository ticketReviewerRepository;

    @Mock
    private TicketLabelRepository ticketLabelRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private BoardAccessService boardAccessService;

    @Mock
    private SprintValidator sprintValidator;

    @Mock
    private BacklogValidator backlogValidator;

    @Mock
    private ProjectValidator projectValidator;

    @Mock
    private TicketValidator ticketValidator;

    @Mock
    private LabelValidator labelValidator;
    @Mock
    private BoardValidator boardValidator;

    @Mock
    private AppAccessValidator appAccessValidator;

    @Mock
    private TicketMapper ticketMapper;

    @Mock
    private SprintService sprintService;

    @Mock
    private TicketHelper ticketHelper;

    @Mock
    private MilestoneValidator milestoneValidator;

    @Mock
    private BoardMemberQueryService boardMemberQueryService;

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private LoggingService loggingService;

    @Mock
    private EntityManager entityManager;

    // Real instance: the backlog status rule is behaviour under test, not a collaborator to stub.
    @Spy
    private TicketStatusNormalizer ticketStatusNormalizer = new TicketStatusNormalizer(new LoggingService());

    @Spy
    private TicketTypeNormalizer ticketTypeNormalizer = new TicketTypeNormalizer(new LoggingService());

    @InjectMocks
    private TicketServiceImpl ticketServiceImpl;

    @Nested
    class RestoreTicket {

        @Test
        void ticketNotFound_throwsNotFoundException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID ticketId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER)).thenReturn(board);
            when(ticketRepository.findByIdWithDeleted(ticketId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(NotFoundException.class, () ->
                    ticketServiceImpl.restoreTicket(user, organizationId, boardId, ticketId));
        }

        @Test
        void ticketNotDeleted_throwsValidationException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID ticketId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Ticket ticket = Ticket.builder().title("T").key("B-1").type(TicketType.FEATURE).board(board).createdBy(user).build();
            ticket.setId(ticketId);
            // ticket.isDeleted() = false by default

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER)).thenReturn(board);
            when(ticketRepository.findByIdWithDeleted(ticketId)).thenReturn(Optional.of(ticket));
            doThrow(new ValidationException(
                    ExceptionDomainKazanError.VAL_TICKET_NOT_DELETED,
                    Map.of()))
                    .when(ticketValidator).ensureTicketIsDeleted(ticket);

            // Act & Assert
            assertThrows(ValidationException.class, () ->
                    ticketServiceImpl.restoreTicket(user, organizationId, boardId, ticketId));
        }

        @Test
        void ticketBoardMismatch_throwsValidationException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID differentBoardId = UUID.randomUUID();
            UUID ticketId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Board differentBoard = Board.builder().name("Other").prefix("O").build();
            differentBoard.setId(differentBoardId);

            Ticket ticket = Ticket.builder().title("T").key("O-1").type(TicketType.FEATURE).board(differentBoard).createdBy(user).build();
            ticket.setId(ticketId);
            ticket.setDeleted(true);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER)).thenReturn(board);
            when(ticketRepository.findByIdWithDeleted(ticketId)).thenReturn(Optional.of(ticket));

            // Act & Assert
            assertThrows(ValidationException.class, () ->
                    ticketServiceImpl.restoreTicket(user, organizationId, boardId, ticketId));
        }

        @Test
        void deletedTicket_restoresTicketAndChildren() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID ticketId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Ticket ticket = Ticket.builder().title("T").key("B-1").type(TicketType.FEATURE).board(board).createdBy(user).build();
            ticket.setId(ticketId);
            ticket.setDeleted(true);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER)).thenReturn(board);
            when(ticketRepository.findByIdWithDeleted(ticketId)).thenReturn(Optional.of(ticket));
            doNothing().when(ticketRepository).restoreById(ticketId);
            when(commentRepository.restoreAllByTicketId(ticketId)).thenReturn(0);
            when(ticketAssigneeRepository.restoreAllByTicketId(ticketId)).thenReturn(0);
            when(ticketReviewerRepository.restoreAllByTicketId(ticketId)).thenReturn(0);
            when(ticketLabelRepository.restoreAllByTicketId(ticketId)).thenReturn(0);
            when(ticketRepository.findDeletedByParentTicketId(ticketId)).thenReturn(Collections.emptyList());

            // Act
            ticketServiceImpl.restoreTicket(user, organizationId, boardId, ticketId);

            // Assert
            verify(ticketRepository).restoreById(ticketId);
            verify(commentRepository).restoreAllByTicketId(ticketId);
            verify(ticketAssigneeRepository).restoreAllByTicketId(ticketId);
            verify(ticketReviewerRepository).restoreAllByTicketId(ticketId);
            verify(ticketLabelRepository).restoreAllByTicketId(ticketId);
        }
    }

    @Nested
    class BulkMoveTickets {

        @Test
        void deleteAction_throwsValidationException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER)).thenReturn(board);
            doThrow(new ValidationException(
                    ExceptionDomainKazanError.VAL_BULK_MOVE_DELETE_NOT_ALLOWED,
                    Map.of()))
                    .when(ticketValidator).ensureBulkMoveActionValid(TicketRelocationAction.DELETE);

            TicketBulkMoveRequest request = new TicketBulkMoveRequest(
                    List.of(UUID.randomUUID()), TicketRelocationAction.DELETE, null, null, null, null, null);

            // Act & Assert
            assertThrows(ValidationException.class, () ->
                    ticketServiceImpl.bulkMoveTickets(user, organizationId, boardId, request));
        }

        @Test
        void ticketCountMismatch_throwsValidationException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID ticketId1 = UUID.randomUUID();
            UUID ticketId2 = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Ticket ticket = Ticket.builder().title("T").key("B-1").type(TicketType.FEATURE).board(board).createdBy(user).build();
            ticket.setId(ticketId1);

            List<Ticket> foundTickets = List.of(ticket);
            List<UUID> requestedIds = List.of(ticketId1, ticketId2);

            // Request asks for 2 tickets but repository only returns 1 (ticketId2 not found on this board).
            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER)).thenReturn(board);
            when(ticketRepository.findAllById(requestedIds))
                    .thenReturn(foundTickets);
            doThrow(new ValidationException(
                    ExceptionDomainKazanError.VAL_BULK_MOVE_TICKETS_NOT_FOUND,
                    Map.of()))
                    .when(ticketValidator).ensureAllTicketsFound(foundTickets, requestedIds);

            TicketBulkMoveRequest request = new TicketBulkMoveRequest(
                    requestedIds, TicketRelocationAction.MOVE_TO_BACKLOG, null, null, null, null, null);

            // Act & Assert
            assertThrows(ValidationException.class, () ->
                    ticketServiceImpl.bulkMoveTickets(user, organizationId, boardId, request));
        }

        @Test
        void moveToBacklog_sameBoard_delegatesToRelocateTickets() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID ticketId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Ticket ticket = Ticket.builder().title("T").key("B-1").type(TicketType.FEATURE).board(board).createdBy(user).build();
            ticket.setId(ticketId);
            List<Ticket> tickets = List.of(ticket);

            Backlog backlog = Backlog.builder().board(board).build();
            backlog.setId(UUID.randomUUID());

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER)).thenReturn(board);
            when(ticketRepository.findAllById(List.of(ticketId))).thenReturn(tickets);
            when(backlogValidator.ensureBacklogExistsByBoardId(boardId)).thenReturn(backlog);

            TicketBulkMoveRequest request = new TicketBulkMoveRequest(
                    List.of(ticketId), TicketRelocationAction.MOVE_TO_BACKLOG, null, null, null, null, null);

            // Act
            ticketServiceImpl.bulkMoveTickets(user, organizationId, boardId, request);

            // Assert
            verify(backlogValidator).ensureBacklogExistsByBoardId(boardId);
            verify(ticketHelper).moveTickets(
                    eq(tickets), eq(null), eq(null),
                    eq(TicketRelocationAction.MOVE_TO_BACKLOG),
                    eq(null),
                    eq(null),
                    eq(backlog),
                    eq(boardId));
        }

        @Test
        void moveToSprint_sameBoard_delegatesToRelocateTickets() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID ticketId = UUID.randomUUID();
            UUID targetSprintId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Ticket ticket = Ticket.builder().title("T").key("B-1").type(TicketType.FEATURE).board(board).createdBy(user).build();
            ticket.setId(ticketId);
            List<Ticket> tickets = List.of(ticket);

            Sprint targetSprint = Sprint.builder().name("Sprint 2").build();
            targetSprint.setId(targetSprintId);
            // Link target sprint to the same board so the same-board validation passes.
            BoardSprint boardSprint = BoardSprint.builder().board(board).sprint(targetSprint).build();
            boardSprint.setId(UUID.randomUUID());
            targetSprint.getBoardSprints().add(boardSprint);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER)).thenReturn(board);
            when(ticketRepository.findAllById(List.of(ticketId))).thenReturn(tickets);
            when(sprintValidator.ensureSprintExists(targetSprintId)).thenReturn(targetSprint);

            TicketBulkMoveRequest request = new TicketBulkMoveRequest(
                    List.of(ticketId), TicketRelocationAction.MOVE_TO_SPRINT, targetSprintId, null, null, null, null);

            // Act
            ticketServiceImpl.bulkMoveTickets(user, organizationId, boardId, request);

            // Assert
            verify(sprintValidator).ensureSprintExists(targetSprintId);
            verify(ticketHelper).moveTickets(
                    eq(tickets), eq(null), eq(null),
                    eq(TicketRelocationAction.MOVE_TO_SPRINT),
                    eq(null),
                    eq(targetSprint),
                    eq(null),
                    eq(boardId));
        }

        @Test
        void moveToSprint_missingTargetSprintId_throwsValidationException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID ticketId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Ticket ticket = Ticket.builder().title("T").key("B-1").type(TicketType.FEATURE).board(board).createdBy(user).build();
            ticket.setId(ticketId);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER)).thenReturn(board);
            when(ticketRepository.findAllById(List.of(ticketId))).thenReturn(List.of(ticket));
            doThrow(new ValidationException(
                    ExceptionDomainKazanError.VAL_SPRINT_TARGET_SPRINT_REQUIRED,
                    Map.of()))
                    .when(ticketValidator).ensureTargetSprintProvided(null, boardId);

            // targetSprintId is null with MOVE_TO_SPRINT action
            TicketBulkMoveRequest request = new TicketBulkMoveRequest(
                    List.of(ticketId), TicketRelocationAction.MOVE_TO_SPRINT, null, null, null, null, null);

            // Act & Assert
            assertThrows(ValidationException.class, () ->
                    ticketServiceImpl.bulkMoveTickets(user, organizationId, boardId, request));
        }

        @Test
        void moveToBacklog_crossBoard_resolvesTargetBoardAndDelegates() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID targetBoardId = UUID.randomUUID();
            UUID ticketId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Source").prefix("S").build();
            board.setId(boardId);

            Board targetBoard = Board.builder().name("Target").prefix("T").build();
            targetBoard.setId(targetBoardId);

            Ticket ticket = Ticket.builder().title("T").key("S-1").type(TicketType.FEATURE).board(board).createdBy(user).build();
            ticket.setId(ticketId);
            List<Ticket> tickets = List.of(ticket);

            Backlog targetBacklog = Backlog.builder().board(targetBoard).build();
            targetBacklog.setId(UUID.randomUUID());

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER)).thenReturn(board);
            when(ticketRepository.findAllById(List.of(ticketId))).thenReturn(tickets);
            when(boardAccessService.verifyBoardAccess(organizationId, targetBoardId, user.getId(), BoardMemberRole.MEMBER)).thenReturn(targetBoard);
            when(backlogValidator.ensureBacklogExistsByBoardId(targetBoardId)).thenReturn(targetBacklog);

            TicketBulkMoveRequest request = new TicketBulkMoveRequest(
                    List.of(ticketId), TicketRelocationAction.MOVE_TO_BACKLOG, null, targetBoardId, null, null, null);

            // Act
            ticketServiceImpl.bulkMoveTickets(user, organizationId, boardId, request);

            // Assert — boardId called twice: initial board load + per-source-board stream check (ticket is on boardId).
            verify(boardAccessService, times(2)).verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER);
            verify(boardAccessService).verifyBoardAccess(organizationId, targetBoardId, user.getId(), BoardMemberRole.MEMBER);
            verify(backlogValidator).ensureBacklogExistsByBoardId(targetBoardId);
            verify(ticketHelper).moveTickets(
                    eq(tickets), eq(null), eq(null),
                    eq(TicketRelocationAction.MOVE_TO_BACKLOG),
                    eq(targetBoard),
                    eq(null),
                    eq(targetBacklog),
                    eq(boardId));
        }

        @Test
        void crossBoard_userLacksPermissionOnTargetBoard_throwsForbiddenException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID targetBoardId = UUID.randomUUID();
            UUID ticketId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Source").prefix("S").build();
            board.setId(boardId);

            Board targetBoard = Board.builder().name("Target").prefix("T").build();
            targetBoard.setId(targetBoardId);

            Ticket ticket = Ticket.builder().title("T").key("S-1").type(TicketType.FEATURE).board(board).createdBy(user).build();
            ticket.setId(ticketId);

            // Source board access passes (used for initial board load and per-source-board check).
            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER)).thenReturn(board);
            when(ticketRepository.findAllById(List.of(ticketId))).thenReturn(List.of(ticket));
            // Effective destination board access check fails.
            doThrow(new ForbiddenException(
                    ExceptionDomainKazanError.SEC_INSUFFICIENT_BOARD_PERMISSIONS,
                    Map.of()))
                    .when(boardAccessService).verifyBoardAccess(organizationId, targetBoardId, user.getId(), BoardMemberRole.MEMBER);

            TicketBulkMoveRequest request = new TicketBulkMoveRequest(
                    List.of(ticketId), TicketRelocationAction.MOVE_TO_BACKLOG, null, targetBoardId, null, null, null);

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    ticketServiceImpl.bulkMoveTickets(user, organizationId, boardId, request));
        }

        @Test
        void moveToBacklog_crossBoardTickets_noTargetBoardId_setsTargetBoardToUrlBoard() {
            // Arrange
            UUID boardAId = UUID.randomUUID();
            UUID boardBId = UUID.randomUUID();
            UUID ticketId1 = UUID.randomUUID();
            UUID ticketId2 = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board boardA = Board.builder().name("Board A").prefix("A").build();
            boardA.setId(boardAId);

            Board boardB = Board.builder().name("Board B").prefix("B").build();
            boardB.setId(boardBId);

            Ticket ticketOnA = Ticket.builder().title("T1").key("A-1").type(TicketType.FEATURE).board(boardA).createdBy(user).build();
            ticketOnA.setId(ticketId1);

            Ticket ticketOnB = Ticket.builder().title("T2").key("B-1").type(TicketType.FEATURE).board(boardB).createdBy(user).build();
            ticketOnB.setId(ticketId2);

            List<UUID> requestedIds = List.of(ticketId1, ticketId2);
            List<Ticket> tickets = List.of(ticketOnA, ticketOnB);

            Backlog backlog = Backlog.builder().board(boardA).build();
            backlog.setId(UUID.randomUUID());

            // boardA is used for initial board load, source-board check, and effective destination.
            when(boardAccessService.verifyBoardAccess(organizationId, boardAId, user.getId(), BoardMemberRole.MEMBER)).thenReturn(boardA);
            when(boardAccessService.verifyBoardAccess(organizationId, boardBId, user.getId(), BoardMemberRole.MEMBER)).thenReturn(boardB);
            when(ticketRepository.findAllById(requestedIds)).thenReturn(tickets);
            when(backlogValidator.ensureBacklogExistsByBoardId(boardAId)).thenReturn(backlog);

            // targetBoardId is null — tickets come from two different boards
            TicketBulkMoveRequest request = new TicketBulkMoveRequest(
                    requestedIds, TicketRelocationAction.MOVE_TO_BACKLOG, null, null, null, null, null);

            // Act
            ticketServiceImpl.bulkMoveTickets(user, organizationId, boardAId, request);

            // Assert — verifyBoardAccess is called: once for initial board, once per distinct source board.
            // boardA appears twice: initial load + source-board check; boardB once as source-board check.
            verify(boardAccessService, times(2)).verifyBoardAccess(organizationId, boardAId, user.getId(), BoardMemberRole.MEMBER);
            verify(boardAccessService).verifyBoardAccess(organizationId, boardBId, user.getId(), BoardMemberRole.MEMBER);

            // The key assertion: targetBoard should be boardA (URL board), not null,
            // so that relocateTickets applies cross-board field updates for ticketOnB.
            verify(ticketHelper).moveTickets(
                    eq(tickets), eq(null), eq(null),
                    eq(TicketRelocationAction.MOVE_TO_BACKLOG),
                    eq(boardA),
                    eq(null),
                    eq(backlog),
                    eq(boardAId));
        }
    }

    @Nested
    class DeprecatedTaskTypeAlias {

        private User user;
        private Board board;
        private UUID boardId;

        @BeforeEach
        void setUpFixtures() {
            boardId = UUID.randomUUID();
            user = User.builder().build();
            user.setId(UUID.randomUUID());
            board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);
            lenient().when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER)).thenReturn(board);
        }

        @Test
        @SuppressWarnings("deprecation")
        void createTicket_typeTask_storesFeature() {
            // Arrange
            UUID backlogId = UUID.randomUUID();
            Backlog backlog = Backlog.builder().board(board).build();
            backlog.setId(backlogId);
            Ticket mapped = Ticket.builder()
                    .title("T").type(TicketType.TASK).priority(TicketPriority.MEDIUM).status(TicketStatus.TODO)
                    .build();
            TicketCreateRequest request = new TicketCreateRequest(
                    "T", null, TicketType.TASK, TicketPriority.MEDIUM, TicketStatus.TODO,
                    null, null, null, null, null,
                    null, backlogId, null, null, null,
                    null, null, null);
            when(ticketMapper.toEntity(request)).thenReturn(mapped);
            when(backlogValidator.ensureBacklogExistsByBoard(backlogId, boardId)).thenReturn(backlog);
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ticketServiceImpl.createTicket(user, organizationId, boardId, request);

            // Assert
            assertThat(mapped.getType()).isEqualTo(TicketType.FEATURE);
        }

        @Test
        @SuppressWarnings("deprecation")
        void createTicketDirect_typeTask_storesFeature() {
            // Arrange
            Backlog backlog = Backlog.builder().board(board).build();
            backlog.setId(UUID.randomUUID());
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ticketServiceImpl.createTicketDirect(
                    user, board, backlog, "T", null, "B-1", TicketType.TASK, TicketPriority.MEDIUM);

            // Assert
            ArgumentCaptor<Ticket> saved = ArgumentCaptor.forClass(Ticket.class);
            verify(ticketRepository).save(saved.capture());
            assertThat(saved.getValue().getType()).isEqualTo(TicketType.FEATURE);
        }

        @Test
        @SuppressWarnings("deprecation")
        void createTicketDirect_anySuggestion_storesMissionRankM() {
            // Arrange
            Backlog backlog = Backlog.builder().board(board).build();
            backlog.setId(UUID.randomUUID());
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ticketServiceImpl.createTicketDirect(
                    user, board, backlog, "T", null, "B-1", TicketType.BUG, TicketPriority.HIGH);

            // Assert
            ArgumentCaptor<Ticket> saved = ArgumentCaptor.forClass(Ticket.class);
            verify(ticketRepository).save(saved.capture());
            assertThat(saved.getValue().getMissionRank()).isEqualTo(MissionRank.M);
        }

        @Test
        @SuppressWarnings("deprecation")
        void updateTicket_typeTask_storesFeature() {
            // Arrange
            UUID ticketId = UUID.randomUUID();
            Ticket ticket = Ticket.builder()
                    .title("T").key("B-1").type(TicketType.BUG).status(TicketStatus.TODO)
                    .board(board).createdBy(user).build();
            ticket.setId(ticketId);
            TicketUpdateRequest request = new TicketUpdateRequest(
                    null, null, TicketType.TASK, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null);
            when(ticketValidator.ensureTicketExists(boardId, ticketId)).thenReturn(ticket);
            doAnswer(invocation -> {
                ((Ticket) invocation.getArgument(0)).setType(TicketType.TASK);
                return null;
            }).when(ticketMapper).updateEntity(any(Ticket.class), any(TicketUpdateRequest.class));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ticketServiceImpl.updateTicket(user, organizationId, boardId, ticketId, request);

            // Assert
            assertThat(ticket.getType()).isEqualTo(TicketType.FEATURE);
        }

        @Test
        @SuppressWarnings("deprecation")
        void updateTicket_typeTaskWithAssigneeIds_storesFeatureBeforeFirstSaveAndFlush() {
            // Arrange
            UUID ticketId = UUID.randomUUID();
            Ticket ticket = Ticket.builder()
                    .title("T").key("B-1").type(TicketType.BUG).status(TicketStatus.TODO)
                    .board(board).createdBy(user).build();
            ticket.setId(ticketId);
            TicketUpdateRequest request = new TicketUpdateRequest(
                    null, null, TicketType.TASK, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, List.of(), null, null);
            when(ticketValidator.ensureTicketExists(boardId, ticketId)).thenReturn(ticket);
            doAnswer(invocation -> {
                ((Ticket) invocation.getArgument(0)).setType(TicketType.TASK);
                return null;
            }).when(ticketMapper).updateEntity(any(Ticket.class), any(TicketUpdateRequest.class));
            AtomicReference<TicketType> typeAtFlush = new AtomicReference<>();
            when(ticketRepository.saveAndFlush(any(Ticket.class))).thenAnswer(invocation -> {
                Ticket flushed = invocation.getArgument(0);
                typeAtFlush.set(flushed.getType());
                return flushed;
            });
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ticketServiceImpl.updateTicket(user, organizationId, boardId, ticketId, request);

            // Assert
            verify(ticketRepository).saveAndFlush(any(Ticket.class));
            assertThat(typeAtFlush.get()).isEqualTo(TicketType.FEATURE);
        }
    }

    @Nested
    class BacklogStatusRule {

        @Test
        void createTicket_withoutSprintAndNonTodoStatus_storesTodo() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID backlogId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Backlog backlog = Backlog.builder().board(board).build();
            backlog.setId(backlogId);

            Ticket mapped = Ticket.builder()
                    .title("T")
                    .type(TicketType.FEATURE)
                    .priority(TicketPriority.MEDIUM)
                    .status(TicketStatus.IN_PROGRESS)
                    .build();

            TicketCreateRequest request = new TicketCreateRequest(
                    "T", null, TicketType.FEATURE, TicketPriority.MEDIUM, TicketStatus.IN_PROGRESS,
                    null, null, null, null, null,
                    null, backlogId, null, null, null,
                    null, null, null);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER)).thenReturn(board);
            when(ticketMapper.toEntity(request)).thenReturn(mapped);
            when(backlogValidator.ensureBacklogExistsByBoard(backlogId, boardId)).thenReturn(backlog);
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ticketServiceImpl.createTicket(user, organizationId, boardId, request);

            // Assert
            assertThat(mapped.getStatus()).isEqualTo(TicketStatus.TODO);
            assertThat(mapped.getBacklog()).isEqualTo(backlog);
            verify(ticketRepository).save(mapped);
        }

        @Test
        void updateTicket_backlogTicketSetToInProgress_resetsStatusToTodo() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID ticketId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Backlog backlog = Backlog.builder().board(board).build();
            backlog.setId(UUID.randomUUID());

            // Ticket sits in the backlog: no sprint.
            Ticket ticket = Ticket.builder()
                    .title("T").key("B-1").type(TicketType.FEATURE)
                    .status(TicketStatus.TODO)
                    .board(board).backlog(backlog).createdBy(user)
                    .build();
            ticket.setId(ticketId);

            TicketUpdateRequest request = new TicketUpdateRequest(
                    null, null, null, TicketStatus.IN_PROGRESS, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER)).thenReturn(board);
            when(ticketValidator.ensureTicketExists(boardId, ticketId)).thenReturn(ticket);
            doAnswer(invocation -> {
                Ticket target = invocation.getArgument(0);
                target.setStatus(TicketStatus.IN_PROGRESS);
                return null;
            }).when(ticketMapper).updateEntity(any(Ticket.class), any(TicketUpdateRequest.class));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ticketServiceImpl.updateTicket(user, organizationId, boardId, ticketId, request);

            // Assert
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.TODO);
            verify(ticketRepository).save(ticket);
        }

        @Test
        void updateTicket_backlogSubTicketSetToDone_keepsDone() {
            // A sub-ticket inherits its parent's backlog placement, but its status
            // is the completion checkbox that drives the parent's percentage. The
            // backlog status rule must not reset it.

            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID ticketId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Backlog backlog = Backlog.builder().board(board).build();
            backlog.setId(UUID.randomUUID());

            Ticket parentTicket = Ticket.builder()
                    .title("P").key("B-1").type(TicketType.FEATURE)
                    .status(TicketStatus.TODO)
                    .board(board).backlog(backlog).createdBy(user)
                    .build();
            parentTicket.setId(UUID.randomUUID());

            // Sub-ticket sits in the same backlog as its parent: no sprint.
            Ticket subTicket = Ticket.builder()
                    .title("S").key("B-2").type(TicketType.FEATURE)
                    .status(TicketStatus.TODO)
                    .board(board).backlog(backlog).parentTicket(parentTicket).createdBy(user)
                    .build();
            subTicket.setId(ticketId);

            TicketUpdateRequest request = new TicketUpdateRequest(
                    null, null, null, TicketStatus.DONE, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER)).thenReturn(board);
            when(ticketValidator.ensureTicketExists(boardId, ticketId)).thenReturn(subTicket);
            doAnswer(invocation -> {
                Ticket target = invocation.getArgument(0);
                target.setStatus(TicketStatus.DONE);
                return null;
            }).when(ticketMapper).updateEntity(any(Ticket.class), any(TicketUpdateRequest.class));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ticketServiceImpl.updateTicket(user, organizationId, boardId, ticketId, request);

            // Assert
            assertThat(subTicket.getStatus()).isEqualTo(TicketStatus.DONE);
            verify(ticketRepository).save(subTicket);
        }

        @Test
        void updateTicket_doneTicketMovedIntoSprint_keepsStatus() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID ticketId = UUID.randomUUID();
            UUID parentTicketId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Backlog backlog = Backlog.builder().board(board).build();
            backlog.setId(UUID.randomUUID());

            Sprint sprint = Sprint.builder().name("Sprint").build();
            sprint.setId(UUID.randomUUID());

            // Ticket starts in the backlog and is pulled into the parent's sprint.
            Ticket ticket = Ticket.builder()
                    .title("T").key("B-1").type(TicketType.FEATURE)
                    .status(TicketStatus.DONE)
                    .board(board).backlog(backlog).createdBy(user)
                    .build();
            ticket.setId(ticketId);

            Ticket parentTicket = Ticket.builder()
                    .title("P").key("B-2").type(TicketType.FEATURE)
                    .status(TicketStatus.IN_PROGRESS)
                    .board(board).sprint(sprint).createdBy(user)
                    .build();
            parentTicket.setId(parentTicketId);

            TicketUpdateRequest request = new TicketUpdateRequest(
                    null, null, null, null, null, null, null, null, parentTicketId, null, null,
                    null, null, null, null, null, null, null, null);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER)).thenReturn(board);
            when(ticketValidator.ensureTicketExists(boardId, ticketId)).thenReturn(ticket);
            when(ticketValidator.ensureTicketExists(boardId, parentTicketId)).thenReturn(parentTicket);
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
            // Stands in for TicketHelper.assignParent, whose placement rule TicketHelperUnitTest covers.
            doAnswer(invocation -> {
                ticket.setParentTicket(parentTicket);
                ticket.setSprint(sprint);
                ticket.setBacklog(null);
                return null;
            }).when(ticketHelper).assignParent(ticket, parentTicket);

            // Act
            ticketServiceImpl.updateTicket(user, organizationId, boardId, ticketId, request);

            // Assert
            assertThat(ticket.getSprint()).isEqualTo(sprint);
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.DONE);
        }
    }

    @Nested
    class StatusRuleValidation {

        private final UUID boardId = UUID.randomUUID();
        private final UUID ticketId = UUID.randomUUID();
        private User user;
        private Board board;
        private Sprint sprint;

        @BeforeEach
        void setUp() {
            user = User.builder().build();
            user.setId(UUID.randomUUID());
            board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);
            sprint = Sprint.builder().name("Sprint").build();
            sprint.setId(UUID.randomUUID());

            // The service delegates both rules to the validator, so the real rules run here.
            TicketValidator realValidator = new TicketValidator(ticketRepository, loggingService);
            lenient().doAnswer(invocation -> {
                realValidator.ensureBacklogTicketStatusAllowed(invocation.getArgument(0));
                return null;
            }).when(ticketValidator).ensureBacklogTicketStatusAllowed(any(Ticket.class));
            lenient().doAnswer(invocation -> {
                realValidator.ensureIncidentCanBeClosed(invocation.getArgument(0), invocation.getArgument(1));
                return null;
            }).when(ticketValidator).ensureIncidentCanBeClosed(any(Ticket.class), any());
            lenient().when(boardAccessService.verifyBoardAccess(
                    organizationId, boardId, user.getId(), BoardMemberRole.MEMBER)).thenReturn(board);
            lenient().when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        }

        private Ticket storedTicket(TicketType type, TicketStatus status, Sprint ticketSprint) {
            Backlog backlog = Backlog.builder().board(board).build();
            backlog.setId(UUID.randomUUID());
            Ticket ticket = Ticket.builder()
                    .title("T").key("B-1").type(type).status(status)
                    .board(board).createdBy(user).build();
            if (ticketSprint != null) {
                ticket.setSprint(ticketSprint);
            } else {
                ticket.setBacklog(backlog);
            }
            ticket.setId(ticketId);
            lenient().when(ticketValidator.ensureTicketExists(boardId, ticketId)).thenReturn(ticket);
            return ticket;
        }

        // Stands in for TicketMapper.updateEntity, which copies the non-null request fields.
        private void stubUpdateEntity() {
            doAnswer(invocation -> {
                Ticket target = invocation.getArgument(0);
                TicketUpdateRequest source = invocation.getArgument(1);
                if (source.type() != null) {
                    target.setType(source.type());
                }
                if (source.status() != null) {
                    target.setStatus(source.status());
                }
                if (source.rca() != null) {
                    target.setRca(source.rca());
                }
                if (source.lessonsLearned() != null) {
                    target.setLessonsLearned(source.lessonsLearned());
                }
                return null;
            }).when(ticketMapper).updateEntity(any(Ticket.class), any(TicketUpdateRequest.class));
        }

        private TicketUpdateRequest updateRequest(TicketType type, TicketStatus status, String rca, String lessons) {
            return new TicketUpdateRequest(
                    null, null, type, status, null, null, null, null,
                    null, null, null, null, null, rca, lessons, null, null, null, null);
        }

        @Test
        void updateTicket_backlogTicketWithoutStatus_normalizesWithoutValidatingBacklogStatus() {
            // Arrange
            Ticket ticket = storedTicket(TicketType.FEATURE, TicketStatus.IN_PROGRESS, null);
            stubUpdateEntity();

            // Act
            ticketServiceImpl.updateTicket(user, organizationId, boardId, ticketId,
                    updateRequest(null, null, null, null));

            // Assert
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.TODO);
            verify(ticketValidator, never()).ensureBacklogTicketStatusAllowed(any(Ticket.class));
            verify(ticketRepository).save(ticket);
        }

        @Test
        void updateTicket_backlogTicketSetToInProgress_throwsAndSavesNothing() {
            // Arrange
            storedTicket(TicketType.FEATURE, TicketStatus.TODO, null);
            stubUpdateEntity();
            TicketUpdateRequest request = updateRequest(null, TicketStatus.IN_PROGRESS, null, null);

            // Act and Assert
            assertThatThrownBy(() -> ticketServiceImpl.updateTicket(user, organizationId, boardId, ticketId, request))
                    .isInstanceOfSatisfying(ValidationException.class, ex -> assertThat(ex.getErrorCode())
                            .isEqualTo(ExceptionDomainKazanError.VAL_TICKET_BACKLOG_STATUS.getCode()));
            verify(ticketRepository, never()).save(any(Ticket.class));
        }

        @Test
        void updateTicket_incidentToDoneWithBothTextsInRequest_succeeds() {
            // Arrange
            Ticket ticket = storedTicket(TicketType.INCIDENT, TicketStatus.IN_PROGRESS, sprint);
            stubUpdateEntity();

            // Act
            ticketServiceImpl.updateTicket(user, organizationId, boardId, ticketId,
                    updateRequest(null, TicketStatus.DONE, "cause", "lessons"));

            // Assert
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.DONE);
            verify(ticketValidator).ensureIncidentCanBeClosed(ticket, TicketStatus.IN_PROGRESS);
            verify(ticketRepository).save(ticket);
        }

        @Test
        void updateTicket_incidentToDoneWithoutTexts_throwsAndSavesNothing() {
            // Arrange
            storedTicket(TicketType.INCIDENT, TicketStatus.IN_PROGRESS, sprint);
            stubUpdateEntity();
            TicketUpdateRequest request = updateRequest(null, TicketStatus.DONE, null, null);

            // Act and Assert
            assertThatThrownBy(() -> ticketServiceImpl.updateTicket(user, organizationId, boardId, ticketId, request))
                    .isInstanceOfSatisfying(ValidationException.class, ex -> assertThat(ex.getErrorCode())
                            .isEqualTo(ExceptionDomainKazanError.VAL_INCIDENT_CLOSE_REQUIRES_RCA.getCode()));
            verify(ticketRepository, never()).save(any(Ticket.class));
        }

        @Test
        void updateTicket_alreadyDoneIncidentWithoutTexts_succeeds() {
            // Arrange
            Ticket ticket = storedTicket(TicketType.INCIDENT, TicketStatus.DONE, sprint);
            stubUpdateEntity();

            // Act
            ticketServiceImpl.updateTicket(user, organizationId, boardId, ticketId,
                    updateRequest(null, TicketStatus.DONE, null, null));

            // Assert
            verify(ticketRepository).save(ticket);
        }

        @Test
        void updateTicket_featureToDoneWithoutRca_succeedsWithoutIncidentCheck() {
            // Arrange
            Ticket ticket = storedTicket(TicketType.FEATURE, TicketStatus.IN_PROGRESS, sprint);
            stubUpdateEntity();

            // Act
            ticketServiceImpl.updateTicket(user, organizationId, boardId, ticketId,
                    updateRequest(null, TicketStatus.DONE, null, null));

            // Assert
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.DONE);
            verify(ticketValidator, never()).ensureIncidentCanBeClosed(any(Ticket.class), any());
            verify(ticketRepository).save(ticket);
        }

        @Test
        void updateTicket_featureChangedToIncidentAndDoneWithoutRca_throwsAndSavesNothing() {
            // Arrange
            storedTicket(TicketType.FEATURE, TicketStatus.IN_PROGRESS, sprint);
            stubUpdateEntity();
            TicketUpdateRequest request = updateRequest(TicketType.INCIDENT, TicketStatus.DONE, null, null);

            // Act and Assert
            assertThatThrownBy(() -> ticketServiceImpl.updateTicket(user, organizationId, boardId, ticketId, request))
                    .isInstanceOfSatisfying(ValidationException.class, ex -> assertThat(ex.getErrorCode())
                            .isEqualTo(ExceptionDomainKazanError.VAL_INCIDENT_CLOSE_REQUIRES_RCA.getCode()));
            verify(ticketRepository, never()).save(any(Ticket.class));
        }

        private TicketCreateRequest createRequest(TicketType type, TicketStatus status, UUID sprintId, UUID backlogId) {
            return new TicketCreateRequest(
                    "T", null, type, null, status,
                    null, null, null, null, null,
                    sprintId, backlogId, null, null, null,
                    null, null, null);
        }

        private void stubCreate(Ticket mapped, TicketCreateRequest request) {
            when(boardRepository.incrementAndGetTicketCounter(boardId)).thenReturn(1L);
            when(ticketMapper.toEntity(request)).thenReturn(mapped);
        }

        @Test
        void createTicket_backlogWithInProgress_throwsAndSavesNothing() {
            // Arrange
            UUID backlogId = UUID.randomUUID();
            Backlog backlog = Backlog.builder().board(board).build();
            backlog.setId(backlogId);
            Ticket mapped = Ticket.builder().title("T").type(TicketType.FEATURE).status(TicketStatus.IN_PROGRESS).build();
            TicketCreateRequest request = createRequest(TicketType.FEATURE, TicketStatus.IN_PROGRESS, null, backlogId);
            stubCreate(mapped, request);
            when(backlogValidator.ensureBacklogExistsByBoard(backlogId, boardId)).thenReturn(backlog);

            // Act and Assert
            assertThatThrownBy(() -> ticketServiceImpl.createTicket(user, organizationId, boardId, request))
                    .isInstanceOfSatisfying(ValidationException.class, ex -> assertThat(ex.getErrorCode())
                            .isEqualTo(ExceptionDomainKazanError.VAL_TICKET_BACKLOG_STATUS.getCode()));
            verify(ticketRepository, never()).save(any(Ticket.class));
        }

        @Test
        void createTicket_sprintIncidentDoneWithoutRca_throwsAndSavesNothing() {
            // Arrange
            Ticket mapped = Ticket.builder().title("T").type(TicketType.INCIDENT).status(TicketStatus.DONE).build();
            TicketCreateRequest request = createRequest(TicketType.INCIDENT, TicketStatus.DONE, sprint.getId(), null);
            stubCreate(mapped, request);
            when(sprintValidator.ensureSprintExistsInOrganization(organizationId, sprint.getId())).thenReturn(sprint);

            // Act and Assert
            assertThatThrownBy(() -> ticketServiceImpl.createTicket(user, organizationId, boardId, request))
                    .isInstanceOfSatisfying(ValidationException.class, ex -> assertThat(ex.getErrorCode())
                            .isEqualTo(ExceptionDomainKazanError.VAL_INCIDENT_CLOSE_REQUIRES_RCA.getCode()));
            verify(ticketValidator).ensureIncidentCanBeClosed(mapped, null);
            verify(ticketRepository, never()).save(any(Ticket.class));
        }

        @Test
        void createTicket_sprintIncidentDoneWithBothTexts_succeeds() {
            // Arrange
            Ticket mapped = Ticket.builder().title("T").type(TicketType.INCIDENT).status(TicketStatus.DONE)
                    .rca("cause").lessonsLearned("lessons").build();
            TicketCreateRequest request = createRequest(TicketType.INCIDENT, TicketStatus.DONE, sprint.getId(), null);
            stubCreate(mapped, request);
            when(sprintValidator.ensureSprintExistsInOrganization(organizationId, sprint.getId())).thenReturn(sprint);

            // Act
            ticketServiceImpl.createTicket(user, organizationId, boardId, request);

            // Assert
            verify(ticketRepository).save(mapped);
        }
    }

    @Nested
    class MoveTicket {

        private UUID boardId;
        private UUID ticketId;
        private User user;
        private Board board;
        private Ticket ticket;

        @BeforeEach
        void setUp() {
            boardId = UUID.randomUUID();
            ticketId = UUID.randomUUID();
            user = User.builder().build();
            user.setId(UUID.randomUUID());
            board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);
            ticket = Ticket.builder().title("T").key("B-1").type(TicketType.FEATURE)
                    .status(TicketStatus.DONE).board(board).createdBy(user).build();
            ticket.setId(ticketId);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER)).thenReturn(board);
            when(ticketValidator.ensureTicketExists(boardId, ticketId)).thenReturn(ticket);
        }

        @Test
        void moveTicket_toBacklog_relocatesTicketWithItsSubTickets() {
            // The relocation carries sub-tickets, unparents a sub-ticket moved apart from its
            // parent and resets backlog statuses; TicketHelperUnitTest covers those rules.

            // Arrange
            Backlog backlog = Backlog.builder().board(board).build();
            backlog.setId(UUID.randomUUID());
            when(backlogValidator.ensureBacklogExistsByBoard(backlog.getId(), boardId)).thenReturn(backlog);

            // Act
            ticketServiceImpl.moveTicket(user, organizationId, boardId, ticketId, new TicketMoveRequest(null, backlog.getId(), null, null, null));

            // Assert
            verify(ticketHelper).moveTickets(
                    List.of(ticket), null, null, TicketRelocationAction.MOVE_TO_BACKLOG, null, null, backlog, boardId);
            verify(ticketRepository, never()).save(any(Ticket.class));
        }

        @Test
        void moveTicket_toSprint_linksSprintAndRelocatesTicketWithItsSubTickets() {
            // Arrange
            Sprint sprint = Sprint.builder().name("Sprint").build();
            sprint.setId(UUID.randomUUID());
            when(sprintValidator.ensureSprintExistsInOrganization(organizationId, sprint.getId())).thenReturn(sprint);

            // Act
            ticketServiceImpl.moveTicket(user, organizationId, boardId, ticketId, new TicketMoveRequest(sprint.getId(), null, null, null, null));

            // Assert
            verify(sprintService).linkSprintToBoardIfNeeded(sprint, board);
            verify(ticketHelper).moveTickets(
                    List.of(ticket), null, null, TicketRelocationAction.MOVE_TO_SPRINT, null, sprint, null, boardId);
        }

        @Test
        void moveTicket_orderLowerThanCurrent_shiftsRangeUpAndSetsOrderAfterRelocation() {
            // Arrange
            ticket.setDisplayOrder(12);
            Backlog backlog = backlogOnBoard();
            AtomicReference<Integer> orderAtRelocation = captureOrderAtRelocation();

            // Act
            ticketServiceImpl.moveTicket(user, organizationId, boardId, ticketId, new TicketMoveRequest(null, backlog.getId(), 5, null, null));

            // Assert
            assertThat(ticket.getDisplayOrder()).isEqualTo(5);
            assertThat(orderAtRelocation.get()).isEqualTo(12);
            InOrder order = inOrder(entityManager, ticketRepository, ticketValidator);
            order.verify(entityManager).flush();
            order.verify(ticketRepository).shiftDisplayOrderUp(boardId, 5, 12, ticketId);
            order.verify(entityManager).flush();
            order.verify(entityManager).clear();
            order.verify(ticketValidator).ensureTicketExists(boardId, ticketId);
            verify(ticketRepository, never()).shiftDisplayOrderDown(any(), anyInt(), anyInt(), any());
            verify(ticketValidator, never()).ensureTicketOrderIsCorrect(anyInt(), anyInt());
            verify(ticketRepository, never()).save(any(Ticket.class));
        }

        @Test
        void moveTicket_orderHigherThanCurrent_shiftsRangeDownAndSetsOrderAfterRelocation() {
            // Arrange
            ticket.setDisplayOrder(5);
            Sprint sprint = Sprint.builder().name("Sprint").build();
            sprint.setId(UUID.randomUUID());
            when(sprintValidator.ensureSprintExistsInOrganization(organizationId, sprint.getId())).thenReturn(sprint);
            AtomicReference<Integer> orderAtRelocation = captureOrderAtRelocation();

            // Act
            ticketServiceImpl.moveTicket(user, organizationId, boardId, ticketId, new TicketMoveRequest(sprint.getId(), null, 12, null, null));

            // Assert
            assertThat(ticket.getDisplayOrder()).isEqualTo(12);
            assertThat(orderAtRelocation.get()).isEqualTo(5);
            verify(ticketRepository).shiftDisplayOrderDown(boardId, 5, 12, ticketId);
            verify(ticketRepository, never()).shiftDisplayOrderUp(any(), anyInt(), anyInt(), any());
            verify(ticketHelper).moveTickets(
                    List.of(ticket), null, null, TicketRelocationAction.MOVE_TO_SPRINT, null, sprint, null, boardId);
        }

        @Test
        void moveTicket_nullOrder_keepsOrderWithoutShifting() {
            // Arrange
            ticket.setDisplayOrder(7);
            Backlog backlog = backlogOnBoard();

            // Act
            ticketServiceImpl.moveTicket(user, organizationId, boardId, ticketId, new TicketMoveRequest(null, backlog.getId(), null, null, null));

            // Assert
            assertThat(ticket.getDisplayOrder()).isEqualTo(7);
            verify(ticketRepository, never()).shiftDisplayOrderUp(any(), anyInt(), anyInt(), any());
            verify(ticketRepository, never()).shiftDisplayOrderDown(any(), anyInt(), anyInt(), any());
            verify(entityManager, never()).clear();
            verify(ticketHelper).moveTickets(
                    List.of(ticket), null, null, TicketRelocationAction.MOVE_TO_BACKLOG, null, null, backlog, boardId);
        }

        @Test
        void moveTicket_orderEqualToCurrent_movesWithoutShiftingOrRejecting() {
            // A client with a stale list may send the ticket's own order; the move still succeeds.

            // Arrange
            ticket.setDisplayOrder(7);
            Backlog backlog = backlogOnBoard();

            // Act
            ticketServiceImpl.moveTicket(user, organizationId, boardId, ticketId, new TicketMoveRequest(null, backlog.getId(), 7, null, null));

            // Assert
            assertThat(ticket.getDisplayOrder()).isEqualTo(7);
            verify(ticketValidator, never()).ensureTicketOrderIsCorrect(anyInt(), anyInt());
            verify(ticketRepository, never()).shiftDisplayOrderUp(any(), anyInt(), anyInt(), any());
            verify(ticketRepository, never()).shiftDisplayOrderDown(any(), anyInt(), anyInt(), any());
            verify(ticketHelper).moveTickets(
                    List.of(ticket), null, null, TicketRelocationAction.MOVE_TO_BACKLOG, null, null, backlog, boardId);
        }

        private Backlog backlogOnBoard() {
            Backlog backlog = Backlog.builder().board(board).build();
            backlog.setId(UUID.randomUUID());
            when(backlogValidator.ensureBacklogExistsByBoard(backlog.getId(), boardId)).thenReturn(backlog);
            return backlog;
        }

        // Records the ticket's order when the relocation runs, which proves the order is only set
        // after the relocation has validated and moved the ticket.
        private AtomicReference<Integer> captureOrderAtRelocation() {
            AtomicReference<Integer> orderAtRelocation = new AtomicReference<>();
            doAnswer(invocation -> {
                List<Ticket> relocated = invocation.getArgument(0);
                orderAtRelocation.set(relocated.get(0).getDisplayOrder());
                return null;
            }).when(ticketHelper).moveTickets(
                    any(), any(), any(), any(TicketRelocationAction.class), any(), any(), any(), eq(boardId));
            return orderAtRelocation;
        }
    }

    @Nested
    class MilestoneAttachment {

        private UUID boardId;
        private UUID ticketId;
        private User user;
        private Board board;
        private Ticket ticket;

        @BeforeEach
        void setUp() {
            boardId = UUID.randomUUID();
            ticketId = UUID.randomUUID();
            user = User.builder().build();
            user.setId(UUID.randomUUID());
            board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);
            ticket = Ticket.builder().title("T").key("B-1").type(TicketType.FEATURE)
                    .status(TicketStatus.TODO).board(board).createdBy(user).build();
            ticket.setId(ticketId);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER)).thenReturn(board);
            when(ticketValidator.ensureTicketExists(boardId, ticketId)).thenReturn(ticket);
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        }

        private TicketUpdateRequest milestoneRequest(UUID milestoneId, Boolean clearMilestone) {
            return new TicketUpdateRequest(
                    null, null, null, null, null, null, milestoneId, clearMilestone, null, null, null,
                    null, null, null, null, null, null, null, null);
        }

        @Test
        void updateTicket_milestoneId_resolvesOnMemberBoardsAndChecksRelation() {
            // Arrange
            UUID milestoneId = UUID.randomUUID();
            Milestone milestone = Milestone.builder().name("M1").board(board).build();
            milestone.setId(milestoneId);
            List<UUID> memberBoardIds = List.of(boardId);

            when(boardMemberQueryService.getMemberBoardIds(user.getId(), organizationId)).thenReturn(memberBoardIds);
            when(milestoneValidator.ensureMilestoneExistsOnBoards(milestoneId, memberBoardIds)).thenReturn(milestone);

            // Act
            ticketServiceImpl.updateTicket(user, organizationId, boardId, ticketId, milestoneRequest(milestoneId, null));

            // Assert
            verify(milestoneValidator).ensureMilestoneLinkableToBoard(milestone, board);
            assertThat(ticket.getMilestone()).isEqualTo(milestone);
        }

        @Test
        void updateTicket_clearMilestone_winsOverMilestoneId() {
            // Arrange
            Milestone existing = Milestone.builder().name("M0").board(board).build();
            existing.setId(UUID.randomUUID());
            ticket.setMilestone(existing);

            // Act
            ticketServiceImpl.updateTicket(user, organizationId, boardId, ticketId, milestoneRequest(UUID.randomUUID(), true));

            // Assert
            assertThat(ticket.getMilestone()).isNull();
            verify(milestoneValidator, never()).ensureMilestoneExistsOnBoards(any(), any());
        }

        @Test
        void updateTicket_noMilestoneFields_keepsMilestone() {
            // Arrange
            Milestone existing = Milestone.builder().name("M0").board(board).build();
            existing.setId(UUID.randomUUID());
            ticket.setMilestone(existing);

            // Act
            ticketServiceImpl.updateTicket(user, organizationId, boardId, ticketId, milestoneRequest(null, null));

            // Assert
            assertThat(ticket.getMilestone()).isEqualTo(existing);
        }
    }

    @Nested
    class ParentAssignment {

        private UUID boardId;
        private UUID ticketId;
        private User user;
        private Board board;
        private Sprint sprint;
        private Ticket parentTicket;
        private Ticket ticket;

        @BeforeEach
        void setUp() {
            boardId = UUID.randomUUID();
            ticketId = UUID.randomUUID();
            user = User.builder().build();
            user.setId(UUID.randomUUID());
            board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);
            sprint = Sprint.builder().name("Sprint").build();
            sprint.setId(UUID.randomUUID());
            parentTicket = Ticket.builder().title("P").key("B-1").type(TicketType.FEATURE)
                    .status(TicketStatus.IN_PROGRESS).board(board).sprint(sprint).createdBy(user).build();
            parentTicket.setId(UUID.randomUUID());
            ticket = Ticket.builder().title("S").key("B-2").type(TicketType.FEATURE)
                    .status(TicketStatus.IN_PROGRESS).board(board).sprint(sprint)
                    .parentTicket(parentTicket).createdBy(user).build();
            ticket.setId(ticketId);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER)).thenReturn(board);
            when(ticketValidator.ensureTicketExists(boardId, ticketId)).thenReturn(ticket);
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        }

        private TicketUpdateRequest parentRequest(UUID parentTicketId, Boolean clearParent) {
            return new TicketUpdateRequest(
                    null, null, null, null, null, null, null, null, parentTicketId, clearParent,
                    null, null, null, null, null, null, null, null, null);
        }

        // Stands in for TicketHelper.unparent, whose placement rule TicketHelperUnitTest covers.
        private void unparentInto(Sprint targetSprint, Backlog targetBacklog) {
            doAnswer(invocation -> {
                ticket.setParentTicket(null);
                ticket.setSprint(targetSprint);
                ticket.setBacklog(targetBacklog);
                return null;
            }).when(ticketHelper).unparent(ticket);
        }

        @Test
        void updateTicket_clearParent_unparentsAndIgnoresParentTicketId() {
            // Arrange: the flag wins over a parentTicketId sent in the same request.
            UUID otherParentId = UUID.randomUUID();
            unparentInto(sprint, null);

            // Act
            ticketServiceImpl.updateTicket(user, organizationId, boardId, ticketId, parentRequest(otherParentId, true));

            // Assert
            verify(ticketHelper).unparent(ticket);
            verify(ticketHelper, never()).assignParent(any(Ticket.class), any(Ticket.class));
            verify(ticketValidator, never()).ensureTicketExists(boardId, otherParentId);
            verify(ticketRepository).save(ticket);
        }

        @Test
        void updateTicket_omittedParentTicketId_keepsParent() {
            // Act: a partial update that sends neither parent field.
            ticketServiceImpl.updateTicket(user, organizationId, boardId, ticketId, parentRequest(null, null));

            // Assert
            assertThat(ticket.getParentTicket()).isEqualTo(parentTicket);
            assertThat(ticket.getSprint()).isEqualTo(sprint);
            verify(ticketHelper, never()).unparent(any(Ticket.class));
            verify(ticketHelper, never()).assignParent(any(Ticket.class), any(Ticket.class));
            verify(ticketRepository).save(ticket);
        }

        @Test
        void updateTicket_clearParentIntoSprint_keepsStatus() {
            // Arrange
            unparentInto(sprint, null);

            // Act
            ticketServiceImpl.updateTicket(user, organizationId, boardId, ticketId, parentRequest(null, true));

            // Assert
            assertThat(ticket.getParentTicket()).isNull();
            assertThat(ticket.getSprint()).isEqualTo(sprint);
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
            verify(ticketRepository).save(ticket);
        }

        @Test
        void updateTicket_clearParentIntoBacklog_resetsStatusToTodo() {
            // A backlog ticket is always TODO, so the progress recorded on a backlog sub-ticket
            // does not survive it becoming a top-level backlog ticket.

            // Arrange
            Backlog backlog = Backlog.builder().board(board).build();
            backlog.setId(UUID.randomUUID());
            unparentInto(null, backlog);

            // Act
            ticketServiceImpl.updateTicket(user, organizationId, boardId, ticketId, parentRequest(null, true));

            // Assert
            assertThat(ticket.getParentTicket()).isNull();
            assertThat(ticket.getBacklog()).isEqualTo(backlog);
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.TODO);
            verify(ticketRepository).save(ticket);
        }

        @Test
        void updateTicket_newParentTicketId_assignsParentWithItsPlacement() {
            // Arrange
            Ticket newParent = Ticket.builder().title("N").key("B-3").type(TicketType.FEATURE)
                    .status(TicketStatus.TODO).board(board).sprint(sprint).createdBy(user).build();
            newParent.setId(UUID.randomUUID());
            when(ticketValidator.ensureTicketExists(boardId, newParent.getId())).thenReturn(newParent);

            // Act
            ticketServiceImpl.updateTicket(user, organizationId, boardId, ticketId, parentRequest(newParent.getId(), null));

            // Assert
            verify(ticketHelper).assignParent(ticket, newParent);
            verify(ticketHelper, never()).unparent(any(Ticket.class));
            verify(ticketRepository).save(ticket);
        }

        @Test
        void createTicket_subTicketWithRequestedBacklog_takesParentSprintOnly() {
            // A sub-ticket is placed where its parent is, so a backlog sent with the request is
            // dropped instead of leaving the new ticket in a sprint and a backlog at once.

            // Arrange: the new ticket's parent is the sprint ticket from setUp.
            UUID requestedBacklogId = UUID.randomUUID();
            Ticket mapped = Ticket.builder().title("N").type(TicketType.FEATURE)
                    .priority(TicketPriority.MEDIUM).status(TicketStatus.IN_PROGRESS).build();
            TicketCreateRequest request = new TicketCreateRequest(
                    "N", null, TicketType.FEATURE, TicketPriority.MEDIUM, TicketStatus.IN_PROGRESS,
                    null, null, null, null, null,
                    null, requestedBacklogId, ticketId, null, null,
                    null, null, null);
            when(ticketMapper.toEntity(request)).thenReturn(mapped);
            when(sprintValidator.ensureSprintExistsInOrganization(organizationId, sprint.getId())).thenReturn(sprint);

            // Act
            ticketServiceImpl.createTicket(user, organizationId, boardId, request);

            // Assert
            assertThat(mapped.getParentTicket()).isEqualTo(ticket);
            assertThat(mapped.getSprint()).isEqualTo(sprint);
            assertThat(mapped.getBacklog()).isNull();
            assertThat(mapped.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
            verify(ticketValidator).ensureTicketHasCorrectPlacement(sprint.getId(), null);
            verify(backlogValidator, never()).ensureBacklogExistsByBoard(any(UUID.class), any(UUID.class));
        }
    }

    @Nested
    class OrganizationScopedReferences {

        private User user;
        private Board board;
        private UUID boardId;
        private UUID ticketId;
        private Ticket ticket;
        private UUID backlogId;

        @BeforeEach
        void setUpFixtures() {
            boardId = UUID.randomUUID();
            ticketId = UUID.randomUUID();
            backlogId = UUID.randomUUID();
            user = User.builder().build();
            user.setId(UUID.randomUUID());
            board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);
            board.setOrganizationId(organizationId);
            ticket = Ticket.builder().title("T").key("B-1").type(TicketType.FEATURE)
                    .status(TicketStatus.TODO).board(board).createdBy(user).build();
            ticket.setId(ticketId);
            lenient().when(boardAccessService.verifyBoardAccess(
                    organizationId, boardId, user.getId(), BoardMemberRole.MEMBER)).thenReturn(board);
        }

        private TicketCreateRequest createRequest(UUID sprintId, UUID projectId,
                                                  List<UUID> assigneeIds, List<UUID> reviewerIds,
                                                  List<UUID> labelIds) {
            return new TicketCreateRequest(
                    "T", null, TicketType.FEATURE, TicketPriority.MEDIUM, TicketStatus.TODO,
                    null, null, null, null, null,
                    sprintId, sprintId == null ? backlogId : null, null, projectId, null,
                    assigneeIds, reviewerIds, labelIds);
        }

        private TicketUpdateRequest updateRequest(UUID projectId, List<UUID> assigneeIds, List<UUID> reviewerIds,
                                                  List<UUID> labelIds) {
            return new TicketUpdateRequest(
                    null, null, null, null, null, projectId, null, null, null, null, null, null, null, null, null,
                    null, assigneeIds, reviewerIds, labelIds);
        }

        private void givenMappedTicketAndBacklog(TicketCreateRequest request) {
            Ticket mapped = Ticket.builder().title("T").type(TicketType.FEATURE).priority(TicketPriority.MEDIUM)
                    .status(TicketStatus.TODO).build();
            when(ticketMapper.toEntity(request)).thenReturn(mapped);
            if (request.sprintId() == null) {
                Backlog backlog = Backlog.builder().board(board).build();
                backlog.setId(backlogId);
                lenient().when(backlogValidator.ensureBacklogExistsByBoard(backlogId, boardId)).thenReturn(backlog);
            }
            lenient().when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        }

        private NotFoundException notFound(ExceptionDomainKazanError error) {
            return new NotFoundException(error);
        }

        private ConflictException notOrganizationMember() {
            return new ConflictException(ExceptionDomainKazanError.STA_BOARD_MEMBER_NOT_IN_ORGANIZATION);
        }

        private ValidationException withoutKazan() {
            return new ValidationException(ExceptionDomainAccessError.VAL_MEMBER_WITHOUT_APP_ACCESS);
        }

        @Test
        void createTicket_sprintOfAnotherOrganization_throwsNotFoundAndSavesNothing() {
            // Arrange
            UUID sprintId = UUID.randomUUID();
            TicketCreateRequest request = createRequest(sprintId, null, null, null, null);
            givenMappedTicketAndBacklog(request);
            when(sprintValidator.ensureSprintExistsInOrganization(organizationId, sprintId))
                    .thenThrow(notFound(ExceptionDomainKazanError.NOT_SPRINT_FOUND));

            // Act & Assert
            assertThatThrownBy(() -> ticketServiceImpl.createTicket(user, organizationId, boardId, request))
                    .isInstanceOf(NotFoundException.class);
            verify(sprintService, never()).linkSprintToBoardIfNeeded(any(Sprint.class), any(Board.class));
            verify(ticketRepository, never()).save(any(Ticket.class));
        }

        @Test
        void moveTicket_sprintOfAnotherOrganization_throwsNotFoundAndMovesNothing() {
            // Arrange
            UUID sprintId = UUID.randomUUID();
            when(ticketValidator.ensureTicketExists(boardId, ticketId)).thenReturn(ticket);
            when(sprintValidator.ensureSprintExistsInOrganization(organizationId, sprintId))
                    .thenThrow(notFound(ExceptionDomainKazanError.NOT_SPRINT_FOUND));

            // Act & Assert
            assertThatThrownBy(() -> ticketServiceImpl.moveTicket(user, organizationId, boardId, ticketId,
                    new TicketMoveRequest(sprintId, null, null, null, null)))
                    .isInstanceOf(NotFoundException.class);
            verify(ticketHelper, never()).moveTickets(any(), any(), any(), any(), any(), any(), any(), any());
            verify(sprintService, never()).linkSprintToBoardIfNeeded(any(Sprint.class), any(Board.class));
        }

        @Test
        void createTicket_projectOnBoardOfAnotherOrganization_throwsNotFoundProject() {
            // Arrange
            UUID projectId = UUID.randomUUID();
            TicketCreateRequest request = createRequest(null, projectId, null, null, null);
            givenMappedTicketAndBacklog(request);
            when(projectValidator.ensureProjectInOrganization(organizationId, projectId))
                    .thenThrow(notFound(ExceptionDomainKazanError.NOT_PROJECT_FOUND));

            // Act & Assert
            assertThatThrownBy(() -> ticketServiceImpl.createTicket(user, organizationId, boardId, request))
                    .isInstanceOf(NotFoundException.class);
            verify(ticketRepository, never()).save(any(Ticket.class));
        }

        @Test
        void createTicket_labelOnBoardOfAnotherOrganization_throwsNotFoundLabel() {
            // Arrange
            UUID labelId = UUID.randomUUID();
            TicketCreateRequest request = createRequest(null, null, null, null, List.of(labelId));
            givenMappedTicketAndBacklog(request);
            when(labelValidator.ensureLabelInOrganization(organizationId, labelId))
                    .thenThrow(notFound(ExceptionDomainKazanError.NOT_LABEL_FOUND));

            // Act & Assert
            assertThatThrownBy(() -> ticketServiceImpl.createTicket(user, organizationId, boardId, request))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void createTicket_projectAndLabelOfAnotherBoardInSameOrganization_areAccepted() {
            // Arrange
            UUID projectId = UUID.randomUUID();
            UUID labelId = UUID.randomUUID();
            Project project = Project.builder().build();
            project.setId(projectId);
            Label label = Label.builder().name("Bug").build();
            label.setId(labelId);
            TicketCreateRequest request = createRequest(null, projectId, null, null, List.of(labelId));
            givenMappedTicketAndBacklog(request);
            when(projectValidator.ensureProjectInOrganization(organizationId, projectId)).thenReturn(project);
            when(labelValidator.ensureLabelInOrganization(organizationId, labelId)).thenReturn(label);

            // Act
            ticketServiceImpl.createTicket(user, organizationId, boardId, request);

            // Assert
            ArgumentCaptor<Ticket> saved = ArgumentCaptor.forClass(Ticket.class);
            verify(ticketRepository, atLeastOnce()).save(saved.capture());
            assertThat(saved.getValue().getProject()).isEqualTo(project);
            assertThat(saved.getValue().getTicketLabels()).hasSize(1);
        }

        @Test
        void createTicket_assigneeNotActiveMember_throwsConflictAndSavesNothing() {
            // Arrange
            UUID assigneeId = UUID.randomUUID();
            TicketCreateRequest request = createRequest(null, null, List.of(assigneeId), null, null);
            givenMappedTicketAndBacklog(request);
            doThrow(notOrganizationMember()).when(boardValidator).ensureUserIsOrganizationMember(organizationId, assigneeId);

            // Act & Assert
            ConflictException exception = assertThrows(ConflictException.class,
                    () -> ticketServiceImpl.createTicket(user, organizationId, boardId, request));
            assertThat(exception.getErrorCode())
                    .isEqualTo(ExceptionDomainKazanError.STA_BOARD_MEMBER_NOT_IN_ORGANIZATION.getCode());
            verify(userQueryService, never()).getUserEntityById(any(UUID.class));
        }

        @Test
        void createTicket_reviewerNotActiveMember_throwsConflict() {
            // Arrange
            UUID reviewerId = UUID.randomUUID();
            TicketCreateRequest request = createRequest(null, null, null, List.of(reviewerId), null);
            givenMappedTicketAndBacklog(request);
            doThrow(notOrganizationMember()).when(boardValidator).ensureUserIsOrganizationMember(organizationId, reviewerId);

            // Act & Assert
            assertThatThrownBy(() -> ticketServiceImpl.createTicket(user, organizationId, boardId, request))
                    .isInstanceOf(ConflictException.class);
            verify(userQueryService, never()).getUserEntityById(any(UUID.class));
        }

        @Test
        void createTicket_withAssigneesAndReviewers_addsThemBeforeTheSave() {
            // Arrange: the save is the persist that cascades to the children and sets their ids,
            // so a child added after it reaches the response without an id.
            UUID assigneeId = UUID.randomUUID();
            UUID reviewerId = UUID.randomUUID();
            TicketCreateRequest request = createRequest(null, null, List.of(assigneeId), List.of(reviewerId), null);
            givenMappedTicketAndBacklog(request);
            when(userQueryService.getUserEntityById(any(UUID.class))).thenReturn(User.builder().build());
            AtomicReference<List<TicketAssignee>> assigneesAtSave = new AtomicReference<>();
            AtomicReference<List<TicketReviewer>> reviewersAtSave = new AtomicReference<>();
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> {
                Ticket saved = invocation.getArgument(0);
                assigneesAtSave.set(List.copyOf(saved.getAssignees()));
                reviewersAtSave.set(List.copyOf(saved.getReviewers()));
                return saved;
            });

            // Act
            ticketServiceImpl.createTicket(user, organizationId, boardId, request);

            // Assert
            assertThat(assigneesAtSave.get()).hasSize(1);
            assertThat(reviewersAtSave.get()).hasSize(1);
        }

        @Test
        void updateTicket_projectOnBoardOfAnotherOrganization_throwsNotFoundProject() {
            // Arrange
            UUID projectId = UUID.randomUUID();
            when(ticketValidator.ensureTicketExists(boardId, ticketId)).thenReturn(ticket);
            when(projectValidator.ensureProjectInOrganization(organizationId, projectId))
                    .thenThrow(notFound(ExceptionDomainKazanError.NOT_PROJECT_FOUND));

            // Act & Assert
            assertThatThrownBy(() -> ticketServiceImpl.updateTicket(user, organizationId, boardId, ticketId,
                    updateRequest(projectId, null, null, null)))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void updateTicket_labelOnBoardOfAnotherOrganization_throwsNotFoundLabel() {
            // Arrange
            UUID labelId = UUID.randomUUID();
            when(ticketValidator.ensureTicketExists(boardId, ticketId)).thenReturn(ticket);
            when(ticketRepository.saveAndFlush(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(labelValidator.ensureLabelInOrganization(organizationId, labelId))
                    .thenThrow(notFound(ExceptionDomainKazanError.NOT_LABEL_FOUND));

            // Act & Assert
            assertThatThrownBy(() -> ticketServiceImpl.updateTicket(user, organizationId, boardId, ticketId,
                    updateRequest(null, null, null, List.of(labelId))))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void updateTicket_newlyAddedNonMemberAssignee_throwsConflict() {
            // Arrange
            UUID assigneeId = UUID.randomUUID();
            when(ticketValidator.ensureTicketExists(boardId, ticketId)).thenReturn(ticket);
            when(ticketRepository.saveAndFlush(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
            doThrow(notOrganizationMember()).when(boardValidator).ensureUserIsOrganizationMember(organizationId, assigneeId);

            // Act & Assert
            assertThatThrownBy(() -> ticketServiceImpl.updateTicket(user, organizationId, boardId, ticketId,
                    updateRequest(null, List.of(assigneeId), null, null)))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void updateTicket_newlyAddedNonMemberReviewer_throwsConflict() {
            // Arrange
            UUID reviewerId = UUID.randomUUID();
            when(ticketValidator.ensureTicketExists(boardId, ticketId)).thenReturn(ticket);
            when(ticketRepository.saveAndFlush(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
            doThrow(notOrganizationMember()).when(boardValidator).ensureUserIsOrganizationMember(organizationId, reviewerId);

            // Act & Assert
            assertThatThrownBy(() -> ticketServiceImpl.updateTicket(user, organizationId, boardId, ticketId,
                    updateRequest(null, null, List.of(reviewerId), null)))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void updateTicket_assigneeAndReviewerAlreadyOnTicket_areKeptWithoutMembershipCheck() {
            // Arrange: both users have left the organization but stay on the ticket
            User departedAssignee = User.builder().build();
            departedAssignee.setId(UUID.randomUUID());
            User departedReviewer = User.builder().build();
            departedReviewer.setId(UUID.randomUUID());
            ticket.getAssignees().add(TicketAssignee.builder().ticket(ticket).user(departedAssignee).build());
            ticket.getReviewers().add(TicketReviewer.builder().ticket(ticket).user(departedReviewer).build());
            when(ticketValidator.ensureTicketExists(boardId, ticketId)).thenReturn(ticket);
            when(ticketRepository.saveAndFlush(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(userQueryService.getUserEntityById(departedAssignee.getId())).thenReturn(departedAssignee);
            when(userQueryService.getUserEntityById(departedReviewer.getId())).thenReturn(departedReviewer);

            // Act
            ticketServiceImpl.updateTicket(user, organizationId, boardId, ticketId, updateRequest(
                    null, List.of(departedAssignee.getId()), List.of(departedReviewer.getId()), null));

            // Assert
            verify(boardValidator, never()).ensureUserIsOrganizationMember(any(UUID.class), any(UUID.class));
            verify(appAccessValidator, times(2)).ensureUsersHaveAppAccess(organizationId, App.KAZAN, List.of());
            assertThat(ticket.getAssignees()).hasSize(1);
            assertThat(ticket.getReviewers()).hasSize(1);
        }

        @Test
        void createTicket_assigneeWithoutKazan_throwsMemberWithoutAppAccessAndAttachesNobody() {
            // Arrange
            UUID assigneeId = UUID.randomUUID();
            TicketCreateRequest request = createRequest(null, null, List.of(assigneeId), null, null);
            givenMappedTicketAndBacklog(request);
            doThrow(withoutKazan()).when(appAccessValidator)
                    .ensureUsersHaveAppAccess(organizationId, App.KAZAN, List.of(assigneeId));

            // Act & Assert
            assertThatThrownBy(() -> ticketServiceImpl.createTicket(user, organizationId, boardId, request))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo("APP_VAL_002"));
            verify(userQueryService, never()).getUserEntityById(any(UUID.class));
        }

        @Test
        void createTicket_reviewerWithoutKazan_throwsMemberWithoutAppAccess() {
            // Arrange
            UUID reviewerId = UUID.randomUUID();
            TicketCreateRequest request = createRequest(null, null, null, List.of(reviewerId), null);
            givenMappedTicketAndBacklog(request);
            doThrow(withoutKazan()).when(appAccessValidator)
                    .ensureUsersHaveAppAccess(organizationId, App.KAZAN, List.of(reviewerId));

            // Act & Assert
            assertThatThrownBy(() -> ticketServiceImpl.createTicket(user, organizationId, boardId, request))
                    .isInstanceOf(ValidationException.class);
            verify(userQueryService, never()).getUserEntityById(any(UUID.class));
        }

        @Test
        void updateTicket_onlyNewlyAddedUsersAreCheckedForKazan() {
            // Arrange: the kept assignee lost Kazan and stays; only the added users are checked
            User keptAssignee = User.builder().build();
            keptAssignee.setId(UUID.randomUUID());
            User addedAssignee = User.builder().build();
            addedAssignee.setId(UUID.randomUUID());
            User addedReviewer = User.builder().build();
            addedReviewer.setId(UUID.randomUUID());
            ticket.getAssignees().add(TicketAssignee.builder().ticket(ticket).user(keptAssignee).build());
            when(ticketValidator.ensureTicketExists(boardId, ticketId)).thenReturn(ticket);
            when(ticketRepository.saveAndFlush(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
            // The assignee check runs first with other arguments, so this stubbing is lenient.
            lenient().doThrow(withoutKazan()).when(appAccessValidator)
                    .ensureUsersHaveAppAccess(organizationId, App.KAZAN, List.of(addedReviewer.getId()));
            lenient().when(userQueryService.getUserEntityById(keptAssignee.getId())).thenReturn(keptAssignee);
            lenient().when(userQueryService.getUserEntityById(addedAssignee.getId())).thenReturn(addedAssignee);

            // Act & Assert
            assertThatThrownBy(() -> ticketServiceImpl.updateTicket(user, organizationId, boardId, ticketId,
                    updateRequest(null, List.of(keptAssignee.getId(), addedAssignee.getId()),
                            List.of(addedReviewer.getId()), null)))
                    .isInstanceOf(ValidationException.class);
            verify(appAccessValidator).ensureUsersHaveAppAccess(organizationId, App.KAZAN,
                    List.of(addedAssignee.getId()));
        }
    }

    @Nested
    class UpdateOrder {

        @Test
        void subTicketMovedBeforeSibling_shiftsRangeUpAndStoresNewOrder() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());
            Ticket subTicket = subTicket(boardId, 55);
            TicketOrderUpdateRequest request = new TicketOrderUpdateRequest(53);

            when(ticketValidator.ensureTicketExists(boardId, subTicket.getId())).thenReturn(subTicket);
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ticketServiceImpl.updateOrder(user, organizationId, boardId, subTicket.getId(), request);

            // Assert
            assertThat(subTicket.getDisplayOrder()).isEqualTo(53);
            verify(boardAccessService).verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER);
            verify(ticketRepository).shiftDisplayOrderUp(boardId, 53, 55, subTicket.getId());
            verify(ticketRepository, never()).shiftDisplayOrderDown(any(), anyInt(), anyInt(), any());
            verify(ticketRepository).save(subTicket);
            verify(ticketMapper).toDetailResponse(subTicket);
        }

        @Test
        void subTicketMovedAfterSibling_shiftsRangeDownAndStoresNewOrder() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());
            Ticket subTicket = subTicket(boardId, 53);
            TicketOrderUpdateRequest request = new TicketOrderUpdateRequest(55);

            when(ticketValidator.ensureTicketExists(boardId, subTicket.getId())).thenReturn(subTicket);
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ticketServiceImpl.updateOrder(user, organizationId, boardId, subTicket.getId(), request);

            // Assert
            assertThat(subTicket.getDisplayOrder()).isEqualTo(55);
            verify(ticketRepository).shiftDisplayOrderDown(boardId, 53, 55, subTicket.getId());
            verify(ticketRepository, never()).shiftDisplayOrderUp(any(), anyInt(), anyInt(), any());
            verify(ticketRepository).save(subTicket);
        }

        @Test
        void subTicketDroppedOnSameOrder_throwsValidationExceptionWithoutShifting() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());
            Ticket subTicket = subTicket(boardId, 54);
            TicketOrderUpdateRequest request = new TicketOrderUpdateRequest(54);

            when(ticketValidator.ensureTicketExists(boardId, subTicket.getId())).thenReturn(subTicket);
            doThrow(new ValidationException(ExceptionDomainKazanError.VAL_TICKET_ORDER, Map.of()))
                    .when(ticketValidator).ensureTicketOrderIsCorrect(54, 54);

            // Act + Assert
            assertThrows(ValidationException.class,
                    () -> ticketServiceImpl.updateOrder(user, organizationId, boardId, subTicket.getId(), request));
            assertThat(subTicket.getDisplayOrder()).isEqualTo(54);
            verify(ticketRepository, never()).shiftDisplayOrderUp(any(), anyInt(), anyInt(), any());
            verify(ticketRepository, never()).shiftDisplayOrderDown(any(), anyInt(), anyInt(), any());
            verify(ticketRepository, never()).save(any(Ticket.class));
        }

        private Ticket subTicket(UUID boardId, int displayOrder) {
            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Ticket parent = Ticket.builder()
                    .title("Parent").key("B-52").type(TicketType.FEATURE)
                    .board(board).displayOrder(52)
                    .build();
            parent.setId(UUID.randomUUID());

            Ticket subTicket = Ticket.builder()
                    .title("Sub").key("B-" + displayOrder).type(TicketType.FEATURE)
                    .board(board).parentTicket(parent).displayOrder(displayOrder)
                    .build();
            subTicket.setId(UUID.randomUUID());
            return subTicket;
        }
    }

}
