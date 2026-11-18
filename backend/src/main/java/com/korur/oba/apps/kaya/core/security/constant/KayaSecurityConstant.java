package com.korur.oba.apps.kaya.core.security.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Security values of the Kaya app: its machine-token paths and body cap.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class KayaSecurityConstant {

    private KayaSecurityConstant() {
    }

    // Paths the recorder calls with a machine token to record sessions; the only machine paths open
    // to a token whose user has no Kaya app
    public static final String MACHINE_TOKEN_INGEST_PATH = "/kaya/ingest/**";

    // Paths the CLI calls with a machine token to read and change agent config; each needs the Kaya app
    public static final String MACHINE_TOKEN_DELIVERY_PATH = "/kaya/delivery/**";

    // Paths the recorder and the CLI call with a machine token instead of a Keycloak token
    public static final String[] MACHINE_TOKEN_PATHS = {
        MACHINE_TOKEN_INGEST_PATH,
        MACHINE_TOKEN_DELIVERY_PATH
    };

    // --- Machine token ---
    public static final String AUTHORIZATION_BEARER_PREFIX = "Bearer ";
    // A batch holds at most 500 events and 50 handoffs, and a delivery proposal may carry one file of
    // 5 MiB, which is about 7 MB as base64; 8 MiB bounds what one request may make the server buffer
    // before the token's user is even known to be within the rate limit.
    public static final int MACHINE_TOKEN_MAX_BODY_BYTES = 8 * 1024 * 1024;
    public static final String FIELD_LIMIT_BYTES = "limitBytes";
}
