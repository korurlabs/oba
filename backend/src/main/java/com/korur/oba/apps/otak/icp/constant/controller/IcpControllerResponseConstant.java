package com.korur.oba.apps.otak.icp.constant.controller;

/**
 * Response message constants for ICP controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class IcpControllerResponseConstant {

    private IcpControllerResponseConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String RESPONSE_ICP_LIST_RETRIEVED = "ICPs retrieved successfully";
    public static final String RESPONSE_ICP_CREATED = "ICP created successfully";
    public static final String RESPONSE_ICP_RETRIEVED = "ICP retrieved successfully";
    public static final String RESPONSE_ICP_UPDATED = "ICP updated successfully";
    public static final String RESPONSE_ICP_DELETED = "ICP deleted successfully";
}
