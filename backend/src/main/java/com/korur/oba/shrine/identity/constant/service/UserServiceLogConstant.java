package com.korur.oba.shrine.identity.constant.service;

/**
 * Purpose:
 * Holds log message constants used by user domain services.
 *
 * Bigger picture:
 * Centralizes all service-layer log strings for the identity domain,
 * covering UserService, UserProfileService, and UserProfileQueryService.
 *
 * @docType STANDARD
 * @author Claude
 * @since 2026-03-04
 * @updated 2026-03-04
 */
public final class UserServiceLogConstant {

    private UserServiceLogConstant() {
    }

    public static final String USER_JWT_CHECK_START = "Starting JWT user bootstrap";
    public static final String USER_UPDATED = "User updated with database ID: {}";
    public static final String USER_DISABLED = "User disabled with database ID: {}";
    public static final String USER_GET_CURRENT_USER_START = "Starting to retrieve current user from JWT";
    public static final String USER_CREATED_FROM_JWT = "User created from JWT with keycloakId={}";
    public static final String USER_ACTIVATED = "User activated after onboarding with database ID: {}";
    public static final String USER_DEFAULT_ORG_SET = "Default organization set for user: {} to organization: {}";
    public static final String USER_ORG_DELETION_APPLIED = "Organization deletion applied to user: {} for organization: {}";

    // Audit constants
    public static final String AUDIT_ENTITY_TYPE = "User";
    public static final String AUDIT_ACTION_USER_DISABLED = "USER_DISABLED";
}
