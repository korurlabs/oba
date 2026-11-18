package com.korur.oba.apps.kalkan.permission.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the page permission controller.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class KalkanPagePermissionControllerLogConstant {

    private KalkanPagePermissionControllerLogConstant() {
    }

    public static final String PERMISSION_LIST_REQUEST = "Received request from user {} to list the permissions of page {} in organization {}";
    public static final String PERMISSION_LIST_COMPLETED = "Listed {} permissions of page {} in organization {}";
    public static final String PERMISSION_REPLACE_REQUEST = "Received request from user {} to replace the permissions of page {} in organization {} with {} grants";
    public static final String PERMISSION_REPLACE_COMPLETED = "Replaced the permissions of page {} in organization {}: {} live grants";
}
