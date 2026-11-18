package com.korur.oba.apps.kazan.ticket.constant;

/**
 * Constants for ticket field names used in error messages and validation.
 * <p>
 * These constants provide consistent field naming across the ticket domain,
 * particularly useful for exception error value maps.
 */
public final class TicketConstant {

    private TicketConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Field name constant for ticket ID.
     */
    public static final String FIELD_TICKET_ID = "ticketId";

    /**
     * Field name constant for ticket key.
     */
    public static final String FIELD_TICKET_KEY = "key";

    /**
     * Field name constant for board ID.
     */
    public static final String FIELD_BOARD_ID = "boardId";

    /**
     * Field name constant for sprint ID.
     */
    public static final String FIELD_SPRINT_ID = "sprintId";

    /**
     * Field name constant for backlog ID.
     */
    public static final String FIELD_BACKLOG_ID = "backlogId";

    /**
     * Field name constant for ticket title.
     */
    public static final String FIELD_TITLE = "title";

    /**
     * Field name constant for ticket status.
     */
    public static final String FIELD_STATUS = "status";

    /**
     * Field name constant for ticket priority.
     */
    public static final String FIELD_PRIORITY = "priority";

    /**
     * Field name constant for ticket type.
     */
    public static final String FIELD_TYPE = "type";

    /**
     * Field name constant for assignee ID.
     */
    public static final String FIELD_ASSIGNEE_ID = "assigneeId";

    /**
     * Field name constant for reviewer ID.
     */
    public static final String FIELD_REVIEWER_ID = "reviewerId";

    /**
     * Field name constant for parent ticket ID.
     */
    public static final String FIELD_PARENT_TICKET_ID = "parentTicketId";

    /**
     * Field name constant for ticket's old order.
     */
    public static final String FIELD_OLD_ORDER = "oldOrder";

    /**
     * Field name constant for ticket's new order.
     */
    public static final String FIELD_NEW_ORDER = "newOrder";

    /**
     * Field name constant for requested ticket count in bulk operations.
     */
    public static final String FIELD_REQUESTED_COUNT = "requestedCount";

    /**
     * Field name constant for found ticket count in bulk operations.
     */
    public static final String FIELD_FOUND_COUNT = "foundCount";

    /**
     * Field name constant for the key an external monitoring source gives an incident ticket.
     */
    public static final String FIELD_INCIDENT_KEY = "incidentKey";

    /**
     * Field name constant for the sub-ticket move mode of a move request.
     */
    public static final String FIELD_SUB_TICKET_MODE = "subTicketMode";

    /**
     * Field name constant for the sub-ticket IDs a move request names.
     */
    public static final String FIELD_SUB_TICKET_IDS = "subTicketIds";

    /**
     * Field name constant for the keys of the tickets a refused move names.
     */
    public static final String FIELD_TICKET_KEYS = "ticketKeys";

    /**
     * Field name constant for the fields a refused write still needs.
     */
    public static final String FIELD_MISSING_FIELDS = "missingFields";

    /**
     * Field name constant for the root cause analysis of an incident.
     */
    public static final String FIELD_RCA = "rca";

    /**
     * Field name constant for the lessons learned from an incident.
     */
    public static final String FIELD_LESSONS_LEARNED = "lessonsLearned";
}
