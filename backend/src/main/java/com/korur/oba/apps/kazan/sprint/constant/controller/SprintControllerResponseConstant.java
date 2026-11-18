package com.korur.oba.apps.kazan.sprint.constant.controller;

/**
 * Response message constants for sprint controller operations.
 * <p>
 * These constants provide consistent success messages returned to clients
 * in API responses.
 */
public final class SprintControllerResponseConstant {

    private SprintControllerResponseConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String RESPONSE_SPRINT_CREATED = "Sprint created successfully";
    public static final String RESPONSE_SPRINT_UPDATED = "Sprint updated successfully";
    public static final String RESPONSE_SPRINT_DELETED = "Sprint deleted successfully";
    public static final String RESPONSE_SPRINT_RETRIEVED = "Sprint retrieved successfully";
    public static final String RESPONSE_SPRINT_LIST_RETRIEVED = "Sprints retrieved successfully";
    public static final String RESPONSE_SPRINT_CHILD_BOARDS_RETRIEVED = "Sprint child boards retrieved successfully";
    public static final String RESPONSE_SPRINT_START_PREVIEW_RETRIEVED = "Sprint start preview retrieved successfully";
    public static final String RESPONSE_SPRINT_STARTED = "Sprint started successfully";
    public static final String RESPONSE_SPRINT_TAKEN_OVER = "Active sprints closed and sprint started successfully";
    public static final String RESPONSE_SPRINT_COMPLETED = "Sprint completed successfully";
    public static final String RESPONSE_SPRINT_LINKED = "Sprint linked successfully";
    public static final String RESPONSE_SPRINT_BULK_LINKED = "Sprint bulk linked successfully";
    public static final String RESPONSE_SPRINT_UNLINKED = "Sprint unlinked successfully";
    public static final String RESPONSE_SPRINT_RESTORED = "Sprint restored successfully";
    public static final String RESPONSE_SPRINT_SERIES_UPDATED = "Sprint series updated successfully";

}
