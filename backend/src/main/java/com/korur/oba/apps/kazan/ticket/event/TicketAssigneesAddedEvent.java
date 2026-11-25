package com.korur.oba.apps.kazan.ticket.event;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal

/**
 * Published when a ticket create or update adds assignees to a ticket. Only the users the change
 * added are named, so an update that keeps an assignee does not start that assignee's work again.
 *
 * Consumers act after the transaction commits, so a rolled-back assignment starts nothing. An agent
 * among the added users is fired by {@link com.korur.oba.apps.kazan.ticket.listener.TicketAgentRoutineListener}.
 * The ticket key travels with the event because the listener runs without the transaction that holds
 * the ticket, and the key is the only input a fired routine receives.
 *
 * @param organizationId organization of the ticket's board
 * @param ticketId       the ticket
 * @param ticketKey      key of the ticket, "KZN-311"
 * @param addedUserIds   the users the change added as assignees, not empty
 */
public record TicketAssigneesAddedEvent(UUID organizationId,
                                        UUID ticketId,
                                        String ticketKey,
                                        List<UUID> addedUserIds) {

    public TicketAssigneesAddedEvent {
        addedUserIds = List.copyOf(addedUserIds);
    }
}
