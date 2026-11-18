package com.korur.oba.apps.kazan.ticket.enums;

/**
 * Defines the workflow status of a ticket.
 */
public enum TicketStatus {

    // Ticket is created and waiting to be worked on.
    TODO,

    // Ticket is actively being worked on.
    IN_PROGRESS,

    // Ticket is blocked on someone outside the team, such as a vendor or a customer.
    WAITING_FOR_THIRD_PARTY,

    // Ticket is submitted for review.
    IN_REVIEW,

    // Ticket is completed.
    DONE
}
