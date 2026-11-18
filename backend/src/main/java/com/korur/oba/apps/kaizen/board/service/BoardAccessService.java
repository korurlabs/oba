package com.korur.oba.apps.kaizen.board.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.backlog.entity.Backlog;
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.board.enums.BoardMemberRole;
import com.korur.oba.apps.kaizen.milestone.entity.Milestone;
import com.korur.oba.apps.kaizen.sprint.entity.Sprint;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;

/**
 * Centralised board access control service.
 * <p>
 * Each method validates that the requested entity exists, that its board belongs
 * to the request's organization, and that the user holds at least the specified
 * minimum role on the board, and returns the validated entity so callers can
 * proceed without redundant lookups. A board of another organization reads as a
 * missing board. The organization role itself is checked once per request by
 * {@link com.korur.oba.dojo.security.interceptor.RequiredUserAccessInterceptor},
 * so these methods never check it again.
 */
public interface BoardAccessService {

    /**
     * Validates that the board exists and the user has at least the minimum role.
     *
     * @param organizationId the organization of the request, already checked by the interceptor
     * @param boardId     the board identifier
     * @param userId      the authenticated user identifier
     * @param minimumRole the minimum board member role required
     * @return the validated board entity
     */
    Board verifyBoardAccess(UUID organizationId, UUID boardId, UUID userId, BoardMemberRole minimumRole);

    /**
     * Validates that the sprint exists, belongs to the board, and the user has
     * at least the minimum role on that board.
     *
     * @param organizationId the organization of the request, already checked by the interceptor
     * @param boardId     the board identifier
     * @param sprintId    the sprint identifier
     * @param userId      the authenticated user identifier
     * @param minimumRole the minimum board member role required
     * @return the validated sprint entity
     */
    Sprint verifySprintAccess(UUID organizationId, UUID boardId, UUID sprintId, UUID userId, BoardMemberRole minimumRole);

    /**
     * Validates that the ticket exists on the board and the user has at least
     * the minimum role on that board.
     *
     * @param organizationId the organization of the request, already checked by the interceptor
     * @param boardId     the board identifier
     * @param ticketId    the ticket identifier
     * @param userId      the authenticated user identifier
     * @param minimumRole the minimum board member role required
     * @return the validated ticket entity
     */
    Ticket verifyTicketAccess(UUID organizationId, UUID boardId, UUID ticketId, UUID userId, BoardMemberRole minimumRole);

    /**
     * Validates that the backlog exists for the board and the user has at least
     * the minimum role on that board.
     *
     * @param organizationId the organization of the request, already checked by the interceptor
     * @param boardId     the board identifier
     * @param userId      the authenticated user identifier
     * @param minimumRole the minimum board member role required
     * @return the validated backlog entity
     */
    Backlog verifyBacklogAccess(UUID organizationId, UUID boardId, UUID userId, BoardMemberRole minimumRole);

    /**
     * Validates that the milestone lives on the board and the user has at least
     * the minimum role on that board. A milestone on another board is reported
     * as not found.
     *
     * @param organizationId the organization of the request, already checked by the interceptor
     * @param boardId     the board identifier
     * @param milestoneId the milestone identifier
     * @param userId      the authenticated user identifier
     * @param minimumRole the minimum board member role required
     * @return the validated milestone entity
     */
    Milestone verifyMilestoneAccess(UUID organizationId, UUID boardId, UUID milestoneId, UUID userId, BoardMemberRole minimumRole);
}
