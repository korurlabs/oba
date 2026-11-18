package com.korur.oba.apps.kazan.board.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.board.constant.BoardConstant;
import com.korur.oba.apps.kazan.board.constant.BoardExceptionLogConstant;
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.entity.BoardMember;
import com.korur.oba.apps.kazan.board.repository.BoardMemberRepository;
import com.korur.oba.apps.kazan.board.repository.BoardRepository;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.ticket.enums.TicketRelocationAction;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

/**
 * Validator for board and board membership lookups.
 */
@Component
@RequiredArgsConstructor
public class BoardValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(BoardValidator.class);

    private final BoardRepository boardRepository;
    private final BoardMemberRepository boardMemberRepository;
    private final OrganizationMemberQueryService organizationMemberQueryService;
    private final LoggingService loggingService;

    /**
     * Ensures the board exists and returns it.
     */
    public Board ensureBoardExists(UUID boardId) {
        // Fetch the board or raise a domain-specific not-found error.
        return boardRepository.findById(boardId)
                .orElseThrow(() -> {
                    // Log for observability before throwing the exception.
                    loggingService.error(
                            LOG_PREFIX,
                            LogLayer.VALIDATOR,
                            BoardExceptionLogConstant.BOARD_NOT_FOUND,
                            boardId);
                    // Propagate a NotFoundException with field context.
                    return new NotFoundException(
                            ExceptionDomainKazanError.NOT_BOARD_FOUND,
                            Map.of(BoardConstant.FIELD_BOARD_ID, boardId));
                });
    }

    /**
     * Ensures the board exists and belongs to the organization the caller acts in, and
     * returns it. A board of another organization answers the same not found as a missing
     * one, so its id reveals nothing to another tenant. Callers run this before any
     * membership or child lookup for the same reason.
     *
     * @param organizationId the organization the caller acts in
     * @param boardId        the board identifier
     * @return the board
     * @throws NotFoundException {@link ExceptionDomainKazanError#NOT_BOARD_FOUND} if the board
     *         is missing or belongs to another organization
     */
    public Board ensureBoardInOrganization(UUID organizationId, UUID boardId) {
        Board board = ensureBoardExists(boardId);
        if (!board.getOrganizationId().equals(organizationId)) {
            loggingService.warn(
                    LOG_PREFIX,
                    LogLayer.VALIDATOR,
                    BoardExceptionLogConstant.BOARD_OF_OTHER_ORGANIZATION,
                    boardId,
                    organizationId);
            throw new NotFoundException(
                    ExceptionDomainKazanError.NOT_BOARD_FOUND,
                    Map.of(BoardConstant.FIELD_BOARD_ID, boardId));
        }
        return board;
    }

    /**
     * Ensures the user is a member of the board and returns the membership.
     */
    public BoardMember ensureBoardMemberExists(UUID boardId, UUID userId) {
        return boardMemberRepository.findByBoard_IdAndUser_Id(boardId, userId)
                .orElseThrow(() -> new NotFoundException(
                        ExceptionDomainKazanError.NOT_BOARD_MEMBER_FOUND,
                        Map.of(
                                BoardConstant.FIELD_BOARD_ID, boardId,
                                BoardConstant.FIELD_USER_ID, userId)));
    }

    /**
     * Ensures a user about to be added to a board is an ACTIVE member of the board's
     * organization, so a board never gains a member from outside its tenant. An unknown
     * user gets the same answer, so the check reveals nothing about other organizations.
     *
     * @param organizationId the organization that owns the board
     * @param userId         the user to add
     * @throws ConflictException STA_BOARD_MEMBER_NOT_IN_ORGANIZATION when the user has no
     *         ACTIVE membership of the organization
     */
    public void ensureUserIsOrganizationMember(UUID organizationId, UUID userId) {
        boolean activeMember = organizationMemberQueryService.findMember(organizationId, userId)
                .map((OrganizationMember member) -> member.getStatus() == OrganizationMemberStatus.ACTIVE)
                .orElse(false);
        if (!activeMember) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    BoardExceptionLogConstant.BOARD_MEMBER_NOT_IN_ORGANIZATION, organizationId, userId);
            throw new ConflictException(
                    ExceptionDomainKazanError.STA_BOARD_MEMBER_NOT_IN_ORGANIZATION,
                    Map.of(BoardConstant.FIELD_ORGANIZATION_ID, organizationId,
                            BoardConstant.FIELD_USER_ID, userId));
        }
    }

    /**
     * Ensures no other board uses the given prefix (for create).
     */
    public void ensurePrefixIsUnique(String prefix) {
        if (boardRepository.existsByPrefix(prefix)) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    BoardExceptionLogConstant.BOARD_PREFIX_DUPLICATE, prefix);
            throw new ConflictException(
                    ExceptionDomainKazanError.STA_BOARD_PREFIX_DUPLICATE,
                    Map.of(BoardConstant.FIELD_PREFIX, prefix));
        }
    }

    /**
     * Ensures no other board uses the given prefix, excluding the board being
     * updated.
     */
    public void ensurePrefixIsUniqueForUpdate(String prefix, UUID boardId) {
        if (boardRepository.existsByPrefixAndIdNot(prefix, boardId)) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    BoardExceptionLogConstant.BOARD_PREFIX_DUPLICATE, prefix);
            throw new ConflictException(
                    ExceptionDomainKazanError.STA_BOARD_PREFIX_DUPLICATE,
                    Map.of(BoardConstant.FIELD_PREFIX, prefix));
        }
    }

    /**
     * Ensures the candidate parent board is not a descendant of the given board.
     * Prevents circular parent references during board deletion with MOVE action.
     *
     * @param boardId           the board being deleted
     * @param candidateParentId the proposed new parent for child boards
     */
    public void ensureNotDescendant(UUID boardId, UUID candidateParentId) {
        Set<UUID> visited = new HashSet<>();
        collectDescendantIds(boardId, visited);
        if (visited.contains(candidateParentId)) {
            throw new ValidationException(
                    ExceptionDomainKazanError.VAL_BOARD_DELETE_CIRCULAR_PARENT,
                    Map.of(BoardConstant.FIELD_BOARD_ID, boardId,
                            BoardConstant.FIELD_NEW_PARENT_BOARD_ID, candidateParentId));
        }
    }

    /**
     * Ensures the board is not already a child of the specified parent.
     *
     * @param board         the candidate child board
     * @param parentBoardId the proposed parent board identifier
     * @throws ConflictException if the board already has the given parent
     */
    public void ensureNotAlreadyChild(Board board, UUID parentBoardId) {
        if (board.getParentBoard() != null
                && board.getParentBoard().getId().equals(parentBoardId)) {
            loggingService.error(
                    LOG_PREFIX,
                    LogLayer.VALIDATOR,
                    BoardExceptionLogConstant.BOARD_ALREADY_CHILD,
                    board.getId(),
                    parentBoardId);
            throw new ConflictException(
                    ExceptionDomainKazanError.STA_BOARD_ALREADY_CHILD,
                    Map.of(BoardConstant.FIELD_BOARD_ID, board.getId(),
                            BoardConstant.FIELD_PARENT_BOARD_ID, parentBoardId));
        }
    }

    /**
     * Ensures the candidate parent is not a descendant of the given board.
     * Prevents circular parent references when re-parenting boards.
     *
     * @param boardId           the board whose descendants are checked
     * @param candidateParentId the proposed parent board identifier
     * @throws ValidationException if the candidate parent is a descendant of the
     *                             board
     */
    public void ensureNotDescendantForReparent(UUID boardId, UUID candidateParentId) {
        Set<UUID> visited = new HashSet<>();
        collectDescendantIds(boardId, visited);
        if (visited.contains(candidateParentId)) {
            loggingService.error(
                    LOG_PREFIX,
                    LogLayer.VALIDATOR,
                    BoardExceptionLogConstant.BOARD_CIRCULAR_PARENT,
                    boardId,
                    candidateParentId);
            throw new ValidationException(
                    ExceptionDomainKazanError.VAL_BOARD_CIRCULAR_PARENT,
                    Map.of(BoardConstant.FIELD_BOARD_ID, boardId,
                            BoardConstant.FIELD_PARENT_BOARD_ID, candidateParentId));
        }
    }

    /**
     * Ensures a target board ID is provided when the ticket action is not DELETE.
     *
     * @param action        the ticket relocation action
     * @param targetBoardId the target board identifier (may be null)
     * @param sourceBoardId the source board identifier for error reporting
     * @throws ValidationException if the target board ID is null and the action is
     *                             not DELETE
     */
    public void ensureTargetBoardProvided(TicketRelocationAction action, UUID targetBoardId, UUID sourceBoardId) {
        if (action != TicketRelocationAction.DELETE && targetBoardId == null) {
            throw new ValidationException(
                    ExceptionDomainKazanError.VAL_BOARD_DELETE_TARGET_BOARD_REQUIRED,
                    Map.of(BoardConstant.FIELD_BOARD_ID, sourceBoardId));
        }
    }

    /**
     * Recursively collects all descendant board IDs starting from the given board.
     */
    private void collectDescendantIds(UUID boardId, Set<UUID> visited) {
        List<Board> children = boardRepository.findByParentBoard_Id(boardId);
        for (Board child : children) {
            if (visited.add(child.getId())) {
                collectDescendantIds(child.getId(), visited);
            }
        }
    }
}
