package com.korur.oba.apps.kaizen.label.constant;

/**
 * Log message constants for label domain exceptions.
 * <p>
 * These constants provide consistent exception logging messages across
 * the label domain, used in validators and services when errors occur.
 */
public final class LabelExceptionLogConstant {

    private LabelExceptionLogConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Log message for when a label is not found.
     */
    public static final String LABEL_NOT_FOUND = "Label not found: {}";

    /**
     * Log message for when a label name already exists on the board.
     */
    public static final String LABEL_NAME_ALREADY_EXISTS = "Label name already exists: boardId={}, name={}";

    /**
     * Log message for when a label cannot be deleted.
     */
    public static final String LABEL_CANNOT_DELETE = "Label cannot be deleted: labelId={}";
}
