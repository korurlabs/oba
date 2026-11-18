package com.korur.oba.apps.kazan.project.constant;

/**
 * Constants for project field names used in error messages and validation.
 * <p>
 * These constants provide consistent field naming across the project domain,
 * particularly useful for exception error value maps.
 */
public final class ProjectConstant {

    private ProjectConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Field name constant for project ID.
     */
    public static final String FIELD_PROJECT_ID = "projectId";

    /**
     * Field name constant for board ID.
     */
    public static final String FIELD_BOARD_ID = "boardId";

    /**
     * Field name constant for project name.
     */
    public static final String FIELD_NAME = "name";

    /**
     * Field name constant for project key.
     */
    public static final String FIELD_KEY = "key";

    /**
     * Field name constant for project status.
     */
    public static final String FIELD_STATUS = "status";
}
