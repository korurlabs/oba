package com.korur.oba.apps.kaya.identity.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Field names, JWT claim names, limits, validation messages and error value keys of the identity domain.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class IdentityConstant {

    private IdentityConstant() {
    }

    // --- Error value keys ---
    public static final String FIELD_HEADER = "header";
    public static final String FIELD_ORGANIZATION_ID = "organizationId";
    public static final String FIELD_ORGANIZATION_STATUS = "organizationStatus";
    public static final String FIELD_MEMBER_STATUS = "memberStatus";
    public static final String FIELD_USER_ID = "userId";
    public static final String FIELD_USER_STATUS = "userStatus";
    public static final String FIELD_KEYCLOAK_ID = "keycloakId";
    public static final String FIELD_REQUIRED_ROLE = "requiredRole";
    public static final String FIELD_ACTUAL_ROLE = "actualRole";

    // --- Entity fields (specifications and sorting) ---
    public static final String FIELD_USER = "user";
}
