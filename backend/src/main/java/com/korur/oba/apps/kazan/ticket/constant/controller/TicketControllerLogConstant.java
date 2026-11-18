package com.korur.oba.apps.kazan.ticket.constant.controller;

/**
 * Log message constants for ticket controller operations.
 * <p>
 * These constants provide consistent logging at the controller layer,
 * tracking incoming requests and their completion status.
 */
public final class TicketControllerLogConstant {

    private TicketControllerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String TICKET_CREATE_REQUEST = "Received request to create ticket";
    public static final String TICKET_CREATE_COMPLETED = "Create ticket request completed";

    public static final String TICKET_GET_REQUEST = "Received request to get ticket";
    public static final String TICKET_GET_COMPLETED = "Get ticket request completed";

    public static final String TICKET_GET_BY_KEY_REQUEST = "Received request to get ticket by key";
    public static final String TICKET_GET_BY_KEY_COMPLETED = "Get ticket by key request completed";

    public static final String TICKET_LIST_REQUEST = "Received request to list tickets";
    public static final String TICKET_LIST_COMPLETED = "List tickets request completed";

    public static final String TICKET_UPDATE_REQUEST = "Received request to update ticket";
    public static final String TICKET_UPDATE_COMPLETED = "Update ticket request completed";

    public static final String TICKET_DELETE_REQUEST = "Received request to delete ticket";
    public static final String TICKET_DELETE_COMPLETED = "Delete ticket request completed";

    public static final String TICKET_MOVE_REQUEST = "Received request to move ticket";
    public static final String TICKET_MOVE_COMPLETED = "Move ticket request completed";

    public static final String TICKET_ASSIGNEE_UPDATE_REQUEST = "Received request to update ticket assignees";
    public static final String TICKET_ASSIGNEE_UPDATE_COMPLETED = "Update ticket assignees request completed";

    public static final String TICKET_REVIEWER_UPDATE_REQUEST = "Received request to update ticket reviewers";
    public static final String TICKET_REVIEWER_UPDATE_COMPLETED = "Update ticket reviewers request completed";

    public static final String ALL_TICKET_LIST_REQUEST = "Received request to list all tickets";
    public static final String ALL_TICKET_LIST_COMPLETED = "List all tickets request completed";

    public static final String HOME_SUMMARY_REQUEST = "Received request to get the home summary";
    public static final String HOME_SUMMARY_COMPLETED = "Get home summary request completed";
    public static final String HOME_STALE_BACKLOG_REQUEST = "Received request to get the home stale backlog";
    public static final String HOME_STALE_BACKLOG_COMPLETED = "Get home stale backlog request completed";
    public static final String HOME_LEADERBOARD_REQUEST = "Received request to get the home leaderboard";
    public static final String HOME_LEADERBOARD_COMPLETED = "Get home leaderboard request completed";
    public static final String HOME_SPRINT_REQUEST = "Received request to get the home sprint";
    public static final String HOME_SPRINT_COMPLETED = "Get home sprint request completed";
    public static final String HOME_DEADLINES_REQUEST = "Received request to get the home deadlines";
    public static final String HOME_DEADLINES_COMPLETED = "Get home deadlines request completed";

    public static final String TICKET_RESTORE_REQUEST = "Received request to restore ticket";
    public static final String TICKET_RESTORE_COMPLETED = "Restore ticket request completed";

    public static final String TICKET_BULK_MOVE_REQUEST = "Received request to bulk move tickets";
    public static final String TICKET_BULK_MOVE_COMPLETED = "Bulk move tickets request completed";
}
