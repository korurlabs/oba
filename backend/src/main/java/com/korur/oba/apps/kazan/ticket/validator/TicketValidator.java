package com.korur.oba.apps.kazan.ticket.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.sprint.constant.SprintConstant;
import com.korur.oba.apps.kazan.ticket.constant.TicketConstant;
import com.korur.oba.apps.kazan.ticket.constant.TicketExceptionLogConstant;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.enums.TicketRelocationAction;
import com.korur.oba.apps.kazan.ticket.enums.TicketStatus;
import com.korur.oba.apps.kazan.ticket.repository.TicketRepository;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Validator for ticket lookups.
 */
@Component
@RequiredArgsConstructor
public class TicketValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(TicketValidator.class);

    private final TicketRepository ticketRepository;
    private final LoggingService loggingService;

    /**
     * Ensures the ticket has a valid placement. Ticket can only be in one placement
     * at a time (backlog or sprint).
     */
    public void ensureTicketHasCorrectPlacement(UUID sprintId, UUID backlogId) {
        boolean hasNoPlacement = sprintId == null && backlogId == null;
        boolean hasBothPlacements = sprintId != null && backlogId != null;
        if (hasNoPlacement || hasBothPlacements) {
            throw new ValidationException(
                    ExceptionDomainKazanError.VAL_TICKET_PLACEMENT,
                    Map.of(
                            TicketConstant.FIELD_SPRINT_ID,
                            sprintId == null ? "null" : sprintId.toString(),
                            TicketConstant.FIELD_BACKLOG_ID,
                            backlogId == null ? "null" : backlogId.toString()));
        }

    }

    /**
     * Ensures the ticket exists and returns it.
     */
    public Ticket ensureTicketExists(UUID boardId, UUID ticketId) {
        // Fetch the ticket or raise a domain-specific not-found error.
        return ticketRepository.findByBoardIdAndId(boardId, ticketId)
                .orElseThrow(() -> {
                    // Log for observability before throwing the exception.
                    loggingService.error(
                            LOG_PREFIX,
                            LogLayer.VALIDATOR,
                            TicketExceptionLogConstant.TICKET_NOT_FOUND,
                            ticketId);
                    // Propagate a NotFoundException with field context.
                    return new NotFoundException(
                            ExceptionDomainKazanError.NOT_TICKET_FOUND,
                            Map.of(TicketConstant.FIELD_TICKET_ID, ticketId));
                });
    }

    /**
     * Ensures the ticket order is correct.
     */
    public void ensureTicketOrderIsCorrect(int oldOrder, int newOrder) {
        if (oldOrder == newOrder) {
            throw new ValidationException(
                    ExceptionDomainKazanError.VAL_TICKET_ORDER,
                    Map.of(
                            TicketConstant.FIELD_OLD_ORDER,
                            oldOrder,
                            TicketConstant.FIELD_NEW_ORDER,
                            newOrder));
        }
    }

    /**
     * Ensures a ticket relocation action is provided when tickets exist.
     *
     * @param action    the ticket relocation action (may be null)
     * @param contextId the context identifier (sprint or board) for error reporting
     * @throws ValidationException if the action is null
     */
    public void ensureTicketActionProvided(TicketRelocationAction action, UUID contextId) {
        if (action == null) {
            throw new ValidationException(
                    ExceptionDomainKazanError.VAL_SPRINT_TICKET_ACTION_REQUIRED,
                    Map.of(SprintConstant.FIELD_SPRINT_ID, contextId));
        }
    }

    /**
     * Ensures a target sprint ID is provided when action is MOVE_TO_SPRINT.
     *
     * @param targetSprintId the target sprint identifier (may be null)
     * @param contextId      the context identifier for error reporting
     * @throws ValidationException if the target sprint ID is null
     */
    public void ensureTargetSprintProvided(UUID targetSprintId, UUID contextId) {
        if (targetSprintId == null) {
            throw new ValidationException(
                    ExceptionDomainKazanError.VAL_SPRINT_TARGET_SPRINT_REQUIRED,
                    Map.of(SprintConstant.FIELD_SPRINT_ID, contextId));
        }
    }

    /**
     * Ensures the bulk move action is valid (DELETE is not allowed).
     *
     * @param action the ticket relocation action
     * @throws ValidationException if the action is DELETE
     */
    public void ensureBulkMoveActionValid(TicketRelocationAction action) {
        if (action == TicketRelocationAction.DELETE) {
            throw new ValidationException(
                    ExceptionDomainKazanError.VAL_BULK_MOVE_DELETE_NOT_ALLOWED,
                    Map.of());
        }
    }

    /**
     * Ensures all requested tickets were found by comparing counts.
     *
     * @param tickets      the tickets found
     * @param requestedIds the requested ticket IDs
     * @throws ValidationException if some tickets were not found
     */
    public void ensureAllTicketsFound(List<Ticket> tickets, List<UUID> requestedIds) {
        if (tickets.size() != requestedIds.size()) {
            throw new ValidationException(
                    ExceptionDomainKazanError.VAL_BULK_MOVE_TICKETS_NOT_FOUND,
                    Map.of(TicketConstant.FIELD_REQUESTED_COUNT, requestedIds.size(),
                            TicketConstant.FIELD_FOUND_COUNT, tickets.size()));
        }
    }

    /**
     * Ensures the ticket is soft-deleted (for restore operations).
     *
     * @param ticket the ticket to validate
     * @throws ValidationException if the ticket is not deleted
     */
    public void ensureTicketIsDeleted(Ticket ticket) {
        if (!ticket.isDeleted()) {
            throw new ValidationException(
                    ExceptionDomainKazanError.VAL_TICKET_NOT_DELETED,
                    Map.of(TicketConstant.FIELD_TICKET_ID, ticket.getId()));
        }
    }

    /**
     * Refuses an explicit status other than TODO on a top-level backlog ticket.
     *
     * The backlog is the list of top-level work that is not planned yet, so its tickets can only
     * be TODO. A placement move into the backlog resets the status silently through
     * {@link com.korur.oba.apps.kazan.ticket.helper.TicketStatusNormalizer}; a write that sets the
     * status itself is refused instead, so the caller learns that the change did not stick.
     * Sub-tickets are exempt, because their status is the completion checkbox of their parent.
     *
     * @param ticket the ticket with its placement and its requested status applied
     * @throws ValidationException if the ticket has no sprint and no parent and is not TODO
     */
    public void ensureBacklogTicketStatusAllowed(Ticket ticket) {
        boolean isTopLevelBacklogTicket = ticket.getSprint() == null && ticket.getParentTicket() == null;
        if (isTopLevelBacklogTicket && ticket.getStatus() != TicketStatus.TODO) {
            throw new ValidationException(
                    ExceptionDomainKazanError.VAL_TICKET_BACKLOG_STATUS,
                    Map.of(TicketConstant.FIELD_TICKET_KEY, String.valueOf(ticket.getKey()),
                            TicketConstant.FIELD_STATUS, String.valueOf(ticket.getStatus())));
        }
    }

    /**
     * Refuses to close an incident whose root cause analysis or lessons learned is missing.
     *
     * Every incident closed must document its cause, so the move to DONE needs both texts. An
     * incident that was already DONE stays editable without them, so incidents closed before the
     * rule existed are not locked. The caller applies this to incidents only.
     *
     * @param ticket         the incident with the update applied
     * @param previousStatus the stored status before the write, or null on create
     * @throws ValidationException if the incident moves to DONE with a blank RCA or lessons learned
     */
    public void ensureIncidentCanBeClosed(Ticket ticket, TicketStatus previousStatus) {
        if (ticket.getStatus() != TicketStatus.DONE || previousStatus == TicketStatus.DONE) {
            return;
        }

        List<String> missingFields = new ArrayList<>();
        if (isBlank(ticket.getRca())) {
            missingFields.add(TicketConstant.FIELD_RCA);
        }
        if (isBlank(ticket.getLessonsLearned())) {
            missingFields.add(TicketConstant.FIELD_LESSONS_LEARNED);
        }

        if (!missingFields.isEmpty()) {
            throw new ValidationException(
                    ExceptionDomainKazanError.VAL_INCIDENT_CLOSE_REQUIRES_RCA,
                    Map.of(TicketConstant.FIELD_TICKET_KEY, String.valueOf(ticket.getKey()),
                            TicketConstant.FIELD_MISSING_FIELDS, missingFields));
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
