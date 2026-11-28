package com.korur.oba.connect.azure.constant.controller;

/**
 * Response message constants for Azure connection controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AzureConnectionControllerResponseConstant {

    private AzureConnectionControllerResponseConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String RESPONSE_CONNECTION_LIST_RETRIEVED = "Azure connections retrieved successfully";
    public static final String RESPONSE_CONNECTION_RETRIEVED = "Azure connection retrieved successfully";
    public static final String RESPONSE_CONNECTION_CREATED = "Azure connection created successfully";
    public static final String RESPONSE_CONNECTION_UPDATED = "Azure connection updated successfully";
    public static final String RESPONSE_CONNECTION_DELETED = "Azure connection deleted successfully";
    public static final String RESPONSE_CONNECTION_VERIFIED = "Azure connection checked";
}
