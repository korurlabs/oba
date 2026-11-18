package com.korur.oba.apps.otak.event.constant;

/**
 * Log message constants for event domain exceptions.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class EventExceptionLogConstant {

    private EventExceptionLogConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Log message for an event that the organization does not have, missing or of another organization.
     */
    public static final String EVENT_NOT_FOUND = "Event not found: id={}, organizationId={}";

    /**
     * Log message for an end date before the start date.
     */
    public static final String EVENT_DATE_RANGE_INVALID = "Event dates refused, end before start: start={}, end={}";

    /**
     * Log message for a name another event of the organization already has, case ignored.
     */
    public static final String EVENT_NAME_TAKEN = "Event name refused, already taken: organizationId={}, name={}";

    /**
     * Log message for a period filter sent without the viewer's date.
     */
    public static final String EVENT_PERIOD_WITHOUT_TODAY = "Event list refused, period without today: period={}";
}
