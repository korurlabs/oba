package com.korur.oba.apps.kaizen.ticket.constant;

/**
 * Log message constants for ticket domain exceptions.
 * <p>
 * These constants provide consistent exception logging messages across
 * the ticket domain, used in validators and services when errors occur.
 */
public final class TicketExceptionLogConstant {

    private TicketExceptionLogConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Log message for when a ticket is not found.
     */
    public static final String TICKET_NOT_FOUND = "Ticket not found: {}";

    /**
     * Log message for when a ticket is not found by its key.
     */
    public static final String TICKET_NOT_FOUND_BY_KEY = "Ticket not found by key: {}";

    /**
     * Log message for when a ticket assignee is not found.
     */
    public static final String TICKET_ASSIGNEE_NOT_FOUND = "Ticket assignee not found: ticketId={}, userId={}";

    /**
     * Log message for when a ticket reviewer is not found.
     */
    public static final String TICKET_REVIEWER_NOT_FOUND = "Ticket reviewer not found: ticketId={}, userId={}";

    /**
     * Log message for when a ticket status transition is invalid.
     */
    public static final String TICKET_INVALID_STATUS_TRANSITION = "Invalid ticket status transition: ticketId={}, from={}, to={}";

    /**
     * Log message for when a ticket cannot be moved to a sprint.
     */
    public static final String TICKET_CANNOT_MOVE_TO_SPRINT = "Ticket cannot be moved to sprint: ticketId={}, sprintId={}";

    /**
     * Log message for when a parent ticket reference is invalid.
     */
    public static final String TICKET_INVALID_PARENT = "Invalid parent ticket: ticketId={}, parentTicketId={}";
}
