package com.korur.oba.apps.kaizen.ticket.constant.service;

/**
 * Log message constants for ticket service operations.
 * <p>
 * These constants provide consistent logging at the service layer,
 * tracking business logic operations and their outcomes.
 */
public final class TicketServiceLogConstant {

    private TicketServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String TICKET_CREATED = "Ticket created: {}";
    public static final String TICKET_UPDATED = "Ticket updated: {}";
    public static final String TICKET_DELETED = "Ticket deleted: {}";
    public static final String TICKETS_DELETED = "{} tickets deleted";
    public static final String TICKET_MOVED = "Ticket moved: {} -> {}";
    public static final String TICKET_ASSIGNEES_UPDATED = "Ticket assignees updated: {}";
    public static final String TICKET_REVIEWERS_UPDATED = "Ticket reviewers updated: {}";
    public static final String TICKETS_MOVED_TO_BACKLOG = "Moved {} incomplete tickets from sprint {} to backlog";
    public static final String TICKET_CREATED_FROM_SUGGESTION = "Ticket created from AI suggestion: {}";
    public static final String TICKET_ASSIGNEE_ADDED = "Assignee added to ticket {}: user {}";
    public static final String TICKETS_TRANSFERRED_TO_SPRINT = "Transferred {} tickets to sprint {}";
    public static final String TICKETS_TRANSFERRED_TO_BACKLOG = "Transferred {} tickets to backlog {}";
    public static final String TICKET_RESTORED = "Ticket restored: {}";
    public static final String TICKET_CHILDREN_RESTORED = "Restored children for ticket {}: {} comments, {} assignees, {} reviewers, {} labels";
    public static final String TICKET_SUB_TICKETS_RESTORED = "Restored {} sub-tickets for ticket {}";
    public static final String TICKET_ASSIGNEE_RESTORED = "Restored soft-deleted assignee for ticket {}: user {}";
    public static final String TICKETS_RELOCATED = "Relocated {} tickets via action {}. SourceContextId: {}";
    public static final String TICKETS_BULK_MOVED = "Bulk-moved {} tickets from board {}";
    public static final String TICKET_DETACHED_ON_BOARD_CHANGE = "Sub-ticket {} detached from {} and kept on its board because the parent moves to board {}";
    public static final String TICKET_KEPT_ON_BOARD_ON_RELOCATION = "Sub-ticket {} moved to the backlog of its own board instead of board {} because its parent {} stays";
    public static final String TICKET_STATUS_RESET_FOR_BACKLOG_PLACEMENT = "Ticket {} status auto-reset from {} to {} because it is placed in the backlog";
    public static final String TICKET_TYPE_CONVERTED_FROM_DEPRECATED_ALIAS = "Ticket {} type converted from deprecated {} to {}";
    public static final String TICKET_PERCENTAGE_SYNCED = "Ticket {} percentage set to {} from a daily update";
    public static final String INCIDENT_TICKET_CREATED = "Incident ticket {} created on board {} by system actor {}";
    public static final String INCIDENT_OPENED_PUBLISHED = "Urgent incident {} on board {} at priority {} published for the board members' alert";
    public static final String INCIDENT_TICKET_ALREADY_OPEN = "Incident {} on board {} already has open ticket {}, nothing written";
}
