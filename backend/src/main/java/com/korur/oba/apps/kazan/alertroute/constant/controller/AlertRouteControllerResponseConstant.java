package com.korur.oba.apps.kazan.alertroute.constant.controller;

/**
 * Response message constants for alert route controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AlertRouteControllerResponseConstant {

    private AlertRouteControllerResponseConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String RESPONSE_ALERT_ROUTE_CREATED = "Alert route created successfully";
    public static final String RESPONSE_ALERT_ROUTE_UPDATED = "Alert route updated successfully";
    public static final String RESPONSE_ALERT_ROUTE_DELETED = "Alert route deleted successfully";
    public static final String RESPONSE_ALERT_ROUTE_LIST_RETRIEVED = "Alert routes retrieved successfully";
}
