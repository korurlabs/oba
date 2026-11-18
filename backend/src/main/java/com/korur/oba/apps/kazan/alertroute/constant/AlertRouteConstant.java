package com.korur.oba.apps.kazan.alertroute.constant;

/**
 * Field names and validation rules of the alert route domain.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AlertRouteConstant {

    private AlertRouteConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Field name constant for the alert route ID.
     */
    public static final String FIELD_ROUTE_ID = "routeId";

    /**
     * Field name constant for the system key.
     */
    public static final String FIELD_SYSTEM_KEY = "systemKey";

    /**
     * Longest system key; the column and the check constraint use the same limit.
     */
    public static final int SYSTEM_KEY_MAX_LENGTH = 50;

    /**
     * Shape of a system key, the same as the check constraint on the column: the
     * value of the alert's system label after the client trims and lowercases it.
     */
    public static final String SYSTEM_KEY_PATTERN = "^[a-z0-9][a-z0-9-]{0,49}$";

    /**
     * Validation message for a system key that does not match {@link #SYSTEM_KEY_PATTERN}.
     */
    public static final String SYSTEM_KEY_PATTERN_MESSAGE =
            "System must be 1 to 50 lowercase letters, digits and hyphens, starting with a letter or digit";

    /**
     * Validation message for a missing system key.
     */
    public static final String SYSTEM_KEY_REQUIRED_MESSAGE = "System is required";

    /**
     * Validation message for a system key longer than {@link #SYSTEM_KEY_MAX_LENGTH}.
     */
    public static final String SYSTEM_KEY_SIZE_MESSAGE = "Length field size can not be more then 50";

    /**
     * Validation message for a missing board ID.
     */
    public static final String BOARD_ID_REQUIRED_MESSAGE = "Board is required";

    /**
     * Validation message for a missing default flag on update.
     */
    public static final String IS_DEFAULT_REQUIRED_MESSAGE = "Default flag is required";
}
