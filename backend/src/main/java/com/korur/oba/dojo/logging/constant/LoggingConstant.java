package com.korur.oba.dojo.logging.constant;

/**
 * Constants for the logging infrastructure.
 *
 * <p>MDC key names, HTTP header names, and default placeholder values
 * used across filters, MDC helpers, and the logging service.</p>
 */
public final class LoggingConstant {

    private LoggingConstant() {
        // Utility class
    }

    // ── MDC Keys ────────────────────────────────────────────────────────

    // Request-scoped keys (set by filters per HTTP request).
    public static final String KEY_REQUEST_ID = "requestId";
    // Keycloak user identity from JWT sub claim.
    public static final String KEY_KEYCLOAK_ID = "keycloakId";
    // Organization context from X-Organization-ID header.
    public static final String KEY_ORGANIZATION_ID = "organizationId";
    // Keycloak realm name derived from JWT issuer URL.
    public static final String KEY_ISSUER_REALM = "issuerRealm";
    // Business domain set per log call by LoggingService.
    public static final String KEY_DOMAIN = "domain";
    // Architectural layer set per log call by LoggingService.
    public static final String KEY_LAYER = "layer";

    // ── HTTP Headers ────────────────────────────────────────────────────

    // Response header returning the request correlation ID to the client.
    public static final String HEADER_REQUEST_ID = "X-Request-ID";
    // Request header providing the organization context for multi-tenant operations.
    public static final String HEADER_ORGANIZATION_ID = "X-Organization-ID";

    // ── Default Values ──────────────────────────────────────────────────

    // Used when no value is available (e.g., unauthenticated request).
    public static final String VALUE_NONE = "none";
    // Used when the field is not applicable to the context.
    public static final String VALUE_NOT_APPLICABLE = "na";
    // Used when an unexpected value is encountered (e.g., unknown issuer realm).
    public static final String VALUE_UNEXPECTED = "unexpected";
}
