package com.korur.oba.apps.kazan.ticket.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kazan.ticket.constant.service.TicketServiceLogConstant;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.enums.TicketStatus;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Single enforcement point for the backlog status rule.
 *
 * A top-level ticket that sits in the backlog may only carry the TODO status.
 * When a placement move (move, bulk move, sprint completion, unparenting) leaves
 * such a ticket without a sprint, its status is silently reset to TODO, because
 * the user asked for a new place, not a new status. A write that sets the status
 * itself is refused instead by
 * {@link com.korur.oba.apps.kazan.ticket.validator.TicketValidator#ensureBacklogTicketStatusAllowed(Ticket)},
 * so a status change is never discarded without a word. Every ticket mutation
 * path that can change placement or status calls this component immediately
 * before persisting, so no path can leave a backlog ticket in IN_PROGRESS,
 * WAITING_FOR_THIRD_PARTY, IN_REVIEW or DONE.
 *
 * Sub-tickets are deliberately exempt. A sub-ticket inherits its parent's
 * placement on create, so a sub-ticket of a backlog ticket is itself backlog
 * placed -- yet its status is the checkbox that drives the parent's completion
 * percentage on the Backlog page. Applying the rule to sub-tickets would make
 * that checkbox impossible to tick, silently resetting every completion the
 * user records. The rule is about the backlog as a list of top-level work
 * items, not about every row that happens to hang off one.
 *
 * @see com.korur.oba.apps.kazan.ticket.service.impl.TicketServiceImpl
 * @see TicketHelper
 * @see com.korur.oba.apps.kazan.backlog.service.impl.BacklogServiceImpl
 */
@Component
@RequiredArgsConstructor
public class TicketStatusNormalizer {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(TicketStatusNormalizer.class);

    private final LoggingService loggingService;

    /**
     * Resets the ticket status to TODO when the ticket is placed in the backlog.
     *
     * A ticket is considered backlog-placed when it is a top-level ticket with
     * no sprint. Tickets that belong to a sprint keep whatever status they were
     * given, and so do sub-tickets, whose status is completion tracking for
     * their parent rather than a position on the backlog board. The reset is
     * applied in place on the passed entity, so the caller must invoke this
     * before saving.
     *
     * @param ticket the ticket to normalize, ignored when null
     */
    public void normalizeBacklogStatus(Ticket ticket) {
        if (ticket == null || ticket.getSprint() != null || ticket.getParentTicket() != null) {
            return;
        }

        TicketStatus currentStatus = ticket.getStatus();
        if (currentStatus == TicketStatus.TODO) {
            return;
        }

        ticket.setStatus(TicketStatus.TODO);

        loggingService.info(LOG_PREFIX, LogLayer.HELPER,
                TicketServiceLogConstant.TICKET_STATUS_RESET_FOR_BACKLOG_PLACEMENT,
                ticket.getKey(), currentStatus, TicketStatus.TODO);
    }

    /**
     * Applies the backlog status rule to every ticket in the given collection.
     *
     * @param tickets the tickets to normalize, ignored when null
     */
    public void normalizeBacklogStatus(Iterable<Ticket> tickets) {
        if (tickets == null) {
            return;
        }

        for (Ticket ticket : tickets) {
            normalizeBacklogStatus(ticket);
        }
    }
}
