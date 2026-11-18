package com.korur.oba.apps.kazan.project.constant;

/**
 * Log message constants for project domain exceptions.
 * <p>
 * These constants provide consistent exception logging messages across
 * the project domain, used in validators and services when errors occur.
 */
public final class ProjectExceptionLogConstant {

    private ProjectExceptionLogConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Log message for when a project is not found.
     */
    public static final String PROJECT_NOT_FOUND = "Project not found: {}";

    /**
     * Log message for when a project key already exists on the board.
     */
    public static final String PROJECT_KEY_ALREADY_EXISTS = "Project key already exists: boardId={}, key={}";

    /**
     * Log message for when a project cannot be deleted.
     */
    public static final String PROJECT_CANNOT_DELETE = "Project cannot be deleted: projectId={}";
}
