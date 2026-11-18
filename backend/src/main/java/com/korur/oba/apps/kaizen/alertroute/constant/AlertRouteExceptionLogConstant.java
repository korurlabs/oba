package com.korur.oba.apps.kaizen.alertroute.constant;

/**
 * Log message constants for alert route domain exceptions.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AlertRouteExceptionLogConstant {

    private AlertRouteExceptionLogConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Log message for when an alert route is not found.
     */
    public static final String ALERT_ROUTE_NOT_FOUND = "Alert route not found: {}";

    /**
     * Log message for when a system already has an alert route.
     */
    public static final String ALERT_ROUTE_SYSTEM_DUPLICATE = "Alert route already exists for system: {}";

    /**
     * Log message for when the organization bound to the Alertmanager token does not exist.
     */
    public static final String ORGANIZATION_NOT_FOUND = "Alertmanager notification refused: the token's organization {} does not exist";

    /**
     * Log message for when the organization bound to the Alertmanager token is not ACTIVE.
     */
    public static final String ORGANIZATION_DISABLED = "Alertmanager notification refused: the token's organization {} has status {}";
}
