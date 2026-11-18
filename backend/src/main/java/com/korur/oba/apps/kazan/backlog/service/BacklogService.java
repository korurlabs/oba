package com.korur.oba.apps.kazan.backlog.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;

/**
 * Write service interface for backlog operations.
 * <p>
 * Handles adding tickets to the backlog.
 */
public interface BacklogService {

    /**
     * Adds a ticket to the board's backlog and removes it from any sprint.
     *
     * A parent ticket takes its sub-tickets along, so a ticket hierarchy is never
     * split between the backlog and a sprint. A sub-ticket whose top-level parent
     * is not in this backlog is detached from its parent and reset to TODO, so it
     * continues as a standalone backlog ticket.
     *
     * @param user           the authenticated user performing the operation
     * @param organizationId the organization of the request, already checked by the interceptor
     * @param boardId        the board identifier
     * @param ticketId       the ticket identifier
     */
    void addTicketToBacklog(User user, UUID organizationId, UUID boardId, UUID ticketId);

    /**
     * Creates the default backlog for a newly created board.
     * Used by board domain during board creation.
     *
     * @param board the board to create the backlog for
     */
    void createDefaultBacklog(com.korur.oba.apps.kazan.board.entity.Board board);
}
