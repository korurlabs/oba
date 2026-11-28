package com.korur.oba.connect.oidc.constant.helper;

/**
 * Log messages of the issuer's signing key provider. They name key ids, never key material.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class OidcSigningKeyProviderLogConstant {

    private OidcSigningKeyProviderLogConstant() {
    }

    public static final String ISSUER_DISABLED = "No OpenID Connect issuer configured; cloud connections are unavailable";
    public static final String EPHEMERAL_KEY =
            "No issuer signing key configured; signing with the ephemeral key {}, which no provider trusts after a restart";
    public static final String CONFIGURED_KEY = "Issuer {} signs assertions with key {}";
    public static final String PUBLISHED_KEY = "Issuer publishes the additional key {}";
    public static final String KEY_WITHOUT_ISSUER = "Issuer signing key {} is configured without an issuer URL; it signs nothing";
}
