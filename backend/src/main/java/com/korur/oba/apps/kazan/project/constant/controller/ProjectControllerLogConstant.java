package com.korur.oba.apps.kazan.project.constant.controller;

/**
 * Log message constants for project controller operations.
 * <p>
 * These constants provide consistent logging at the controller layer,
 * tracking incoming requests and their completion status.
 */
public final class ProjectControllerLogConstant {

    private ProjectControllerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String PROJECT_CREATE_REQUEST = "Received request to create project";
    public static final String PROJECT_CREATE_COMPLETED = "Create project request completed";

    public static final String PROJECT_GET_REQUEST = "Received request to get project";
    public static final String PROJECT_GET_COMPLETED = "Get project request completed";

    public static final String PROJECT_LIST_REQUEST = "Received request to list projects";
    public static final String PROJECT_LIST_COMPLETED = "List projects request completed";

    public static final String PROJECT_UPDATE_REQUEST = "Received request to update project";
    public static final String PROJECT_UPDATE_COMPLETED = "Update project request completed";

    public static final String PROJECT_DELETE_REQUEST = "Received request to delete project";
    public static final String PROJECT_DELETE_COMPLETED = "Delete project request completed";
}
