package com.korur.oba.shrine.identity.constant;

/**
 * Log messages used when user-related exceptions are raised.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class UserExceptionLogConstant {

    private UserExceptionLogConstant() {
    }

    public static final String USER_EMAIL_ALREADY_EXISTS = "Validation failed: email already exists, email={}";
    public static final String USER_EMAIL_TAKEN_BY_OTHER = "Validation failed: email already used by another user, email={}, userId={}";
    public static final String USER_STATUS_NOT_ACTIVE = "Validation failed: user is not active, userId={}";
    public static final String USER_NOT_MEMBER_OF_ORGANIZATION = "Validation failed: user is not a member of the organization, userId={}, organizationId={}";
    public static final String USER_KEYCLOAK_ID_DELETED = "Validation failed: Keycloak ID belongs to a deleted user, keycloakId={}";

    // --- Organization access refusals of UserAuthorizationValidator; never log the raw header value ---
    public static final String ACCESS_DENIED_NOT_PROVISIONED = "Access denied on {}: no user is provisioned for keycloakId={}";
    public static final String ACCESS_DENIED_USER_NOT_FOUND = "Access denied on {}: no user {} exists for the API token";
    public static final String ACCESS_DENIED_TOKEN_ORGANIZATION_MISMATCH =
            "Access denied on {}: the request names organization {}, the API token acts in {}";
    public static final String ACCESS_DENIED_USER_NOT_ACTIVE = "Access denied on {}: user is not active, userId={}, status={}";
    public static final String ACCESS_DENIED_ORGANIZATION_ID_MISSING = "Access denied on {}: the request names no organization";
    public static final String ACCESS_DENIED_HEADER_INVALID = "Access denied on {}: the X-Organization-ID header is not a UUID";
    public static final String ACCESS_DENIED_PATH_VARIABLE_INVALID = "Access denied on {}: the organizationId path variable is not a UUID";
    public static final String ACCESS_DENIED_ORGANIZATION_ID_MISMATCH =
            "Access denied on {}: the X-Organization-ID header differs from the path organization {}";
    public static final String ACCESS_DENIED_NOT_MEMBER = "Access denied on {}: user is not a member, userId={}, organizationId={}";
    public static final String ACCESS_DENIED_MEMBERSHIP_DISABLED =
            "Access denied on {}: membership is not active, userId={}, organizationId={}, status={}";
    public static final String ACCESS_DENIED_ORGANIZATION_DISABLED =
            "Access denied on {}: organization is not active, userId={}, organizationId={}, status={}";
    public static final String ACCESS_DENIED_APP_UNKNOWN =
            "Access denied on {}: handler package {} names no app, userId={}, organizationId={}";
    public static final String ACCESS_DENIED_NO_APP_ACCESS =
            "Access denied on {}: no access to the app, userId={}, organizationId={}, app={}, role={}";
}
