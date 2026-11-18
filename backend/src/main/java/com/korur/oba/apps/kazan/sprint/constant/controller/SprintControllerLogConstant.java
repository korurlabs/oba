package com.korur.oba.apps.kazan.sprint.constant.controller;

/**
 * Log message constants for sprint controller operations.
 * <p>
 * These constants provide consistent logging at the controller layer,
 * tracking incoming requests and their completion status.
 */
public final class SprintControllerLogConstant {

    private SprintControllerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String SPRINT_CREATE_REQUEST = "Received request to create sprint";
    public static final String SPRINT_CREATE_COMPLETED = "Create sprint request completed";

    public static final String SPRINT_GET_REQUEST = "Received request to get sprint";
    public static final String SPRINT_GET_COMPLETED = "Get sprint request completed";

    public static final String SPRINT_LIST_REQUEST = "Received request to list sprints";
    public static final String SPRINT_LIST_COMPLETED = "List sprints request completed";

    public static final String SPRINT_CHILD_BOARDS_LIST_REQUEST = "Received request to list sprint child boards";
    public static final String SPRINT_CHILD_BOARDS_LIST_COMPLETED = "List sprint child boards request completed";

    public static final String SPRINT_UPDATE_REQUEST = "Received request to update sprint";
    public static final String SPRINT_UPDATE_COMPLETED = "Update sprint request completed";

    public static final String SPRINT_DELETE_REQUEST = "Received request to delete sprint";
    public static final String SPRINT_DELETE_COMPLETED = "Delete sprint request completed";

    public static final String SPRINT_START_PREVIEW_REQUEST = "Received request to preview sprint start";
    public static final String SPRINT_START_PREVIEW_COMPLETED = "Preview sprint start request completed";
    public static final String SPRINT_START_REQUEST = "Received request to start sprint. TakeOver: {}";
    public static final String SPRINT_START_COMPLETED = "Start sprint request completed";

    public static final String SPRINT_COMPLETE_REQUEST = "Received request to complete sprint";
    public static final String SPRINT_COMPLETE_COMPLETED = "Complete sprint request completed";

    public static final String SPRINT_LINK_REQUEST = "Received request to link sprint";
    public static final String SPRINT_LINK_COMPLETED = "Link sprint request completed";

    public static final String SPRINT_BULK_LINK_REQUEST = "Received request to bulk link sprint";
    public static final String SPRINT_BULK_LINK_COMPLETED = "Bulk link sprint request completed";

    public static final String SPRINT_UNLINK_REQUEST = "Received request to unlink sprint";
    public static final String SPRINT_UNLINK_COMPLETED = "Unlink sprint request completed";

    public static final String SPRINT_RESTORE_REQUEST = "Received request to restore sprint";
    public static final String SPRINT_RESTORE_COMPLETED = "Restore sprint request completed";

    public static final String SPRINT_SERIES_UPDATE_REQUEST = "Received request to update sprint series";
    public static final String SPRINT_SERIES_UPDATE_COMPLETED = "Update sprint series request completed";
}
