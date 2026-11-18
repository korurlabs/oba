package com.korur.oba.apps.kaizen.ticket.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.backlog.entity.Backlog;
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.systemactor.entity.SystemActor;
import com.korur.oba.apps.kaizen.ticket.dto.command.IncidentReport;
import com.korur.oba.apps.kaizen.ticket.dto.request.TicketBulkMoveRequest;
import com.korur.oba.apps.kaizen.ticket.dto.request.TicketCreateRequest;
import com.korur.oba.apps.kaizen.ticket.dto.request.TicketMoveRequest;
import com.korur.oba.apps.kaizen.ticket.dto.request.TicketOrderUpdateRequest;
import com.korur.oba.apps.kaizen.ticket.dto.request.TicketUpdateRequest;
import com.korur.oba.apps.kaizen.ticket.dto.response.TicketBulkMoveResponse;
import com.korur.oba.apps.kaizen.ticket.dto.response.TicketDetailResponse;
import com.korur.oba.apps.kaizen.ticket.enums.IncidentOutcome;
import com.korur.oba.apps.kaizen.ticket.enums.TicketPriority;
import com.korur.oba.apps.kaizen.ticket.enums.TicketType;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Write operations for tickets.
 */
public interface TicketService {

    /**
     * Creates a new ticket in a board.
     */
    TicketDetailResponse createTicket(User user, UUID organizationId, UUID boardId, TicketCreateRequest request);

    /**
     * Updates ticket metadata.
     */
    TicketDetailResponse updateTicket(User user, UUID organizationId, UUID boardId, UUID ticketId, TicketUpdateRequest request);

    /**
     * Deletes a ticket.
     */
    void deleteTicket(User user, UUID organizationId, UUID boardId, UUID ticketId);

    /**
     * Moves a ticket to a different sprint or status.
     */
    TicketDetailResponse moveTicket(User user, UUID organizationId, UUID boardId, UUID ticketId, TicketMoveRequest request);

    /**
     * Updates the order of a ticket.
     */
    TicketDetailResponse updateOrder(User user, UUID organizationId, UUID boardId, UUID ticketId, TicketOrderUpdateRequest request);

    /**
     * Creates a ticket directly with minimal fields.
     * Used by AI domain for batch ticket creation from suggestions.
     *
     * @param board       the board to create the ticket in
     * @param backlog     the backlog to assign (nullable)
     * @param title       ticket title
     * @param description ticket description
     * @param key         ticket key (e.g. "BOARD-1")
     * @param type        ticket type
     * @param priority    ticket priority
     * @return the created ticket detail response
     */
    TicketDetailResponse createTicketDirect(
            User user,
            Board board,
            Backlog backlog,
            String title,
            String description,
            String key,
            TicketType type,
            TicketPriority priority);

    /**
     * Opens a P1 INCIDENT ticket in a board's backlog for an incident reported by a
     * system actor, unless the incident already has an open ticket on that board.
     *
     * A system actor is no board member, so this method runs no board access check:
     * the caller must take the actor from the authenticated principal, whose authority
     * the security chain has already checked. The board row is locked for the rest of
     * the transaction, so two reports of one incident cannot both create a ticket.
     * A board of another organization than the reporter's is refused as not found.
     *
     * @param actor                 the system actor reporting the incident
     * @param organizationId        the organization bound to the reporter's credential
     * @param boardId               the board the incident ticket belongs to
     * @param report                the incident key, ticket title and description
     * @param ticketCreationAllowed false when the caller has reached its own limit of
     *                              new tickets; the incident is then ignored
     * @return CREATED when a ticket was created, IGNORED when nothing was written
     */
    IncidentOutcome reportIncident(
            SystemActor actor,
            UUID organizationId,
            UUID boardId,
            IncidentReport report,
            boolean ticketCreationAllowed);

    /**
     * Bulk-moves tickets to a different sprint or backlog within the same
     * or across boards. DELETE action is not allowed.
     *
     * @param user           the user performing the bulk move
     * @param organizationId the organization of the request, already checked by the interceptor;
     *                       every source and target board must belong to it
     * @param boardId        the source board identifier
     * @param request        the bulk move request
     */
    TicketBulkMoveResponse bulkMoveTickets(User user, UUID organizationId, UUID boardId, TicketBulkMoveRequest request);

    /**
     * Restores a soft-deleted ticket and all its cascade-deleted children.
     * Validates that the parent board is active (not deleted) and the ticket
     * is actually deleted before restoring.
     *
     * Restores: the ticket itself, comments, assignees, reviewers, ticket-labels,
     * and sub-tickets recursively.
     *
     * @param user           the user performing the restore
     * @param organizationId the organization of the request, already checked by the interceptor
     * @param boardId        the board identifier
     * @param ticketId       the ticket identifier to restore
     */
    void restoreTicket(User user, UUID organizationId, UUID boardId, UUID ticketId);

    /**
     * Sets a ticket's progress percentage and nothing else, for callers that report
     * progress outside the ticket form, such as a daily update.
     *
     * The user needs board role MEMBER or higher on the ticket's board, the same rule
     * as a ticket update, so the caller's own eligibility check never replaces it.
     *
     * @param user           the user reporting the progress
     * @param organizationId the organization the progress is reported in; a ticket on a
     *                       board of another organization is reported as not found
     * @param ticketId       the ticket identifier
     * @param percentage     the new progress, already validated by the caller to 0-100
     */
    void syncTicketPercentage(User user, UUID organizationId, UUID ticketId, int percentage);
}
