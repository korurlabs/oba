package com.korur.oba.apps.kazan.board.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.backlog.repository.BacklogRepository;
import com.korur.oba.apps.kazan.backlog.service.BacklogService;
import com.korur.oba.apps.kazan.board.dto.request.BoardAddChildrenRequest;
import com.korur.oba.apps.kazan.board.dto.request.BoardCreateRequest;
import com.korur.oba.apps.kazan.board.dto.request.BoardMemberAddRequest;
import com.korur.oba.apps.kazan.board.dto.request.BoardDeleteRequest;
import com.korur.oba.apps.kazan.board.dto.request.BoardUpdateRequest;
import com.korur.oba.apps.kazan.board.dto.response.BoardDetailResponse;
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.entity.BoardMember;
import com.korur.oba.apps.kazan.board.entity.BoardSprint;
import com.korur.oba.apps.kazan.board.enums.BoardMemberRole;
import com.korur.oba.apps.kazan.board.helper.BoardHelper;
import com.korur.oba.apps.kazan.board.mapper.BoardMapper;
import com.korur.oba.apps.kazan.board.mapper.BoardMemberMapper;
import com.korur.oba.apps.kazan.board.repository.BoardMemberRepository;
import com.korur.oba.apps.kazan.board.repository.BoardRepository;
import com.korur.oba.apps.kazan.board.service.BoardAccessService;
import com.korur.oba.apps.kazan.board.validator.BoardValidator;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.milestone.service.MilestoneService;
import com.korur.oba.apps.kazan.project.entity.Project;
import com.korur.oba.apps.kazan.project.repository.ProjectRepository;
import com.korur.oba.apps.kazan.sprint.entity.Sprint;
import com.korur.oba.apps.kazan.sprint.repository.SprintRepository;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.enums.TicketRelocationAction;
import com.korur.oba.apps.kazan.ticket.enums.TicketType;
import com.korur.oba.apps.kazan.ticket.helper.TicketHelper;
import com.korur.oba.apps.kazan.ticket.repository.TicketRepository;
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
class BoardServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private BoardMemberMapper boardMemberMapper;

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private BacklogService backlogService;

    @Mock
    private BoardAccessService boardAccessService;

    @Mock
    private BoardValidator boardValidator;

    @Mock
    private AppAccessValidator appAccessValidator;

    @Mock
    private BoardMapper boardMapper;

    @Mock
    private LoggingService loggingService;

    @Mock
    private BoardMemberRepository boardMemberRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private BoardHelper boardHelper;

    @Mock
    private TicketHelper ticketHelper;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private BacklogRepository backlogRepository;

    @Mock
    private SprintRepository sprintRepository;

    @Mock
    private MilestoneService milestoneService;

    @InjectMocks
    private BoardServiceImpl boardServiceImpl;

    @Nested
    class DeleteBoard {

        @Test
        void userNotOwner_throwsForbiddenException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.OWNER))
                    .thenThrow(new ForbiddenException(
                            com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError.SEC_INSUFFICIENT_BOARD_PERMISSIONS,
                            java.util.Map.of()));

            // Act & Assert
            BoardDeleteRequest request = new BoardDeleteRequest(null, null, null, null, null);
            assertThrows(ForbiddenException.class, () ->
                    boardServiceImpl.deleteBoard(user, organizationId, boardId, request));
        }

        @Test
        void noChildrenNoTickets_deletesSuccessfully() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").build();
            board.setId(boardId);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.OWNER)).thenReturn(board);
            when(boardRepository.findByParentBoard_Id(boardId)).thenReturn(Collections.emptyList());
            when(ticketRepository.findByBoard_Id(boardId)).thenReturn(Collections.emptyList());
            when(projectRepository.findByBoard_Id(boardId)).thenReturn(Collections.emptyList());

            // Act
            BoardDeleteRequest request = new BoardDeleteRequest(null, null, null, null, null);
            boardServiceImpl.deleteBoard(user, organizationId, boardId, request);

            // Assert
            verify(milestoneService).deleteBoardMilestones(boardId);
            verify(boardRepository).delete(board);
            verify(projectRepository, never()).deleteAll(any());
        }

        @Test
        void withProjects_deletesProjectsBeforeBoard() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").build();
            board.setId(boardId);

            Project project = new Project();

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.OWNER)).thenReturn(board);
            when(boardRepository.findByParentBoard_Id(boardId)).thenReturn(Collections.emptyList());
            when(ticketRepository.findByBoard_Id(boardId)).thenReturn(Collections.emptyList());
            when(projectRepository.findByBoard_Id(boardId)).thenReturn(List.of(project));

            // Act
            BoardDeleteRequest request = new BoardDeleteRequest(null, null, null, null, null);
            boardServiceImpl.deleteBoard(user, organizationId, boardId, request);

            // Assert
            verify(projectRepository).deleteAll(List.of(project));
            verify(boardRepository).delete(board);
        }

        @Test
        void withTickets_deleteAction_delegatesToRelocateTickets() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").build();
            board.setId(boardId);

            com.korur.oba.apps.kazan.ticket.entity.Ticket ticket =
                    com.korur.oba.apps.kazan.ticket.entity.Ticket.builder()
                            .title("T").key("B-1")
                            .type(com.korur.oba.apps.kazan.ticket.enums.TicketType.FEATURE)
                            .board(board).createdBy(user).build();
            ticket.setId(UUID.randomUUID());
            List<com.korur.oba.apps.kazan.ticket.entity.Ticket> tickets = List.of(ticket);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.OWNER)).thenReturn(board);
            when(boardRepository.findByParentBoard_Id(boardId)).thenReturn(Collections.emptyList());
            when(ticketRepository.findByBoard_Id(boardId)).thenReturn(tickets);
            when(projectRepository.findByBoard_Id(boardId)).thenReturn(Collections.emptyList());

            // Act
            BoardDeleteRequest request = new BoardDeleteRequest(
                    TicketRelocationAction.DELETE, null, null, null, null);
            boardServiceImpl.deleteBoard(user, organizationId, boardId, request);

            // Assert — ticket handling delegated to relocateTickets.
            verify(ticketHelper).relocateTickets(
                    eq(tickets),
                    eq(TicketRelocationAction.DELETE),
                    eq(null),
                    eq(null),
                    eq(null),
                    eq(boardId));
        }

        @Test
        void withTickets_moveToSprint_targetSprintNotOnTargetBoard_throwsValidationException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID targetBoardId = UUID.randomUUID();
            UUID targetSprintId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").build();
            board.setId(boardId);

            Board targetBoard = Board.builder().organizationId(organizationId).name("Target").prefix("T").build();
            targetBoard.setId(targetBoardId);

            // Sprint linked to a DIFFERENT board, not the target board.
            Board otherBoard = Board.builder().organizationId(organizationId).name("Other").prefix("O").build();
            otherBoard.setId(UUID.randomUUID());

            Sprint targetSprint = Sprint.builder().name("Sprint 2").build();
            targetSprint.setId(targetSprintId);
            BoardSprint boardSprint = BoardSprint.builder().board(otherBoard).sprint(targetSprint).build();
            boardSprint.setId(UUID.randomUUID());
            targetSprint.getBoardSprints().add(boardSprint);

            Ticket ticket = Ticket.builder().title("T").key("B-1").type(TicketType.FEATURE).board(board).createdBy(user).build();
            ticket.setId(UUID.randomUUID());

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.OWNER)).thenReturn(board);
            when(boardRepository.findByParentBoard_Id(boardId)).thenReturn(Collections.emptyList());
            when(ticketRepository.findByBoard_Id(boardId)).thenReturn(List.of(ticket));
            when(boardAccessService.verifyBoardAccess(organizationId, targetBoardId, user.getId(), BoardMemberRole.MEMBER))
                    .thenReturn(targetBoard);
            when(sprintRepository.findById(targetSprintId)).thenReturn(Optional.of(targetSprint));

            BoardDeleteRequest request = new BoardDeleteRequest(
                    TicketRelocationAction.MOVE_TO_SPRINT, targetBoardId, targetSprintId, null, null);

            // Act & Assert
            assertThrows(ValidationException.class, () ->
                    boardServiceImpl.deleteBoard(user, organizationId, boardId, request));
        }

        @Test
        void withTickets_moveToSprint_targetSprintOnTargetBoard_succeeds() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID targetBoardId = UUID.randomUUID();
            UUID targetSprintId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").build();
            board.setId(boardId);

            Board targetBoard = Board.builder().organizationId(organizationId).name("Target").prefix("T").build();
            targetBoard.setId(targetBoardId);

            Sprint targetSprint = Sprint.builder().name("Sprint 2").build();
            targetSprint.setId(targetSprintId);
            // Link sprint to the target board so validation passes.
            BoardSprint boardSprint = BoardSprint.builder().board(targetBoard).sprint(targetSprint).build();
            boardSprint.setId(UUID.randomUUID());
            targetSprint.getBoardSprints().add(boardSprint);

            Ticket ticket = Ticket.builder().title("T").key("B-1").type(TicketType.FEATURE).board(board).createdBy(user).build();
            ticket.setId(UUID.randomUUID());
            List<Ticket> tickets = List.of(ticket);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.OWNER)).thenReturn(board);
            when(boardRepository.findByParentBoard_Id(boardId)).thenReturn(Collections.emptyList());
            when(ticketRepository.findByBoard_Id(boardId)).thenReturn(tickets);
            when(boardAccessService.verifyBoardAccess(organizationId, targetBoardId, user.getId(), BoardMemberRole.MEMBER))
                    .thenReturn(targetBoard);
            when(sprintRepository.findById(targetSprintId)).thenReturn(Optional.of(targetSprint));
            when(projectRepository.findByBoard_Id(boardId)).thenReturn(Collections.emptyList());

            BoardDeleteRequest request = new BoardDeleteRequest(
                    TicketRelocationAction.MOVE_TO_SPRINT, targetBoardId, targetSprintId, null, null);

            // Act
            boardServiceImpl.deleteBoard(user, organizationId, boardId, request);

            // Assert
            verify(ticketHelper).relocateTickets(
                    eq(tickets),
                    eq(TicketRelocationAction.MOVE_TO_SPRINT),
                    eq(targetBoard),
                    eq(targetSprint),
                    eq(null),
                    eq(boardId));
            verify(boardRepository).delete(board);
        }
    }

    @Nested
    class RestoreBoard {

        @Test
        void boardNotFound_throwsNotFoundException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            when(boardRepository.findByIdIncludingDeleted(boardId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(NotFoundException.class, () ->
                    boardServiceImpl.restoreBoard(user, organizationId, boardId));
        }

        @Test
        void boardNotDeleted_throwsValidationException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").build();
            board.setId(boardId);
            // board.isDeleted() returns false by default

            when(boardRepository.findByIdIncludingDeleted(boardId)).thenReturn(Optional.of(board));

            // Act & Assert
            assertThrows(ValidationException.class, () ->
                    boardServiceImpl.restoreBoard(user, organizationId, boardId));
        }

        @Test
        void memberNotFound_throwsForbiddenException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").build();
            board.setId(boardId);
            board.setDeleted(true);

            when(boardRepository.findByIdIncludingDeleted(boardId)).thenReturn(Optional.of(board));
            when(boardMemberRepository.findByBoardIdAndUserIdIncludingDeleted(boardId, user.getId()))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    boardServiceImpl.restoreBoard(user, organizationId, boardId));
        }

        @Test
        void memberNotOwner_throwsForbiddenException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").build();
            board.setId(boardId);
            board.setDeleted(true);

            BoardMember member = BoardMember.builder()
                    .board(board)
                    .user(user)
                    .role(BoardMemberRole.MEMBER)
                    .build();
            member.setId(UUID.randomUUID());

            when(boardRepository.findByIdIncludingDeleted(boardId)).thenReturn(Optional.of(board));
            when(boardMemberRepository.findByBoardIdAndUserIdIncludingDeleted(boardId, user.getId()))
                    .thenReturn(Optional.of(member));

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    boardServiceImpl.restoreBoard(user, organizationId, boardId));
        }

        @Test
        void ownerRestoresDeletedBoard_callsRestoreAndBoardHelper() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").build();
            board.setId(boardId);
            board.setDeleted(true);

            BoardMember member = BoardMember.builder()
                    .board(board)
                    .user(user)
                    .role(BoardMemberRole.OWNER)
                    .build();
            member.setId(UUID.randomUUID());

            when(boardRepository.findByIdIncludingDeleted(boardId)).thenReturn(Optional.of(board));
            when(boardMemberRepository.findByBoardIdAndUserIdIncludingDeleted(boardId, user.getId()))
                    .thenReturn(Optional.of(member));
            doNothing().when(boardHelper).restoreWithFreeSlug(board);
            doNothing().when(boardHelper).restoreBoardChildren(boardId);

            // Act
            boardServiceImpl.restoreBoard(user, organizationId, boardId);

            // Assert
            verify(boardHelper).restoreWithFreeSlug(board);
            verify(boardHelper).restoreBoardChildren(boardId);
        }
    }

    @Nested
    class UpdateBoard {

        private final UUID boardId = UUID.randomUUID();
        private final UUID oldParentId = UUID.randomUUID();
        private final UUID newParentId = UUID.randomUUID();

        private User user;
        private Board board;
        private Board oldParent;
        private Board newParent;

        @BeforeEach
        void setUp() {
            user = User.builder().build();
            user.setId(UUID.randomUUID());

            oldParent = Board.builder().organizationId(organizationId).name("Old parent").prefix("OP").build();
            oldParent.setId(oldParentId);

            newParent = Board.builder().organizationId(organizationId).name("New parent").prefix("NP").build();
            newParent.setId(newParentId);

            board = Board.builder().organizationId(organizationId).name("Board").prefix("B").parentBoard(oldParent).build();
            board.setId(boardId);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN))
                    .thenReturn(board);
        }

        private BoardUpdateRequest parentRequest(UUID parentBoardId, Boolean clearParent) {
            return new BoardUpdateRequest("Renamed", null, null, null, null, parentBoardId, clearParent);
        }

        private void stubSaveAndResponse(UUID expectedParentId) {
            when(boardHelper.saveWithSlug(board)).thenReturn(board);
            when(boardMapper.toDetailResponse(board)).thenReturn(new BoardDetailResponse(
                    boardId, null, null, "Renamed", null, "B", "renamed", null, null, expectedParentId));
        }

        @Test
        void parentBoardId_setsNewParentAndReturnsIt() {
            // Arrange
            when(boardAccessService.verifyBoardAccess(organizationId, newParentId, user.getId(), BoardMemberRole.ADMIN))
                    .thenReturn(newParent);
            stubSaveAndResponse(newParentId);

            // Act
            BoardDetailResponse result = boardServiceImpl.updateBoard(
                    user, organizationId, boardId, parentRequest(newParentId, null));

            // Assert
            assertSame(newParent, board.getParentBoard());
            assertEquals(newParentId, result.parentBoardId());
            verify(boardValidator).ensureNotDescendantForReparent(boardId, newParentId);
            verify(boardMapper).updateEntity(eq(board), any(BoardUpdateRequest.class));
        }

        @Test
        void clearParent_makesBoardTopLevel() {
            // Arrange
            stubSaveAndResponse(null);

            // Act
            BoardDetailResponse result = boardServiceImpl.updateBoard(
                    user, organizationId, boardId, parentRequest(null, Boolean.TRUE));

            // Assert
            assertNull(board.getParentBoard());
            assertNull(result.parentBoardId());
            verify(boardHelper).saveWithSlug(board);
        }

        @Test
        void clearParentWithParentBoardId_clearParentWinsWithoutParentLookup() {
            // Arrange
            stubSaveAndResponse(null);

            // Act
            boardServiceImpl.updateBoard(user, organizationId, boardId, parentRequest(newParentId, Boolean.TRUE));

            // Assert
            assertNull(board.getParentBoard());
            verify(boardAccessService, never())
                    .verifyBoardAccess(eq(organizationId), eq(newParentId), any(), any());
            verify(boardValidator, never()).ensureNotDescendantForReparent(any(), any());
        }

        @Test
        void clearParentOnTopLevelBoard_isNoOp() {
            // Arrange
            board.setParentBoard(null);
            stubSaveAndResponse(null);

            // Act
            boardServiceImpl.updateBoard(user, organizationId, boardId, parentRequest(null, Boolean.TRUE));

            // Assert
            assertNull(board.getParentBoard());
            verify(boardHelper).saveWithSlug(board);
        }

        @Test
        void noParentFields_keepsCurrentParent() {
            // Arrange
            stubSaveAndResponse(oldParentId);

            // Act
            boardServiceImpl.updateBoard(user, organizationId, boardId, parentRequest(null, null));

            // Assert
            assertSame(oldParent, board.getParentBoard());
            verify(boardValidator, never()).ensureNotDescendantForReparent(any(), any());
        }

        @Test
        void clearParentFalse_keepsCurrentParent() {
            // Arrange
            stubSaveAndResponse(oldParentId);

            // Act
            boardServiceImpl.updateBoard(user, organizationId, boardId, parentRequest(null, Boolean.FALSE));

            // Assert
            assertSame(oldParent, board.getParentBoard());
        }

        @Test
        void sameParent_isNoOpWithoutAccessLookup() {
            // Arrange
            stubSaveAndResponse(oldParentId);

            // Act
            boardServiceImpl.updateBoard(user, organizationId, boardId, parentRequest(oldParentId, null));

            // Assert
            assertSame(oldParent, board.getParentBoard());
            verify(boardAccessService, never())
                    .verifyBoardAccess(eq(organizationId), eq(oldParentId), any(), any());
            verify(boardValidator, never()).ensureNotDescendantForReparent(any(), any());
        }

        @Test
        void selfAsParent_throwsCircularParentAndSavesNothing() {
            // Act
            ValidationException exception = assertThrows(ValidationException.class, () ->
                    boardServiceImpl.updateBoard(user, organizationId, boardId, parentRequest(boardId, null)));

            // Assert
            assertEquals(ExceptionDomainKazanError.VAL_BOARD_CIRCULAR_PARENT.getCode(),
                    exception.getErrorCode());
            assertSame(oldParent, board.getParentBoard());
            verify(boardMapper, never()).updateEntity(any(), any());
            verify(boardHelper, never()).saveWithSlug(any());
        }

        @Test
        void descendantAsParent_throwsCircularParentAndSavesNothing() {
            // Arrange
            when(boardAccessService.verifyBoardAccess(organizationId, newParentId, user.getId(), BoardMemberRole.ADMIN))
                    .thenReturn(newParent);
            doThrow(new ValidationException(
                    ExceptionDomainKazanError.VAL_BOARD_CIRCULAR_PARENT, Map.of()))
                    .when(boardValidator).ensureNotDescendantForReparent(boardId, newParentId);

            // Act
            ValidationException exception = assertThrows(ValidationException.class, () ->
                    boardServiceImpl.updateBoard(user, organizationId, boardId, parentRequest(newParentId, null)));

            // Assert
            assertEquals(ExceptionDomainKazanError.VAL_BOARD_CIRCULAR_PARENT.getCode(),
                    exception.getErrorCode());
            assertSame(oldParent, board.getParentBoard());
            verify(boardMapper, never()).updateEntity(any(), any());
            verify(boardHelper, never()).saveWithSlug(any());
        }

        @Test
        void memberOnNewParent_throwsForbiddenAndSavesNothing() {
            // Arrange
            when(boardAccessService.verifyBoardAccess(organizationId, newParentId, user.getId(), BoardMemberRole.ADMIN))
                    .thenThrow(new ForbiddenException(
                            ExceptionDomainKazanError.SEC_INSUFFICIENT_BOARD_PERMISSIONS, Map.of()));

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    boardServiceImpl.updateBoard(user, organizationId, boardId, parentRequest(newParentId, null)));
            assertSame(oldParent, board.getParentBoard());
            verify(boardMapper, never()).updateEntity(any(), any());
            verify(boardHelper, never()).saveWithSlug(any());
        }

        @Test
        void missingParent_throwsNotFoundAndSavesNothing() {
            // Arrange
            when(boardAccessService.verifyBoardAccess(organizationId, newParentId, user.getId(), BoardMemberRole.ADMIN))
                    .thenThrow(new NotFoundException(ExceptionDomainKazanError.NOT_BOARD_FOUND, Map.of()));

            // Act & Assert
            assertThrows(NotFoundException.class, () ->
                    boardServiceImpl.updateBoard(user, organizationId, boardId, parentRequest(newParentId, null)));
            assertSame(oldParent, board.getParentBoard());
            verify(boardHelper, never()).saveWithSlug(any());
        }
    }

    @Nested
    class AddChildBoards {

        @Test
        void validRequest_setsParentAndReturnsMappedResponses() {
            // Arrange
            UUID parentBoardId = UUID.randomUUID();
            UUID childBoardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board parentBoard = Board.builder().organizationId(organizationId).name("Parent").prefix("P").build();
            parentBoard.setId(parentBoardId);

            Board childBoard = Board.builder().organizationId(organizationId).name("Child").prefix("C").build();
            childBoard.setId(childBoardId);

            BoardDetailResponse detailResponse = new BoardDetailResponse(
                    childBoardId, null, null, "Child", null, "C", "child", null, null, parentBoardId);

            when(boardAccessService.verifyBoardAccess(organizationId, parentBoardId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(parentBoard);
            when(boardAccessService.verifyBoardAccess(organizationId, childBoardId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(childBoard);
            doNothing().when(boardValidator).ensureNotAlreadyChild(childBoard, parentBoardId);
            doNothing().when(boardValidator).ensureNotDescendantForReparent(childBoardId, parentBoardId);
            when(boardRepository.saveAll(any())).thenReturn(List.of(childBoard));
            when(boardMapper.toDetailResponse(childBoard)).thenReturn(detailResponse);

            BoardAddChildrenRequest request = new BoardAddChildrenRequest(List.of(childBoardId));

            // Act
            List<BoardDetailResponse> result = boardServiceImpl.addChildBoards(user, organizationId, parentBoardId, request);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(childBoardId, result.get(0).id());
            verify(boardRepository).saveAll(any());
            verify(boardMapper).toDetailResponse(childBoard);
        }

        @Test
        void selfReference_throwsValidationException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board parentBoard = Board.builder().organizationId(organizationId).name("Board").prefix("B").build();
            parentBoard.setId(boardId);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(parentBoard);

            BoardAddChildrenRequest request = new BoardAddChildrenRequest(List.of(boardId));

            // Act & Assert
            assertThrows(ValidationException.class, () ->
                    boardServiceImpl.addChildBoards(user, organizationId, boardId, request));
        }

        @Test
        void userLacksManageOnParent_throwsForbiddenException() {
            // Arrange
            UUID parentBoardId = UUID.randomUUID();
            UUID childBoardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            when(boardAccessService.verifyBoardAccess(organizationId, parentBoardId, user.getId(), BoardMemberRole.ADMIN))
                    .thenThrow(new ForbiddenException(
                            com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError.SEC_INSUFFICIENT_BOARD_PERMISSIONS,
                            java.util.Map.of()));

            BoardAddChildrenRequest request = new BoardAddChildrenRequest(List.of(childBoardId));

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    boardServiceImpl.addChildBoards(user, organizationId, parentBoardId, request));
        }

        @Test
        void userLacksManageOnChild_throwsForbiddenException() {
            // Arrange
            UUID parentBoardId = UUID.randomUUID();
            UUID childBoardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board parentBoard = Board.builder().organizationId(organizationId).name("Parent").prefix("P").build();
            parentBoard.setId(parentBoardId);

            Board childBoard = Board.builder().organizationId(organizationId).name("Child").prefix("C").build();
            childBoard.setId(childBoardId);

            when(boardAccessService.verifyBoardAccess(organizationId, parentBoardId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(parentBoard);
            when(boardAccessService.verifyBoardAccess(organizationId, childBoardId, user.getId(), BoardMemberRole.ADMIN))
                    .thenThrow(new ForbiddenException(
                            com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError.SEC_INSUFFICIENT_BOARD_PERMISSIONS,
                            java.util.Map.of()));

            BoardAddChildrenRequest request = new BoardAddChildrenRequest(List.of(childBoardId));

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    boardServiceImpl.addChildBoards(user, organizationId, parentBoardId, request));
        }

        @Test
        void multipleChildren_setsParentOnAll() {
            // Arrange
            UUID parentBoardId = UUID.randomUUID();
            UUID childBoardId1 = UUID.randomUUID();
            UUID childBoardId2 = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board parentBoard = Board.builder().organizationId(organizationId).name("Parent").prefix("P").build();
            parentBoard.setId(parentBoardId);

            Board childBoard1 = Board.builder().organizationId(organizationId).name("Child1").prefix("C1").build();
            childBoard1.setId(childBoardId1);

            Board childBoard2 = Board.builder().organizationId(organizationId).name("Child2").prefix("C2").build();
            childBoard2.setId(childBoardId2);

            BoardDetailResponse response1 = new BoardDetailResponse(
                    childBoardId1, null, null, "Child1", null, "C1", "child1", null, null, parentBoardId);
            BoardDetailResponse response2 = new BoardDetailResponse(
                    childBoardId2, null, null, "Child2", null, "C2", "child2", null, null, parentBoardId);

            when(boardAccessService.verifyBoardAccess(organizationId, parentBoardId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(parentBoard);
            when(boardAccessService.verifyBoardAccess(organizationId, childBoardId1, user.getId(), BoardMemberRole.ADMIN)).thenReturn(childBoard1);
            when(boardAccessService.verifyBoardAccess(organizationId, childBoardId2, user.getId(), BoardMemberRole.ADMIN)).thenReturn(childBoard2);
            doNothing().when(boardValidator).ensureNotAlreadyChild(any(), eq(parentBoardId));
            doNothing().when(boardValidator).ensureNotDescendantForReparent(any(), eq(parentBoardId));
            when(boardRepository.saveAll(any())).thenReturn(List.of(childBoard1, childBoard2));
            when(boardMapper.toDetailResponse(childBoard1)).thenReturn(response1);
            when(boardMapper.toDetailResponse(childBoard2)).thenReturn(response2);

            BoardAddChildrenRequest request = new BoardAddChildrenRequest(
                    List.of(childBoardId1, childBoardId2));

            // Act
            List<BoardDetailResponse> result = boardServiceImpl.addChildBoards(user, organizationId, parentBoardId, request);

            // Assert
            assertEquals(2, result.size());
            verify(boardRepository).saveAll(any());
        }

        @Test
        void parentBoardNotFound_throwsNotFoundException() {
            // Arrange
            UUID parentBoardId = UUID.randomUUID();
            UUID childBoardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            when(boardAccessService.verifyBoardAccess(organizationId, parentBoardId, user.getId(), BoardMemberRole.ADMIN))
                    .thenThrow(new NotFoundException(
                            com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError.NOT_BOARD_FOUND,
                            java.util.Map.of()));

            BoardAddChildrenRequest request = new BoardAddChildrenRequest(List.of(childBoardId));

            // Act & Assert
            assertThrows(NotFoundException.class, () ->
                    boardServiceImpl.addChildBoards(user, organizationId, parentBoardId, request));
        }

        @Test
        void childAlreadyUnderParent_throwsConflictException() {
            // Arrange
            UUID parentBoardId = UUID.randomUUID();
            UUID childBoardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board parentBoard = Board.builder().organizationId(organizationId).name("Parent").prefix("P").build();
            parentBoard.setId(parentBoardId);

            Board childBoard = Board.builder().organizationId(organizationId).name("Child").prefix("C").parentBoard(parentBoard).build();
            childBoard.setId(childBoardId);

            when(boardAccessService.verifyBoardAccess(organizationId, parentBoardId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(parentBoard);
            when(boardAccessService.verifyBoardAccess(organizationId, childBoardId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(childBoard);
            doThrow(new ConflictException(ExceptionDomainKazanError.STA_BOARD_ALREADY_CHILD, Map.of()))
                    .when(boardValidator).ensureNotAlreadyChild(childBoard, parentBoardId);

            BoardAddChildrenRequest request = new BoardAddChildrenRequest(List.of(childBoardId));

            // Act
            ConflictException exception = assertThrows(ConflictException.class, () ->
                    boardServiceImpl.addChildBoards(user, organizationId, parentBoardId, request));

            // Assert
            assertEquals(ExceptionDomainKazanError.STA_BOARD_ALREADY_CHILD.getCode(), exception.getErrorCode());
            verify(boardRepository, never()).saveAll(any());
        }

        @Test
        void parentIsDescendantOfChild_throwsCircularParent() {
            // Arrange
            UUID parentBoardId = UUID.randomUUID();
            UUID childBoardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board parentBoard = Board.builder().organizationId(organizationId).name("Parent").prefix("P").build();
            parentBoard.setId(parentBoardId);

            Board childBoard = Board.builder().organizationId(organizationId).name("Child").prefix("C").build();
            childBoard.setId(childBoardId);

            when(boardAccessService.verifyBoardAccess(organizationId, parentBoardId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(parentBoard);
            when(boardAccessService.verifyBoardAccess(organizationId, childBoardId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(childBoard);
            doThrow(new ValidationException(ExceptionDomainKazanError.VAL_BOARD_CIRCULAR_PARENT, Map.of()))
                    .when(boardValidator).ensureNotDescendantForReparent(childBoardId, parentBoardId);

            BoardAddChildrenRequest request = new BoardAddChildrenRequest(List.of(childBoardId));

            // Act
            ValidationException exception = assertThrows(ValidationException.class, () ->
                    boardServiceImpl.addChildBoards(user, organizationId, parentBoardId, request));

            // Assert
            assertEquals(ExceptionDomainKazanError.VAL_BOARD_CIRCULAR_PARENT.getCode(), exception.getErrorCode());
            assertNull(childBoard.getParentBoard());
            verify(boardRepository, never()).saveAll(any());
        }
    }

    @Nested
    class OrganizationIsolation {

        private User user;

        @BeforeEach
        void setUp() {
            user = User.builder().build();
            user.setId(UUID.randomUUID());
        }

        private NotFoundException boardNotFound() {
            return new NotFoundException(ExceptionDomainKazanError.NOT_BOARD_FOUND, Map.of());
        }

        @Test
        void createBoard_validRequest_setsHeaderOrganizationOnTheBoard() {
            // Arrange
            BoardCreateRequest request = new BoardCreateRequest("Board", null, "B", null, null, null, null);
            Board entity = Board.builder().name("Board").prefix("B").build();
            when(boardMapper.toEntity(request)).thenReturn(entity);
            when(boardHelper.nextFreeSlug("Board", null)).thenReturn("board");
            when(boardHelper.saveWithSlug(entity)).thenReturn(entity);

            // Act
            boardServiceImpl.createBoard(user, organizationId, request);

            // Assert
            assertEquals(organizationId, entity.getOrganizationId());
        }

        @Test
        void createBoard_parentOfAnotherOrganization_throwsBoardNotFoundAndSavesNothing() {
            // Arrange
            UUID parentId = UUID.randomUUID();
            BoardCreateRequest request = new BoardCreateRequest("Board", null, "B", null, null, parentId, null);
            Board entity = Board.builder().name("Board").prefix("B").build();
            when(boardMapper.toEntity(request)).thenReturn(entity);
            when(boardHelper.nextFreeSlug("Board", null)).thenReturn("board");
            when(boardAccessService.verifyBoardAccess(organizationId, parentId, user.getId(), BoardMemberRole.ADMIN))
                    .thenThrow(boardNotFound());

            // Act & Assert
            NotFoundException exception = assertThrows(NotFoundException.class, () ->
                    boardServiceImpl.createBoard(user, organizationId, request));
            assertEquals("KAZ_NOT_001", exception.getErrorCode());
            verify(boardHelper, never()).saveWithSlug(any());
        }

        @Test
        void createBoard_initialMemberNotInOrganization_throwsNotInOrganizationAndSavesNothing() {
            // Arrange
            UUID outsiderId = UUID.randomUUID();
            BoardCreateRequest request = new BoardCreateRequest("Board", null, "B", null, null, null,
                    List.of(new BoardMemberAddRequest(outsiderId, BoardMemberRole.MEMBER)));
            Board entity = Board.builder().name("Board").prefix("B").build();
            when(boardMapper.toEntity(request)).thenReturn(entity);
            when(boardHelper.nextFreeSlug("Board", null)).thenReturn("board");
            doThrow(new ConflictException(ExceptionDomainKazanError.STA_BOARD_MEMBER_NOT_IN_ORGANIZATION))
                    .when(boardValidator).ensureUserIsOrganizationMember(organizationId, outsiderId);

            // Act & Assert
            ConflictException exception = assertThrows(ConflictException.class, () ->
                    boardServiceImpl.createBoard(user, organizationId, request));
            assertEquals("KAZ_STA_019", exception.getErrorCode());
            verify(boardHelper, never()).saveWithSlug(any());
        }

        @Test
        void createBoard_initialMemberWithoutKazan_throwsMemberWithoutAppAccessAndSavesNothing() {
            // Arrange
            UUID colleagueId = UUID.randomUUID();
            BoardCreateRequest request = new BoardCreateRequest("Board", null, "B", null, null, null,
                    List.of(new BoardMemberAddRequest(user.getId(), BoardMemberRole.OWNER),
                            new BoardMemberAddRequest(colleagueId, BoardMemberRole.MEMBER)));
            Board entity = Board.builder().name("Board").prefix("B").build();
            when(boardMapper.toEntity(request)).thenReturn(entity);
            when(boardHelper.nextFreeSlug("Board", null)).thenReturn("board");
            when(boardMemberMapper.toEntity(any(BoardMemberAddRequest.class)))
                    .thenReturn(BoardMember.builder().role(BoardMemberRole.MEMBER).build());
            doThrow(new ValidationException(ExceptionDomainAccessError.VAL_MEMBER_WITHOUT_APP_ACCESS))
                    .when(appAccessValidator).ensureUsersHaveAppAccess(organizationId, App.KAZAN, List.of(colleagueId));

            // Act & Assert
            ValidationException exception = assertThrows(ValidationException.class, () ->
                    boardServiceImpl.createBoard(user, organizationId, request));
            assertEquals("APP_VAL_002", exception.getErrorCode());
            verify(boardHelper, never()).saveWithSlug(any());
        }

        @Test
        void updateBoard_newParentOfAnotherOrganization_throwsBoardNotFoundAndSavesNothing() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID foreignParentId = UUID.randomUUID();
            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").build();
            board.setId(boardId);
            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN))
                    .thenReturn(board);
            when(boardAccessService.verifyBoardAccess(organizationId, foreignParentId, user.getId(),
                    BoardMemberRole.ADMIN)).thenThrow(boardNotFound());
            BoardUpdateRequest request = new BoardUpdateRequest("Renamed", null, null, null, null,
                    foreignParentId, null);

            // Act & Assert
            assertThrows(NotFoundException.class, () ->
                    boardServiceImpl.updateBoard(user, organizationId, boardId, request));
            verify(boardHelper, never()).saveWithSlug(any());
            verify(boardMapper, never()).updateEntity(any(), any());
        }

        @Test
        void addChildBoards_childOfAnotherOrganization_throwsBoardNotFoundAndSavesNothing() {
            // Arrange
            UUID parentId = UUID.randomUUID();
            UUID foreignChildId = UUID.randomUUID();
            Board parent = Board.builder().organizationId(organizationId).name("Parent").prefix("P").build();
            parent.setId(parentId);
            when(boardAccessService.verifyBoardAccess(organizationId, parentId, user.getId(), BoardMemberRole.ADMIN))
                    .thenReturn(parent);
            when(boardAccessService.verifyBoardAccess(organizationId, foreignChildId, user.getId(),
                    BoardMemberRole.ADMIN)).thenThrow(boardNotFound());

            // Act & Assert
            assertThrows(NotFoundException.class, () -> boardServiceImpl.addChildBoards(user, organizationId,
                    parentId, new BoardAddChildrenRequest(List.of(foreignChildId))));
            verify(boardRepository, never()).saveAll(any());
        }

        @Test
        void addChildBoards_parentOfAnotherOrganization_throwsBoardNotFound() {
            // Arrange
            UUID foreignParentId = UUID.randomUUID();
            when(boardAccessService.verifyBoardAccess(organizationId, foreignParentId, user.getId(),
                    BoardMemberRole.ADMIN)).thenThrow(boardNotFound());

            // Act & Assert
            assertThrows(NotFoundException.class, () -> boardServiceImpl.addChildBoards(user, organizationId,
                    foreignParentId, new BoardAddChildrenRequest(List.of(UUID.randomUUID()))));
            verify(boardRepository, never()).saveAll(any());
        }

        @Test
        void deleteBoard_moveChildrenToParentOfAnotherOrganization_throwsBoardNotFoundAndDeletesNothing() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID foreignParentId = UUID.randomUUID();
            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").build();
            board.setId(boardId);
            Board child = Board.builder().organizationId(organizationId).name("Child").prefix("C").build();
            child.setId(UUID.randomUUID());
            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.OWNER))
                    .thenReturn(board);
            when(boardRepository.findByParentBoard_Id(boardId)).thenReturn(List.of(child));
            when(boardAccessService.verifyBoardAccess(organizationId, foreignParentId, user.getId(),
                    BoardMemberRole.ADMIN)).thenThrow(boardNotFound());
            BoardDeleteRequest request = new BoardDeleteRequest(null, null, null,
                    com.korur.oba.apps.kazan.board.enums.BoardChildBoardAction.MOVE, foreignParentId);

            // Act & Assert
            assertThrows(NotFoundException.class, () ->
                    boardServiceImpl.deleteBoard(user, organizationId, boardId, request));
            verify(boardRepository, never()).delete(any(Board.class));
            verify(boardRepository, never()).save(any());
        }

        @Test
        void deleteBoard_ticketTargetOfAnotherOrganization_throwsBoardNotFoundAndMovesNothing() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID foreignTargetId = UUID.randomUUID();
            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").build();
            board.setId(boardId);
            Ticket ticket = Ticket.builder().title("T").key("B-1").type(TicketType.FEATURE).board(board)
                    .createdBy(user).build();
            ticket.setId(UUID.randomUUID());
            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.OWNER))
                    .thenReturn(board);
            when(boardRepository.findByParentBoard_Id(boardId)).thenReturn(Collections.emptyList());
            when(ticketRepository.findByBoard_Id(boardId)).thenReturn(List.of(ticket));
            when(boardAccessService.verifyBoardAccess(organizationId, foreignTargetId, user.getId(),
                    BoardMemberRole.MEMBER)).thenThrow(boardNotFound());
            BoardDeleteRequest request = new BoardDeleteRequest(
                    TicketRelocationAction.MOVE_TO_BACKLOG, foreignTargetId, null, null, null);

            // Act & Assert
            assertThrows(NotFoundException.class, () ->
                    boardServiceImpl.deleteBoard(user, organizationId, boardId, request));
            verify(ticketHelper, never()).relocateTickets(any(), any(), any(), any(), any(), any());
            verify(boardRepository, never()).delete(any(Board.class));
        }

        @Test
        void restoreBoard_boardOfAnotherOrganization_throwsBoardNotFoundBeforeMembershipLookup() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            Board foreign = Board.builder().organizationId(UUID.randomUUID()).name("Foreign").prefix("F").build();
            foreign.setId(boardId);
            foreign.setDeleted(true);
            when(boardRepository.findByIdIncludingDeleted(boardId)).thenReturn(Optional.of(foreign));

            // Act & Assert
            NotFoundException exception = assertThrows(NotFoundException.class, () ->
                    boardServiceImpl.restoreBoard(user, organizationId, boardId));
            assertEquals("KAZ_NOT_001", exception.getErrorCode());
            verify(boardMemberRepository, never()).findByBoardIdAndUserIdIncludingDeleted(any(), any());
            verify(boardHelper, never()).restoreWithFreeSlug(any());
        }
    }
}
