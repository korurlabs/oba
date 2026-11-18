package com.korur.oba.dojo.security.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the current-user resolution, the RequiredUserAccess check and the Kaya machine
 * token filter.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class SecurityLogConstant {

    private SecurityLogConstant() {
    }

    // --- Current user ---
    public static final String NO_JWT_AUTHENTICATION = "No JWT authentication in the security context";
    public static final String JWT_SUBJECT_MISSING = "The JWT has no sub claim";
    public static final String RESOLVED_CURRENT_USER = "Resolved current user {} for keycloakId {}";

    // --- RequiredUserAccess check ---
    public static final String ACCESS_DENIED_UNANNOTATED =
            "RequiredUserAccess check failed: handler {} on path {} declares no RequiredUserAccess and the path is not listed";
    public static final String USER_ROLE_DENIED =
            "RequiredUserAccess check failed: user {} has role {}, {} is required on path {}";
    public static final String ROLE_CHECK_PASSED = "RequiredUserAccess check passed: user {} has role {}, {} is required";

    // --- Machine token filter ---
    public static final String MACHINE_TOKEN_MISSING = "Machine token path {} {} called without a bearer token";
    public static final String MACHINE_TOKEN_REFUSED = "Machine token refused on {} {}: {}";
    public static final String MACHINE_TOKEN_AUTHENTICATED = "Machine token {} of machine {} authenticated on {} {}";
    public static final String MACHINE_BODY_TOO_LARGE = "Body of {} {} from machine {} exceeds {} bytes";
    public static final String MACHINE_ERROR_WRITE_FAILED = "Could not write the error response of {} {}";
}
