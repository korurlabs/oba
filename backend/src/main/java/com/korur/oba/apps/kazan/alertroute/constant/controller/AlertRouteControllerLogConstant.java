package com.korur.oba.apps.kazan.alertroute.constant.controller;

/**
 * Log message constants for alert route controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AlertRouteControllerLogConstant {

    private AlertRouteControllerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String ALERT_ROUTE_CREATE_REQUEST = "Received request to create alert route";
    public static final String ALERT_ROUTE_CREATE_COMPLETED = "Create alert route request completed";

    public static final String ALERT_ROUTE_LIST_REQUEST = "Received request to list alert routes";
    public static final String ALERT_ROUTE_LIST_COMPLETED = "List alert routes request completed";

    public static final String ALERT_ROUTE_UPDATE_REQUEST = "Received request to update alert route";
    public static final String ALERT_ROUTE_UPDATE_COMPLETED = "Update alert route request completed";

    public static final String ALERT_ROUTE_DELETE_REQUEST = "Received request to delete alert route";
    public static final String ALERT_ROUTE_DELETE_COMPLETED = "Delete alert route request completed";
}
