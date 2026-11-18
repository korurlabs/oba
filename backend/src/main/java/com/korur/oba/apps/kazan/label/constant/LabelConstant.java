package com.korur.oba.apps.kazan.label.constant;

/**
 * Constants for label field names used in error messages and validation.
 * <p>
 * These constants provide consistent field naming across the label domain,
 * particularly useful for exception error value maps.
 */
public final class LabelConstant {

    private LabelConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Field name constant for label ID.
     */
    public static final String FIELD_LABEL_ID = "labelId";

    /**
     * Field name constant for board ID.
     */
    public static final String FIELD_BOARD_ID = "boardId";

    /**
     * Field name constant for label name.
     */
    public static final String FIELD_NAME = "name";

    /**
     * Field name constant for label color.
     */
    public static final String FIELD_COLOR = "color";
}
