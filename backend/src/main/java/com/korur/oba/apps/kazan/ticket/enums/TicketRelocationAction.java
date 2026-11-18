package com.korur.oba.apps.kazan.ticket.enums;

/**
 * Defines what to do with tickets during relocation events
 * (sprint delete, sprint complete, board delete, bulk move).
 */
public enum TicketRelocationAction {

    // Move tickets to the target backlog.
    MOVE_TO_BACKLOG,

    // Move tickets to the target sprint.
    MOVE_TO_SPRINT,

    // Soft-delete all tickets.
    DELETE
}
