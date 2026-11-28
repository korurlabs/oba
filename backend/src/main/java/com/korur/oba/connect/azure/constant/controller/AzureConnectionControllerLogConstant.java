package com.korur.oba.connect.azure.constant.controller;

/**
 * Log message constants for Azure connection controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AzureConnectionControllerLogConstant {

    private AzureConnectionControllerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String CONNECTION_LIST_REQUEST = "Received request to list Azure connections";
    public static final String CONNECTION_LIST_COMPLETED = "List Azure connections request completed";

    public static final String CONNECTION_GET_REQUEST = "Received request to get Azure connection";
    public static final String CONNECTION_GET_COMPLETED = "Get Azure connection request completed";

    public static final String CONNECTION_CREATE_REQUEST = "Received request to create Azure connection";
    public static final String CONNECTION_CREATE_COMPLETED = "Create Azure connection request completed";

    public static final String CONNECTION_UPDATE_REQUEST = "Received request to update Azure connection";
    public static final String CONNECTION_UPDATE_COMPLETED = "Update Azure connection request completed";

    public static final String CONNECTION_DELETE_REQUEST = "Received request to delete Azure connection";
    public static final String CONNECTION_DELETE_COMPLETED = "Delete Azure connection request completed";

    public static final String CONNECTION_VERIFY_REQUEST = "Received request to verify Azure connection";
    public static final String CONNECTION_VERIFY_COMPLETED = "Verify Azure connection request completed";
}
