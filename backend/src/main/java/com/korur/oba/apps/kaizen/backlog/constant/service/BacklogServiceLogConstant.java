package com.korur.oba.apps.kaizen.backlog.constant.service;

/**
 * Log message constants for backlog service operations.
 * <p>
 * These constants provide consistent logging at the service layer,
 * tracking business logic operations and their outcomes.
 */
public final class BacklogServiceLogConstant {

    private BacklogServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String BACKLOG_CREATED = "Backlog created: {}";
    public static final String BACKLOG_UPDATED = "Backlog updated: {}";
    public static final String BACKLOG_DELETED = "Backlog deleted: {}";
}
