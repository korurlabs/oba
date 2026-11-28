package com.korur.oba.connect.azure.constant;

/**
 * Log message constants for refused Azure connection requests.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AzureConnectionExceptionLogConstant {

    private AzureConnectionExceptionLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String CONNECTIONS_UNAVAILABLE =
            "Azure connection refused: the OpenID Connect issuer is not configured: organizationId={}";
    public static final String CONNECTION_NOT_FOUND =
            "Azure connection not found: connectionId={}, organizationId={}";
    public static final String CONNECTION_NAME_DUPLICATE =
            "Azure connection name already exists: organizationId={}, name={}";
    public static final String CONNECTION_IDENTITY_DUPLICATE =
            "Azure connection identity already connected: organizationId={}, tenantId={}, clientId={}";
    public static final String CONNECTION_CLIENT_ID_MISSING =
            "Azure connection check refused, no client id: connectionId={}";
    public static final String CONNECTION_NOT_CONNECTED =
            "Azure connection is not connected: connectionId={}, status={}";
    public static final String CONNECTION_IN_USE =
            "Azure connection delete refused, still referenced: connectionId={}";
}
