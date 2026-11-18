package com.korur.oba.apps.kalkan.permission.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the permission and access services, including the refusal lines.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class KalkanPagePermissionServiceLogConstant {

    private KalkanPagePermissionServiceLogConstant() {
    }

    // --- Access ---
    public static final String PAGE_HIDDEN = "User {} holds no level on root {} of page {} in organization {}";
    public static final String LEVEL_TOO_LOW = "User {} holds {} on root {} of page {} in organization {}, {} needed";
    public static final String ROOT_CREATE_REFUSED = "User {} with role {} may not create a root page in organization {}";

    // --- Reads ---
    public static final String LISTING_PERMISSIONS = "Listing the permissions of page {} in organization {}";

    // --- Writes ---
    public static final String PERMISSIONS_REPLACED = "Replaced the permissions of root {} in organization {} by user {}: {} revoked, {} granted";
    public static final String CREATOR_GRANTED = "Granted MANAGE on new root {} to its creator {}";
    public static final String PERMISSION_ON_CHILD = "User {} tried to set permissions on non-root page {} in organization {}";
    public static final String INVALID_SUBJECT = "Refused a grant on root {} in organization {}: subject {} {}";
    public static final String GUEST_ABOVE_VIEW = "Refused {} for guest {} on root {} in organization {}";
}
