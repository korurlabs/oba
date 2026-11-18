package com.korur.oba.apps.otak.icp.constant.controller;

/**
 * Log message constants for ICP controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class IcpControllerLogConstant {

    private IcpControllerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String ICP_LIST_REQUEST = "Received request to list ICPs";
    public static final String ICP_LIST_COMPLETED = "List ICPs request completed";

    public static final String ICP_CREATE_REQUEST = "Received request to create ICP";
    public static final String ICP_CREATE_COMPLETED = "Create ICP request completed";

    public static final String ICP_GET_REQUEST = "Received request to get ICP";
    public static final String ICP_GET_COMPLETED = "Get ICP request completed";

    public static final String ICP_UPDATE_REQUEST = "Received request to update ICP";
    public static final String ICP_UPDATE_COMPLETED = "Update ICP request completed";

    public static final String ICP_DELETE_REQUEST = "Received request to delete ICP";
    public static final String ICP_DELETE_COMPLETED = "Delete ICP request completed";
}
