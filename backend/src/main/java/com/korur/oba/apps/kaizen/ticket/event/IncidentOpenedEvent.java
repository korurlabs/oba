package com.korur.oba.apps.kaizen.ticket.event;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.ticket.enums.TicketPriority;

/**
 * Published when a ticket became an open incident of an urgent priority (P1 or P2): reported by
 * a monitoring source, created by a member, or raised to an urgent priority by an update.
 *
 * Consumers act after the transaction commits, so a rolled-back ticket alerts nobody. The board
 * members are alerted on their phones by {@link com.korur.oba.apps.kaizen.ticket.listener.IncidentPushListener}.
 * The board name travels with the event because the listener runs without the transaction that
 * holds the board.
 *
 * @param organizationId organization of the board
 * @param boardId        board the incident is on; its members are alerted
 * @param boardName      board name, shown as the alert's subject
 * @param ticketId       the incident ticket
 * @param ticketKey      key of the incident ticket, "KZN-311"
 * @param title          title of the incident ticket
 * @param priority       the urgent priority, {@link TicketPriority#P1} or {@link TicketPriority#P2}
 * @param actorId        user who opened or raised the incident, who is not alerted; null when a
 *                       monitoring source reported it
 */
public record IncidentOpenedEvent(UUID organizationId,
                                  UUID boardId,
                                  String boardName,
                                  UUID ticketId,
                                  String ticketKey,
                                  String title,
                                  TicketPriority priority,
                                  UUID actorId) {
}
