package com.korur.oba.dojo.security.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the current-user resolution, the RequiredUserAccess check, the API token filter and
 * the Kaya machine token filter. They carry token ids, never a token.
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
    public static final String RESOLVED_API_TOKEN_USER = "Resolved current user {} for {} token {}";
    public static final String API_TOKEN_USER_NOT_FOUND = "No user {} for {} token {}";

    // --- RequiredUserAccess check ---
    public static final String ACCESS_DENIED_UNANNOTATED =
            "RequiredUserAccess check failed: handler {} on path {} declares no RequiredUserAccess and the path is not listed";
    public static final String USER_ROLE_DENIED =
            "RequiredUserAccess check failed: user {} has role {}, {} is required on path {}";
    public static final String ROLE_CHECK_PASSED = "RequiredUserAccess check passed: user {} has role {}, {} is required";
    public static final String ACCESS_DENIED_UNANNOTATED_FOR_TOKEN =
            "RequiredUserAccess check failed: handler {} on {} {} declares no RequiredUserAccess and is not exempt for {} tokens";
    public static final String ACCESS_DENIED_AUTHENTICATION_UNSUPPORTED =
            "RequiredUserAccess check failed: handler {} on path {} was reached with an unsupported authentication {}";

    // --- API token filter ---
    public static final String API_TOKEN_MISSING = "Reserved token path {} {} called without a token of an accepted kind";
    public static final String API_TOKEN_ON_FOREIGN_PATH = "{} token refused on {} {}: the path is reserved for {} tokens";
    public static final String API_TOKEN_OUTSIDE_RESERVED_PATHS = "{} token refused on {} {}: outside the paths of its kind";
    public static final String API_TOKEN_REFUSED = "{} token refused on {} {}: {}";
    public static final String API_TOKEN_AUTHENTICATED = "{} token {} of user {} authenticated on {} {}";
    public static final String API_TOKEN_BODY_TOO_LARGE = "Body of {} {} with token {} exceeds {} bytes";
    public static final String API_TOKEN_ERROR_WRITE_FAILED = "Could not write the error response of {} {}";

    // --- Machine token filter ---
    public static final String MACHINE_TOKEN_MISSING = "Machine token path {} {} called without a bearer token";
    public static final String MACHINE_TOKEN_REFUSED = "Machine token refused on {} {}: {}";
    public static final String MACHINE_TOKEN_AUTHENTICATED = "Machine token {} of machine {} authenticated on {} {}";
    public static final String MACHINE_BODY_TOO_LARGE = "Body of {} {} from machine {} exceeds {} bytes";
    public static final String MACHINE_ERROR_WRITE_FAILED = "Could not write the error response of {} {}";
}
