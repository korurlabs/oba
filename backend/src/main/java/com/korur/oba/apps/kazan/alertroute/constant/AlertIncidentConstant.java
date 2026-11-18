package com.korur.oba.apps.kazan.alertroute.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Routing rules, the ticket cap and the ticket texts that Kazan derives from an
 * Alertmanager alert. The Alertmanager payload keys stay in
 * {@link com.korur.oba.connect.alertmanager.constant.AlertmanagerConstant}.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AlertIncidentConstant {

    private AlertIncidentConstant() {
        // Private constructor to prevent instantiation
    }

    // Names the system an alert comes from; selects its board in the alert map.
    public static final String LABEL_SYSTEM = "system";
    // Stands in for a missing system label in log lines.
    public static final String SYSTEM_NONE = "none";

    // An alert storm creates at most this many tickets per notification.
    public static final int MAX_TICKETS_PER_NOTIFICATION = 10;

    // Match the limits of TicketCreateRequest.
    public static final int TITLE_MAX_LENGTH = 255;
    public static final int DESCRIPTION_MAX_LENGTH = 10000;

    public static final String TITLE_SEPARATOR = ": ";
    public static final String TITLE_FALLBACK_PREFIX = "Alert ";
    public static final String LINE_BREAK = "\n";
    public static final String SECTION_BREAK = "\n\n";
    public static final String LABEL_SEPARATOR = ": ";
    public static final String DESCRIPTION_STARTED_AT_PREFIX = "Started at ";
    public static final String DESCRIPTION_SOURCE_PREFIX = "Source: ";
    public static final String DESCRIPTION_ALERTMANAGER_PREFIX = "Alertmanager: ";
}
