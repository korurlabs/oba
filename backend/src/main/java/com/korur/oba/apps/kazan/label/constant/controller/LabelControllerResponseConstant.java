package com.korur.oba.apps.kazan.label.constant.controller;

/**
 * Response message constants for label controller operations.
 * <p>
 * These constants provide consistent success messages returned to clients
 * in API responses.
 */
public final class LabelControllerResponseConstant {

    private LabelControllerResponseConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String RESPONSE_LABEL_CREATED = "Label created successfully";
    public static final String RESPONSE_LABEL_UPDATED = "Label updated successfully";
    public static final String RESPONSE_LABEL_DELETED = "Label deleted successfully";
    public static final String RESPONSE_LABEL_RETRIEVED = "Label retrieved successfully";
    public static final String RESPONSE_LABEL_LIST_RETRIEVED = "Labels retrieved successfully";
}
