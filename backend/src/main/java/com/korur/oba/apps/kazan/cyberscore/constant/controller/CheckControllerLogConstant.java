package com.korur.oba.apps.kazan.cyberscore.constant.controller;

/**
 * Log message constants for check controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class CheckControllerLogConstant {

    private CheckControllerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String CHECK_LIST_REQUEST = "Received request to list checks";
    public static final String CHECK_LIST_COMPLETED = "List checks request completed";

    public static final String CHECK_CREATE_REQUEST = "Received request to create check";
    public static final String CHECK_CREATE_COMPLETED = "Create check request completed";

    public static final String CHECK_UPDATE_REQUEST = "Received request to update check";
    public static final String CHECK_UPDATE_COMPLETED = "Update check request completed";

    public static final String CHECK_OUTCOME_REQUEST = "Received request to set check outcome";
    public static final String CHECK_OUTCOME_COMPLETED = "Set check outcome request completed";

    public static final String CHECK_STATUS_REQUEST = "Received request to set check status";
    public static final String CHECK_STATUS_COMPLETED = "Set check status request completed";

    public static final String CHECK_DELETE_REQUEST = "Received request to delete check";
    public static final String CHECK_DELETE_COMPLETED = "Delete check request completed";
}
