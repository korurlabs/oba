package com.korur.oba.apps.kaizen.backlog.constant;

/**
 * Constants for backlog field names used in error messages and validation.
 * <p>
 * These constants provide consistent field naming across the backlog domain,
 * particularly useful for exception error value maps.
 */
public final class BacklogConstant {

    private BacklogConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Field name constant for backlog ID.
     */
    public static final String FIELD_BACKLOG_ID = "backlogId";

    /**
     * Field name constant for board ID.
     */
    public static final String FIELD_BOARD_ID = "boardId";

    /**
     * Field name constant for backlog name.
     */
    public static final String FIELD_NAME = "name";
}
