package com.korur.oba.apps.kazan.board.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.backlog.entity.Backlog;
import com.korur.oba.apps.kazan.backlog.repository.BacklogRepository;
import com.korur.oba.apps.kazan.backlog.service.BacklogService;
import com.korur.oba.apps.kazan.board.constant.BoardConstant;
import com.korur.oba.apps.kazan.board.constant.service.BoardServiceLogConstant;
import com.korur.oba.apps.kazan.board.dto.request.BoardAddChildrenRequest;
import com.korur.oba.apps.kazan.board.dto.request.BoardCreateRequest;
import com.korur.oba.apps.kazan.board.dto.request.BoardDeleteRequest;
import com.korur.oba.apps.kazan.board.dto.request.BoardMemberAddRequest;
import com.korur.oba.apps.kazan.board.dto.request.BoardUpdateRequest;
import com.korur.oba.apps.kazan.board.dto.response.BoardDetailResponse;
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.entity.BoardMember;
import com.korur.oba.apps.kazan.board.enums.BoardChildBoardAction;
import com.korur.oba.apps.kazan.board.enums.BoardMemberRole;
import com.korur.oba.apps.kazan.board.helper.BoardHelper;
import com.korur.oba.apps.kazan.board.mapper.BoardMapper;
import com.korur.oba.apps.kazan.board.mapper.BoardMemberMapper;
import com.korur.oba.apps.kazan.board.repository.BoardMemberRepository;
import com.korur.oba.apps.kazan.board.repository.BoardRepository;
import com.korur.oba.apps.kazan.board.service.BoardAccessService;
import com.korur.oba.apps.kazan.board.service.BoardService;
import com.korur.oba.apps.kazan.board.validator.BoardValidator;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.milestone.service.MilestoneService;
import com.korur.oba.apps.kazan.project.entity.Project;
import com.korur.oba.apps.kazan.project.repository.ProjectRepository;
import com.korur.oba.apps.kazan.sprint.constant.SprintConstant;
import com.korur.oba.apps.kazan.sprint.entity.Sprint;
import com.korur.oba.apps.kazan.sprint.repository.SprintRepository;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.enums.TicketRelocationAction;
import com.korur.oba.apps.kazan.ticket.helper.TicketHelper;
import com.korur.oba.apps.kazan.ticket.repository.TicketRepository;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.service.UserQueryService;

/**
 * Implementation of board write operations.
 * <p>
 * Manages board lifecycle and membership with role-based access control.
 */
@Service
@RequiredArgsConstructor
public class BoardServiceImpl implements BoardService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(BoardServiceImpl.class);

    private final BoardRepository boardRepository;
    private final BoardMemberMapper boardMemberMapper;
    private final UserQueryService userQueryService;
    private final BacklogService backlogService;
    private final BoardAccessService boardAccessService;
    private final BoardValidator boardValidator;
    private final AppAccessValidator appAccessValidator;
    private final BoardMapper boardMapper;
    private final LoggingService loggingService;
    private final BoardMemberRepository boardMemberRepository;
    private final ProjectRepository projectRepository;
    private final BoardHelper boardHelper;
    private final TicketHelper ticketHelper;
    private final TicketRepository ticketRepository;
    private final BacklogRepository backlogRepository;
    private final SprintRepository sprintRepository;
    private final MilestoneService milestoneService;

    @Override
    @Transactional
    public BoardDetailResponse createBoard(User user, UUID organizationId, BoardCreateRequest request) {
        boardValidator.ensurePrefixIsUnique(request.prefix());

        Board board = boardMapper.toEntity(request);
        // The board belongs to the request's organization, never to one named in a body.
        board.setOrganizationId(organizationId);
        board.setSlug(boardHelper.nextFreeSlug(board.getName(), null));

        // Resolve parent board.
        if (request.parentBoardId() != null) {
            Board parentBoard = boardAccessService.verifyBoardAccess(
                    organizationId, request.parentBoardId(), user.getId(), BoardMemberRole.ADMIN);
            board.setParentBoard(parentBoard);
        }

        // Assign the creating user as OWNER.
        BoardMember ownerMember = BoardMember.builder()
                .board(board)
                .user(user)
                .role(BoardMemberRole.OWNER)
                .build();
        board.getMembers().add(ownerMember);

        // Add any initial members provided at creation time (owner is skipped to avoid
        // duplicates).
        if (request.members() != null) {
            List<UUID> initialMemberIds = new ArrayList<>();
            for (BoardMemberAddRequest memberRequest : request.members()) {
                if (memberRequest.userId().equals(user.getId())) {
                    continue;
                }
                boardValidator.ensureUserIsOrganizationMember(organizationId, memberRequest.userId());
                initialMemberIds.add(memberRequest.userId());
                User targetUser = userQueryService.getUserEntityById(memberRequest.userId());
                BoardMember member = boardMemberMapper.toEntity(memberRequest);
                member.setBoard(board);
                member.setUser(targetUser);
                board.getMembers().add(member);
            }
            // Every initial member must be able to open Kazan in the organization, checked for all at
            // once before the board is stored.
            appAccessValidator.ensureUsersHaveAppAccess(organizationId, App.KAZAN, initialMemberIds);
        }

        board = boardHelper.saveWithSlug(board);

        // Create the default backlog for the board.
        backlogService.createDefaultBacklog(board);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                BoardServiceLogConstant.BOARD_CREATED, board.getId());

        return boardMapper.toDetailResponse(board);
    }

    @Override
    @Transactional
    public BoardDetailResponse updateBoard(User user, UUID organizationId, UUID boardId, BoardUpdateRequest request) {
        // Validate board exists and user has management permissions.
        Board board = boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN);

        if (request.prefix() != null) {
            boardValidator.ensurePrefixIsUniqueForUpdate(request.prefix(), boardId);
        }

        // The parent checks run before the mapper touches the board, so a refused parent change
        // leaves the other fields of the same request unsaved.
        applyParentChange(user, organizationId, board, request);

        // Apply partial update via mapper; a rename may move the board to a new slug.
        boardMapper.updateEntity(board, request);
        boardHelper.syncSlugWithName(board);
        board = boardHelper.saveWithSlug(board);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                BoardServiceLogConstant.BOARD_UPDATED, board.getId());

        return boardMapper.toDetailResponse(board);
    }

    @Override
    @Transactional
    public void deleteBoard(User user, UUID organizationId, UUID boardId, BoardDeleteRequest request) {
        // Validate board exists and user is the owner.
        Board board = boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.OWNER);

        // Step 1: Handle child boards.
        handleChildBoardsOnDelete(board, request, user, organizationId);

        // Step 2: Handle tickets via centralized relocation.
        List<Ticket> boardTickets = ticketRepository.findByBoard_Id(board.getId());
        if (!boardTickets.isEmpty()) {
            if (request.ticketAction() == null) {
                throw new ValidationException(
                        ExceptionDomainKazanError.VAL_BOARD_DELETE_TICKET_ACTION_REQUIRED,
                        Map.of(BoardConstant.FIELD_BOARD_ID, board.getId()));
            }

            Board targetBoard = null;
            Sprint targetSprint = null;
            Backlog targetBacklog = null;

            if (request.ticketAction() != TicketRelocationAction.DELETE) {
                boardValidator.ensureTargetBoardProvided(request.ticketAction(), request.targetBoardId(), board.getId());
                // Tickets may only move to a board of the same organization that the caller
                // contributes to.
                final Board resolvedTargetBoard = boardAccessService.verifyBoardAccess(
                        organizationId, request.targetBoardId(), user.getId(), BoardMemberRole.MEMBER);
                targetBoard = resolvedTargetBoard;

                switch (request.ticketAction()) {
                    case MOVE_TO_BACKLOG -> targetBacklog = backlogRepository.findByBoard_Id(resolvedTargetBoard.getId())
                            .orElseThrow(() -> new NotFoundException(
                                    ExceptionDomainKazanError.NOT_BACKLOG_FOUND,
                                    Map.of(BoardConstant.FIELD_TARGET_BOARD_ID, resolvedTargetBoard.getId())));
                    case MOVE_TO_SPRINT -> {
                        if (request.targetSprintId() == null) {
                            throw new ValidationException(
                                    ExceptionDomainKazanError.VAL_SPRINT_TARGET_SPRINT_REQUIRED,
                                    Map.of(BoardConstant.FIELD_BOARD_ID, board.getId()));
                        }
                        targetSprint = sprintRepository.findById(request.targetSprintId())
                                .orElseThrow(() -> new NotFoundException(
                                        ExceptionDomainKazanError.NOT_SPRINT_FOUND,
                                        Map.of(SprintConstant.FIELD_SPRINT_ID, request.targetSprintId())));
                        boolean belongsToTargetBoard = targetSprint.getBoardSprints().stream()
                                .anyMatch(bs -> bs.getBoard().getId().equals(resolvedTargetBoard.getId()));
                        if (!belongsToTargetBoard) {
                            throw new ValidationException(
                                    ExceptionDomainKazanError.VAL_BOARD_DELETE_TARGET_SPRINT_BOARD_MISMATCH,
                                    Map.of(SprintConstant.FIELD_SPRINT_ID, request.targetSprintId(),
                                            BoardConstant.FIELD_TARGET_BOARD_ID, resolvedTargetBoard.getId()));
                        }
                    }
                    default -> { /* DELETE handled by outer guard */ }
                }
            }

            ticketHelper.relocateTickets(
                    boardTickets, request.ticketAction(),
                    targetBoard, targetSprint, targetBacklog, board.getId());
        }

        // Step 4: Soft-delete all board projects explicitly.
        List<Project> projects = projectRepository.findByBoard_Id(boardId);
        if (!projects.isEmpty()) {
            projectRepository.deleteAll(projects);
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                    BoardServiceLogConstant.BOARD_DELETE_PROJECTS_DELETED, boardId);
        }

        // Step 5: Detach every ticket from the board's milestones and soft-delete them,
        // including tickets moved to another board in step 3.
        milestoneService.deleteBoardMilestones(boardId);

        // Step 6: Soft-delete the board (cascade handles backlog, labels, members+specialties, boardSprints).
        boardRepository.delete(board);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                BoardServiceLogConstant.BOARD_DELETED, boardId);
    }

    @Override
    @Transactional
    public void restoreBoard(User user, UUID organizationId, UUID boardId) {
        // Find the board including deleted records.
        Board board = boardRepository.findByIdIncludingDeleted(boardId)
                .orElseThrow(() -> new NotFoundException(
                        ExceptionDomainKazanError.NOT_BOARD_FOUND,
                        Map.of(BoardConstant.FIELD_BOARD_ID, boardId)));

        // A board of another organization reads as a missing one, before any other check.
        if (!board.getOrganizationId().equals(organizationId)) {
            throw new NotFoundException(
                    ExceptionDomainKazanError.NOT_BOARD_FOUND,
                    Map.of(BoardConstant.FIELD_BOARD_ID, boardId));
        }

        if (!board.isDeleted()) {
            throw new ValidationException(
                    ExceptionDomainKazanError.VAL_BOARD_NOT_DELETED,
                    Map.of(BoardConstant.FIELD_BOARD_ID, boardId));
        }

        // Verify the user was an OWNER of the board before it was deleted.
        // Normal membership queries filter out deleted records, so we use a
        // native query that includes soft-deleted board members.
        BoardMember member = boardMemberRepository.findByBoardIdAndUserIdIncludingDeleted(boardId, user.getId())
                .orElseThrow(() -> new ForbiddenException(
                        ExceptionDomainKazanError.SEC_INSUFFICIENT_BOARD_PERMISSIONS,
                        Map.of(BoardConstant.FIELD_BOARD_ID, boardId,
                                BoardConstant.FIELD_USER_ID, user.getId())));

        if (member.getRole() != BoardMemberRole.OWNER) {
            throw new ForbiddenException(
                    ExceptionDomainKazanError.SEC_INSUFFICIENT_BOARD_PERMISSIONS,
                    Map.of(BoardConstant.FIELD_BOARD_ID, boardId,
                            BoardConstant.FIELD_USER_ID, user.getId()));
        }

        // Restore the board itself, on a free slug if a live board took its slug meanwhile.
        boardHelper.restoreWithFreeSlug(board);

        // Restore all cascade-deleted children.
        boardHelper.restoreBoardChildren(boardId);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                BoardServiceLogConstant.BOARD_RESTORED, boardId);
    }

    @Override
    @Transactional
    public List<BoardDetailResponse> addChildBoards(User user, UUID organizationId, UUID boardId, BoardAddChildrenRequest request) {
        // Validate parent board exists and user has manage permissions.
        Board parentBoard = boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN);

        List<Board> updatedChildren = new ArrayList<>();

        for (UUID childBoardId : request.childBoardIds()) {
            // Validate child board exists and user has manage permissions.
            Board childBoard = boardAccessService.verifyBoardAccess(
                    organizationId, childBoardId, user.getId(), BoardMemberRole.ADMIN);

            // Linking a board that is already a child is a conflict here, unlike the board
            // update request, where the same parent again is a no-op.
            boardValidator.ensureNotAlreadyChild(childBoard, boardId);

            assignParent(childBoard, parentBoard);
            updatedChildren.add(childBoard);
        }

        // Save all updated children.
        List<Board> savedChildren = boardRepository.saveAll(updatedChildren);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                BoardServiceLogConstant.BOARD_CHILDREN_ADDED, boardId, request.childBoardIds());

        return savedChildren.stream()
                .map(boardMapper::toDetailResponse)
                .toList();
    }

    /**
     * Applies the parent fields of a board update request to a board the caller administers.
     * clearParent wins over parentBoardId so a client can never move and detach at once. A null
     * parentBoardId alone keeps the parent because partial updates omit it, and the current
     * parent again is a no-op. The old parent is not checked: a board's placement is a setting
     * of the board itself, and the caller may not be a member of the old parent.
     *
     * @param user           the caller, who must also administer a new parent
     * @param organizationId the organization of the request; a new parent must belong to it
     * @param board   the board being updated
     * @param request the update request carrying the parent fields
     */
    private void applyParentChange(User user, UUID organizationId, Board board, BoardUpdateRequest request) {
        if (Boolean.TRUE.equals(request.clearParent())) {
            if (board.getParentBoard() != null) {
                assignParent(board, null);
            }
            return;
        }

        UUID parentBoardId = request.parentBoardId();
        if (parentBoardId == null
                || (board.getParentBoard() != null && board.getParentBoard().getId().equals(parentBoardId))) {
            return;
        }

        Board newParent = boardAccessService.verifyBoardAccess(
                organizationId, parentBoardId, user.getId(), BoardMemberRole.ADMIN);
        assignParent(board, newParent);
    }

    /**
     * Places a board under a new parent, or at the top level when the parent is null, after
     * checking that the board would not become its own ancestor. The caller has already
     * verified ADMIN access on both boards. The board's children, tickets, sprints and members
     * move with it unchanged.
     *
     * @param board     the board to place
     * @param newParent the new parent board, or null for the top level
     * @throws ValidationException if the new parent is the board itself or one of its descendants
     */
    private void assignParent(Board board, Board newParent) {
        UUID newParentId = null;
        if (newParent != null) {
            newParentId = newParent.getId();
            if (newParentId.equals(board.getId())) {
                throw new ValidationException(
                        ExceptionDomainKazanError.VAL_BOARD_CIRCULAR_PARENT,
                        Map.of(BoardConstant.FIELD_BOARD_ID, board.getId(),
                                BoardConstant.FIELD_PARENT_BOARD_ID, newParentId));
            }
            boardValidator.ensureNotDescendantForReparent(board.getId(), newParentId);
        }

        board.setParentBoard(newParent);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                BoardServiceLogConstant.BOARD_PARENT_CHANGED, board.getId(), newParentId);
    }

    private void handleChildBoardsOnDelete(Board board, BoardDeleteRequest request, User user, UUID organizationId) {
        List<Board> childBoards = boardRepository.findByParentBoard_Id(board.getId());
        if (childBoards.isEmpty()) {
            return;
        }

        if (request.childBoardAction() == null) {
            throw new ValidationException(
                    ExceptionDomainKazanError.VAL_BOARD_DELETE_CHILD_ACTION_REQUIRED,
                    Map.of(BoardConstant.FIELD_BOARD_ID, board.getId()));
        }

        switch (request.childBoardAction()) {
            case PROMOTE -> {
                for (Board child : childBoards) {
                    child.setParentBoard(null);
                    boardRepository.save(child);
                }
                loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                        BoardServiceLogConstant.BOARD_DELETE_CHILD_BOARDS_PROMOTED, board.getId());
            }
            case MOVE -> {
                if (request.newParentBoardId() == null) {
                    throw new ValidationException(
                            ExceptionDomainKazanError.VAL_BOARD_DELETE_NEW_PARENT_REQUIRED,
                            Map.of(BoardConstant.FIELD_BOARD_ID, board.getId()));
                }
                Board newParent = boardAccessService.verifyBoardAccess(
                        organizationId, request.newParentBoardId(), user.getId(), BoardMemberRole.ADMIN);
                boardValidator.ensureNotDescendant(board.getId(), request.newParentBoardId());
                for (Board child : childBoards) {
                    child.setParentBoard(newParent);
                    boardRepository.save(child);
                }
                loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                        BoardServiceLogConstant.BOARD_DELETE_CHILD_BOARDS_MOVED, board.getId());
            }
            case DELETE -> {
                BoardDeleteRequest childRequest = new BoardDeleteRequest(
                        request.ticketAction(), request.targetBoardId(), request.targetSprintId(),
                        BoardChildBoardAction.DELETE, null);
                for (Board child : childBoards) {
                    deleteBoard(user, organizationId, child.getId(), childRequest);
                }
                loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                        BoardServiceLogConstant.BOARD_DELETE_CHILD_BOARDS_DELETED, board.getId());
            }
        }
    }
}
