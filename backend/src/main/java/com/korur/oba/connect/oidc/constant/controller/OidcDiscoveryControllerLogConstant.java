package com.korur.oba.connect.oidc.constant.controller;

/**
 * Log messages of the issuer's discovery controller.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class OidcDiscoveryControllerLogConstant {

    private OidcDiscoveryControllerLogConstant() {
    }

    public static final String DISCOVERY_REQUEST = "Received request for the OpenID Connect discovery document";
    public static final String DISCOVERY_COMPLETED = "OpenID Connect discovery document request completed: issuer={}";
    public static final String JWKS_REQUEST = "Received request for the issuer key set";
    public static final String JWKS_COMPLETED = "Issuer key set request completed";
}
