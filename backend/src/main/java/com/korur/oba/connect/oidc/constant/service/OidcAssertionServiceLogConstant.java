package com.korur.oba.connect.oidc.constant.service;

/**
 * Log messages of the issuer's assertion service. They never hold an assertion.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class OidcAssertionServiceLogConstant {

    private OidcAssertionServiceLogConstant() {
    }

    public static final String ISSUER_NOT_CONFIGURED = "OpenID Connect issuer requested while none is configured";
    public static final String ASSERTION_SIGNING_FAILED = "Signing an assertion with key {} failed";
}
