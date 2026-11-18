package com.korur.oba.apps.kaizen.alertroute.constant.service;

/**
 * Log message constants for alert route service operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AlertRouteServiceLogConstant {

    private AlertRouteServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String ALERT_ROUTE_CREATED = "Alert route created: id={}, system={}, boardId={}, isDefault={}";
    public static final String ALERT_ROUTE_UPDATED = "Alert route updated: id={}, system={}, boardId={}, isDefault={}";
    public static final String ALERT_ROUTE_DELETED = "Alert route deleted: id={}, system={}, boardId={}";
    public static final String ALERT_ROUTE_DEFAULT_CLEARED = "Previous default alert route cleared before saving a new default";
}
