package com.korur.oba.apps.kaya.identity.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the user services.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class UserServiceLogConstant {

    private UserServiceLogConstant() {
    }

    public static final String USER_NOT_FOUND = "No user is provisioned for keycloakId {}";
    public static final String MEMBER_USER_NOT_FOUND = "User {} is not a member of organization {}";

    // --- Access ---
    public static final String ADMIN_OR_SELF_REQUIRED = "Refused: user {} with role {} is neither ADMIN nor user {}";

    // --- Audit ---
    public static final String AUDIT_RECORDED = "Audit: {} on user {} of organization {} by {} {}";
}
