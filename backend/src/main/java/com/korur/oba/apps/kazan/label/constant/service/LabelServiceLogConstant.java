package com.korur.oba.apps.kazan.label.constant.service;

/**
 * Log message constants for label service operations.
 * <p>
 * These constants provide consistent logging at the service layer,
 * tracking business logic operations and their outcomes.
 */
public final class LabelServiceLogConstant {

    private LabelServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String LABEL_CREATED = "Label created: {}";
    public static final String LABEL_UPDATED = "Label updated: {}";
    public static final String LABEL_DELETED = "Label deleted: {}";
    public static final String LABEL_RESTORED = "Restored soft-deleted label: {}";
}
