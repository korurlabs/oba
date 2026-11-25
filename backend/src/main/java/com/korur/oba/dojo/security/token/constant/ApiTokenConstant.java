package com.korur.oba.dojo.security.token.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Values of the API tokens: their shape, lifetime, hashing and the request header that carries them.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class ApiTokenConstant {

    private ApiTokenConstant() {
    }

    // --- Token prefixes, one per kind; a prefix routes a bearer token to the token chain ---
    public static final String PREFIX_KAYA_MACHINE = "kyt_";
    public static final String PREFIX_AGENT = "oat_";

    // --- Token shape: the prefix and 32 random bytes in unpadded base64url (43 characters) ---
    public static final int TOKEN_RANDOM_BYTES = 32;
    public static final int TOKEN_BODY_LENGTH = 43;
    // The part kept in clear, so a person can tell tokens apart; the column holds 12 characters
    public static final int TOKEN_DISPLAY_PREFIX_LENGTH = 12;
    public static final String TOKEN_HASH_ALGORITHM = "SHA-256";

    // --- Lifetime ---
    public static final long KAYA_MACHINE_VALIDITY_DAYS = 365;
    public static final long AGENT_VALIDITY_DAYS = 365;

    // A sender that posts every few seconds would otherwise rewrite the token row on every request
    public static final long MARK_USED_INTERVAL_SECONDS = 60;


    // --- Field names of error values and log details ---
    public static final String FIELD_LIMIT_BYTES = "limitBytes";

    // --- Startup failures ---
    public static final String DUPLICATE_KIND_HANDLER = "Two API token kind handlers for kind ";
}
