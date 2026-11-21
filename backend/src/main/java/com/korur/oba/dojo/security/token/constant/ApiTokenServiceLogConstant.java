package com.korur.oba.dojo.security.token.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the API token services. They carry token ids and prefixes, never a token.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class ApiTokenServiceLogConstant {

    private ApiTokenServiceLogConstant() {
    }

    // --- Lifecycle ---
    public static final String TOKEN_ISSUED =
            "Issued {} token {} for subject {}, owner {}, organization {}; revoked token {}";
    public static final String TOKEN_REVOKED = "Revoked {} token {} of subject {}";
    public static final String TOKEN_NONE_TO_REVOKE = "No live {} token to revoke for subject {}";
    public static final String MEMBER_TOKENS_REVOKED = "Revoked {} live tokens of user {} in organization {}";
    public static final String TOKEN_USED = "Recorded the use of token {}";

    // --- Authentication refusals; every one answers the same 401 of the kind ---
    public static final String TOKEN_UNKNOWN_KIND = "API token refused: the value carries no known kind prefix";
    public static final String TOKEN_MALFORMED = "API token refused: the value does not have the shape of a {} token";
    public static final String TOKEN_UNKNOWN = "API token refused: no {} token has this hash";
    public static final String TOKEN_KIND_MISMATCH = "API token refused: token {} is of kind {}, its prefix claims {}";
    public static final String TOKEN_REVOKED_REFUSED = "API token refused: {} token {} was revoked at {}";
    public static final String TOKEN_EXPIRED = "API token refused: {} token {} expired at {}";
}
