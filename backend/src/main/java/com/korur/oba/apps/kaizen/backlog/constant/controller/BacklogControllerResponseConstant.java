package com.korur.oba.apps.kaizen.backlog.constant.controller;

/**
 * Response message constants for backlog controller operations.
 * <p>
 * These constants provide consistent success messages returned to clients
 * in API responses.
 */
public final class BacklogControllerResponseConstant {

    private BacklogControllerResponseConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String RESPONSE_BACKLOG_CREATED = "Backlog created successfully";
    public static final String RESPONSE_BACKLOG_UPDATED = "Backlog updated successfully";
    public static final String RESPONSE_BACKLOG_DELETED = "Backlog deleted successfully";
    public static final String RESPONSE_BACKLOG_RETRIEVED = "Backlog retrieved successfully";
    public static final String RESPONSE_BACKLOG_LIST_RETRIEVED = "Backlogs retrieved successfully";
}
