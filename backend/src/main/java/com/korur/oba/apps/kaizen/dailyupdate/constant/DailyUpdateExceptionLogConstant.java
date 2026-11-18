package com.korur.oba.apps.kaizen.dailyupdate.constant;

/**
 * Log message constants for daily update domain exceptions.
 */
public final class DailyUpdateExceptionLogConstant {

    private DailyUpdateExceptionLogConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Log message for when an update is not found or not visible to the user.
     */
    public static final String DAILY_UPDATE_NOT_FOUND = "Daily update not found: {}";

    /**
     * Log message for when a request lists the same ticket twice.
     */
    public static final String DAILY_UPDATE_DUPLICATE_TICKET = "Daily update lists a ticket twice: ticketId={}";

    /**
     * Log message for when a user edits or deletes another user's update.
     */
    public static final String DAILY_UPDATE_NOT_AUTHOR = "User is not the author of daily update: dailyUpdateId={}, userId={}";

    /**
     * Log message for when a ticket is missing or on a board the user is not a member of.
     */
    public static final String DAILY_UPDATE_TICKETS_NOT_ACCESSIBLE = "Daily update tickets not found or not accessible: {}";

    /**
     * Log message for when the list window is inverted.
     */
    public static final String DAILY_UPDATE_FROM_AFTER_TO = "Daily update from date after to date: from={}, to={}";

    /**
     * Log message for when a request breaks a size or range limit.
     */
    public static final String DAILY_UPDATE_LIMIT_EXCEEDED = "Daily update exceeds a limit: field={}, limit={}";

    /**
     * Log message for when the not-posted list is asked for a day after today.
     */
    public static final String DAILY_UPDATE_REPORT_DATE_IN_FUTURE = "Daily update report date is in the future: date={}, today={}";

    /**
     * Log message for when a member tries to nudge themselves.
     */
    public static final String DAILY_UPDATE_NUDGE_SELF = "Daily update nudge to oneself refused: organizationId={}, userId={}";

    /**
     * Log message for when the nudged user is not a member expected to post,
     * or has no Kaizen access in the organization.
     */
    public static final String DAILY_UPDATE_NUDGE_RECIPIENT_NOT_FOUND = "Daily update nudge recipient is not an expected poster with Kaizen access: organizationId={}, recipientId={}";

    /**
     * Log message for when the nudged member already posted that day.
     */
    public static final String DAILY_UPDATE_NUDGE_RECIPIENT_POSTED = "Daily update nudge recipient already posted: recipientId={}, day={}";

    /**
     * Log message for when the member was already nudged that day.
     */
    public static final String DAILY_UPDATE_NUDGE_ALREADY_SENT = "Daily update nudge already sent: organizationId={}, recipientId={}, day={}";

    /**
     * Log message for when a concurrent nudge took the day's row first.
     */
    public static final String DAILY_UPDATE_NUDGE_RACE_LOST = "Daily update nudge lost to a concurrent nudge: organizationId={}, recipientId={}, day={}, cause={}";

    /**
     * Log message for when the nudge push could not be handed to the push dispatch.
     */
    public static final String DAILY_UPDATE_NUDGE_PUSH_FAILED = "Daily update nudge push failed: organizationId={}, nudgeId={}, recipientId={}";
}
