package com.korur.oba.apps.kaizen.project.constant.controller;

/**
 * Response message constants for project controller operations.
 * <p>
 * These constants provide consistent success messages returned to clients
 * in API responses.
 */
public final class ProjectControllerResponseConstant {

    private ProjectControllerResponseConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String RESPONSE_PROJECT_CREATED = "Project created successfully";
    public static final String RESPONSE_PROJECT_UPDATED = "Project updated successfully";
    public static final String RESPONSE_PROJECT_DELETED = "Project deleted successfully";
    public static final String RESPONSE_PROJECT_RETRIEVED = "Project retrieved successfully";
    public static final String RESPONSE_PROJECT_LIST_RETRIEVED = "Projects retrieved successfully";
}
