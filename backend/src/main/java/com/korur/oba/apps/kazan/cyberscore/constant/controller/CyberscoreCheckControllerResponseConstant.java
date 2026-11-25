package com.korur.oba.apps.kazan.cyberscore.constant.controller;

/**
 * Response message constants for check controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class CyberscoreCheckControllerResponseConstant {

    private CyberscoreCheckControllerResponseConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String RESPONSE_CHECK_LIST_RETRIEVED = "Checks retrieved successfully";
    public static final String RESPONSE_CHECK_CREATED = "Check created successfully";
    public static final String RESPONSE_CHECK_UPDATED = "Check updated successfully";
    public static final String RESPONSE_CHECK_OUTCOME_UPDATED = "Check outcome updated successfully";
    public static final String RESPONSE_CHECK_STATUS_UPDATED = "Check status updated successfully";
    public static final String RESPONSE_CHECK_DELETED = "Check deleted successfully";
}
