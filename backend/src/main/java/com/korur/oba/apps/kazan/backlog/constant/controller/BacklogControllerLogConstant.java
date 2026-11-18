package com.korur.oba.apps.kazan.backlog.constant.controller;

/**
 * Log message constants for backlog controller operations.
 * <p>
 * These constants provide consistent logging at the controller layer,
 * tracking incoming requests and their completion status.
 */
public final class BacklogControllerLogConstant {

    private BacklogControllerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String BACKLOG_CREATE_REQUEST = "Received request to create backlog";
    public static final String BACKLOG_CREATE_COMPLETED = "Create backlog request completed";

    public static final String BACKLOG_GET_REQUEST = "Received request to get backlog";
    public static final String BACKLOG_GET_COMPLETED = "Get backlog request completed";

    public static final String BACKLOG_LIST_REQUEST = "Received request to list backlogs";
    public static final String BACKLOG_LIST_COMPLETED = "List backlogs request completed";

    public static final String BACKLOG_UPDATE_REQUEST = "Received request to update backlog";
    public static final String BACKLOG_UPDATE_COMPLETED = "Update backlog request completed";

    public static final String BACKLOG_DELETE_REQUEST = "Received request to delete backlog";
    public static final String BACKLOG_DELETE_COMPLETED = "Delete backlog request completed";
}
