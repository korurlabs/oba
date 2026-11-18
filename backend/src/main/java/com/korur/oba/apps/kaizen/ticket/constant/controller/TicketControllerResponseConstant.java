package com.korur.oba.apps.kaizen.ticket.constant.controller;

/**
 * Response message constants for ticket controller operations.
 * <p>
 * These constants provide consistent success messages returned to clients
 * in API responses.
 */
public final class TicketControllerResponseConstant {

    private TicketControllerResponseConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String RESPONSE_TICKET_CREATED = "Ticket created successfully";
    public static final String RESPONSE_TICKET_UPDATED = "Ticket updated successfully";
    public static final String RESPONSE_TICKET_DELETED = "Ticket deleted successfully";
    public static final String RESPONSE_TICKET_RETRIEVED = "Ticket retrieved successfully";
    public static final String RESPONSE_TICKET_LIST_RETRIEVED = "Tickets retrieved successfully";
    public static final String RESPONSE_TICKET_MOVED = "Ticket moved successfully";
    public static final String RESPONSE_TICKET_ASSIGNEES_UPDATED = "Ticket assignees updated successfully";
    public static final String RESPONSE_TICKET_REVIEWERS_UPDATED = "Ticket reviewers updated successfully";

    public static final String RESPONSE_ALL_TICKET_LIST_RETRIEVED = "All tickets retrieved successfully";
    public static final String RESPONSE_HOME_SUMMARY_RETRIEVED = "Home summary retrieved successfully";
    public static final String RESPONSE_HOME_STALE_BACKLOG_RETRIEVED = "Home stale backlog retrieved successfully";
    public static final String RESPONSE_HOME_LEADERBOARD_RETRIEVED = "Home leaderboard retrieved successfully";
    public static final String RESPONSE_HOME_SPRINT_RETRIEVED = "Home sprint retrieved successfully";
    public static final String RESPONSE_HOME_DEADLINES_RETRIEVED = "Home deadlines retrieved successfully";
    public static final String RESPONSE_TICKET_RESTORED = "Ticket restored successfully";
    public static final String RESPONSE_TICKETS_BULK_MOVED = "Tickets bulk-moved successfully";
}
