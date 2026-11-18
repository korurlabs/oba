package com.korur.oba.apps.kaizen.label.constant.controller;

/**
 * Log message constants for label controller operations.
 * <p>
 * These constants provide consistent logging at the controller layer,
 * tracking incoming requests and their completion status.
 */
public final class LabelControllerLogConstant {

    private LabelControllerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String LABEL_CREATE_REQUEST = "Received request to create label";
    public static final String LABEL_CREATE_COMPLETED = "Create label request completed";

    public static final String LABEL_GET_REQUEST = "Received request to get label";
    public static final String LABEL_GET_COMPLETED = "Get label request completed";

    public static final String LABEL_LIST_REQUEST = "Received request to list labels";
    public static final String LABEL_LIST_COMPLETED = "List labels request completed";

    public static final String LABEL_UPDATE_REQUEST = "Received request to update label";
    public static final String LABEL_UPDATE_COMPLETED = "Update label request completed";

    public static final String LABEL_DELETE_REQUEST = "Received request to delete label";
    public static final String LABEL_DELETE_COMPLETED = "Delete label request completed";
}
