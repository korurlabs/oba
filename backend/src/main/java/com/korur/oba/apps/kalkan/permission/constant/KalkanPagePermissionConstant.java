package com.korur.oba.apps.kalkan.permission.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Field names and error value keys of the permission domain.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class KalkanPagePermissionConstant {

    private KalkanPagePermissionConstant() {
    }

    // --- Error value keys ---
    public static final String FIELD_USER_ID = "userId";
    public static final String FIELD_GROUP_ID = "groupId";
    public static final String FIELD_LEVEL = "level";
    public static final String FIELD_ROLE = "role";
    public static final String FIELD_REASON = "reason";

    // --- Error value reasons ---
    public static final String REASON_NO_SUBJECT = "NO_SUBJECT";
    public static final String REASON_GROUPS_NOT_SUPPORTED = "GROUPS_NOT_SUPPORTED";
    public static final String REASON_DUPLICATE_SUBJECT = "DUPLICATE_SUBJECT";
    public static final String REASON_NOT_ACTIVE_MEMBER = "NOT_ACTIVE_MEMBER";

    // --- Display ---
    public static final String NAME_SEPARATOR = " ";
}
