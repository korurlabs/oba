package com.korur.oba.apps.kazan.ticket.constant.listener;

/**
 * Log message constants of the incident push. No message carries the ticket title.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class IncidentPushListenerLogConstant {

    private IncidentPushListenerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String INCIDENT_PUSH_REQUESTED = "Incident push requested: ticket={}, board={}, priority={}, recipients={}, skippedWithoutKazan={}";
    public static final String INCIDENT_PUSH_NO_RECIPIENTS = "Incident push skipped, the board has no members: ticket={}, board={}";
    public static final String INCIDENT_PUSH_NO_RECIPIENTS_WITH_KAZAN = "Incident push skipped, no board member has Kazan access: ticket={}, board={}, members={}";
    public static final String INCIDENT_PUSH_FAILED = "Incident push failed, the incident stays open without an alert: ticket={}, board={}";
}
