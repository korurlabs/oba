package com.korur.oba.apps.kaizen.alertroute.constant.service;

/**
 * Log messages of Kaizen's handling of Alertmanager notifications: routing,
 * the ticket cap and the outcome of each notification.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AlertmanagerIncidentServiceLogConstant {

    private AlertmanagerIncidentServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String BOARD_NOT_CONFIGURED = "Alertmanager notification refused: no alert route and no default board match the alert {} of system {}";
    public static final String ALERT_SYSTEM_NOT_ROUTED = "Alert {} of system {} has no alert route, sent to the default board";
    public static final String ALERT_NOT_FIRING = "Alert {} has status {}, ignored";
    public static final String ALERT_TICKET_CAP_REACHED = "Alert {} on board {} ignored: the notification already created {} tickets";
    public static final String SYSTEM_ACTOR_MISSING = "Alertmanager notification refused: SYSTEM_ACTOR_MISSING, no {} system actor exists";
    public static final String ALERTS_PROCESSED = "Alertmanager notification: created {}, ignored {}";
}
