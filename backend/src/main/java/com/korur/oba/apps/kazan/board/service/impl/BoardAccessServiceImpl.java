package com.korur.oba.apps.kazan.board.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.backlog.entity.Backlog;
import com.korur.oba.apps.kazan.backlog.validator.BacklogValidator;
import com.korur.oba.apps.kazan.board.constant.BoardConstant;
import com.korur.oba.apps.kazan.board.constant.BoardMemberRoleConstant;
import com.korur.oba.apps.kazan.board.constant.service.BoardAccessServiceLogConstant;
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.entity.BoardMember;
import com.korur.oba.apps.kazan.board.enums.BoardMemberRole;
import com.korur.oba.apps.kazan.board.service.BoardAccessService;
import com.korur.oba.apps.kazan.board.validator.BoardValidator;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.milestone.entity.Milestone;
import com.korur.oba.apps.kazan.milestone.validator.MilestoneValidator;
import com.korur.oba.apps.kazan.sprint.entity.Sprint;
import com.korur.oba.apps.kazan.sprint.validator.SprintValidator;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.validator.TicketValidator;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Centralised board access control implementation.
 * <p>
 * Validates entity existence via dedicated validators and enforces
 * role-based access on the board membership.
 */
@Service
@RequiredArgsConstructor
public class BoardAccessServiceImpl implements BoardAccessService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(BoardAccessServiceImpl.class);

    private final BoardValidator boardValidator;
    private final SprintValidator sprintValidator;
    private final TicketValidator ticketValidator;
    private final BacklogValidator backlogValidator;
    private final MilestoneValidator milestoneValidator;
    private final LoggingService loggingService;

    @Override
    public Board verifyBoardAccess(UUID organizationId, UUID boardId, UUID userId, BoardMemberRole minimumRole) {
        Board board = boardValidator.ensureBoardInOrganization(organizationId, boardId);
        enforceRole(boardId, userId, minimumRole);
        return board;
    }

    @Override
    public Sprint verifySprintAccess(UUID organizationId, UUID boardId, UUID sprintId, UUID userId, BoardMemberRole minimumRole) {
        boardValidator.ensureBoardInOrganization(organizationId, boardId);
        Sprint sprint = sprintValidator.ensureSprintExists(sprintId);

        // Verify the sprint is linked to the specified board.
        boolean belongsToBoard = sprint.getBoardSprints().stream()
                .anyMatch(bs -> bs.getBoard().getId().equals(boardId));
        if (!belongsToBoard) {
            throw new NotFoundException(
                    ExceptionDomainKazanError.NOT_SPRINT_FOUND,
                    Map.of(BoardConstant.FIELD_BOARD_ID, boardId));
        }

        enforceRole(boardId, userId, minimumRole);
        return sprint;
    }

    @Override
    public Ticket verifyTicketAccess(UUID organizationId, UUID boardId, UUID ticketId, UUID userId, BoardMemberRole minimumRole) {
        boardValidator.ensureBoardInOrganization(organizationId, boardId);
        Ticket ticket = ticketValidator.ensureTicketExists(boardId, ticketId);
        enforceRole(boardId, userId, minimumRole);
        return ticket;
    }

    @Override
    public Backlog verifyBacklogAccess(UUID organizationId, UUID boardId, UUID userId, BoardMemberRole minimumRole) {
        boardValidator.ensureBoardInOrganization(organizationId, boardId);
        Backlog backlog = backlogValidator.ensureBacklogExistsByBoardId(boardId);
        enforceRole(boardId, userId, minimumRole);
        return backlog;
    }

    @Override
    public Milestone verifyMilestoneAccess(UUID organizationId, UUID boardId, UUID milestoneId, UUID userId, BoardMemberRole minimumRole) {
        boardValidator.ensureBoardInOrganization(organizationId, boardId);
        // Membership is checked before the milestone lookup so that a non-member
        // learns nothing about the board's milestones.
        enforceRole(boardId, userId, minimumRole);
        return milestoneValidator.ensureMilestoneExists(boardId, milestoneId);
    }

    /**
     * Looks up the board membership and verifies the member's role is within
     * the allowed set derived from the minimum role.
     *
     * @param boardId     the board identifier
     * @param userId      the user identifier
     * @param minimumRole the minimum role required
     * @throws NotFoundException  if the user is not a member of the board
     * @throws ForbiddenException if the member's role is insufficient
     */
    private void enforceRole(UUID boardId, UUID userId, BoardMemberRole minimumRole) {
        BoardMember member = boardValidator.ensureBoardMemberExists(boardId, userId);

        Set<BoardMemberRole> allowedRoles = resolveAllowedRoles(minimumRole);
        if (!allowedRoles.contains(member.getRole())) {
            loggingService.error(
                    LOG_PREFIX,
                    LogLayer.SERVICE,
                    BoardAccessServiceLogConstant.INSUFFICIENT_PERMISSIONS,
                    boardId,
                    userId,
                    minimumRole);
            throw new ForbiddenException(
                    ExceptionDomainKazanError.SEC_INSUFFICIENT_BOARD_PERMISSIONS,
                    Map.of(
                            BoardConstant.FIELD_BOARD_ID, boardId,
                            BoardConstant.FIELD_USER_ID, userId));
        }
    }

    /**
     * Maps a minimum role to the corresponding set of allowed roles.
     */
    private static Set<BoardMemberRole> resolveAllowedRoles(BoardMemberRole minimumRole) {
        return switch (minimumRole) {
            case VIEWER -> BoardMemberRoleConstant.BOARD_ROLES_WITH_VIEW_ACCESS;
            case MEMBER -> BoardMemberRoleConstant.BOARD_ROLES_WITH_CONTRIBUTE_ACCESS;
            case ADMIN -> BoardMemberRoleConstant.BOARD_ROLES_WITH_ADMIN_ACCESS;
            case OWNER -> BoardMemberRoleConstant.BOARD_ROLES_WITH_OWNER_ACCESS;
        };
    }
}
