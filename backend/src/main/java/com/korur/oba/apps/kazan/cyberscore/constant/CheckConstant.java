package com.korur.oba.apps.kazan.cyberscore.constant;

/**
 * Field names, limits and validation messages of the cyberscore checks.
 * The limits repeat the column lengths and check constraints of kazan.checks.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class CheckConstant {

    private CheckConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Field name constant for the check ID.
     */
    public static final String FIELD_CHECK_ID = "checkId";

    /**
     * Field name constant for the check name.
     */
    public static final String FIELD_NAME = "name";

    /**
     * Unique constraint on organization and name, which backs the name check
     * against a concurrent write.
     */
    public static final String NAME_UNIQUE_CONSTRAINT = "checks_organization_id_name_uk";

    /**
     * Longest check name.
     */
    public static final int NAME_MAX_LENGTH = 255;

    /**
     * Longest description and expected state.
     */
    public static final int TEXT_MAX_LENGTH = 5000;

    /**
     * Lowest weight of a check.
     */
    public static final int MIN_WEIGHT = 1;

    /**
     * Highest weight of a check.
     */
    public static final int MAX_WEIGHT = 100;

    /**
     * Lowest sort order within a category.
     */
    public static final int MIN_SORT_ORDER = 0;

    /**
     * Highest sort order within a category.
     */
    public static final int MAX_SORT_ORDER = 9999;

    /**
     * Sort order of a check whose request names none. A String, because a
     * mapping default takes only a compile-time String.
     */
    public static final String DEFAULT_SORT_ORDER = "0";

    public static final String NAME_REQUIRED_MESSAGE = "Name is required";
    public static final String NAME_SIZE_MESSAGE = "Name must not exceed 255 characters";
    public static final String DESCRIPTION_SIZE_MESSAGE = "Description must not exceed 5000 characters";
    public static final String EXPECTED_REQUIRED_MESSAGE = "Expected state is required";
    public static final String EXPECTED_SIZE_MESSAGE = "Expected state must not exceed 5000 characters";
    public static final String WEIGHT_REQUIRED_MESSAGE = "Weight is required";
    public static final String WEIGHT_RANGE_MESSAGE = "Weight must be between 1 and 100";
    public static final String SORT_ORDER_RANGE_MESSAGE = "Sort order must be between 0 and 9999";
    public static final String OUTCOME_REQUIRED_MESSAGE = "Outcome is required";
    public static final String STATUS_REQUIRED_MESSAGE = "Status is required";
}
