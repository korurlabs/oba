package com.korur.oba.connect.github.constant.client;

/**
 * Log messages of the GitHub App token provider. No message carries a token, the App JWT or the
 * private key.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class GithubAppTokenProviderLogConstant {

    private GithubAppTokenProviderLogConstant() {
        throw new UnsupportedOperationException("Constant class should not be instantiated");
    }

    public static final String TOKEN_REFUSED_NOT_CONFIGURED =
            "GitHub installation token refused: the integration is not configured";
    public static final String TOKEN_FETCHED = "Fetched a GitHub installation token, expires at {}";
    public static final String TOKEN_DROPPED = "Dropped the cached GitHub installation token after GitHub rejected it";
    public static final String TOKEN_FETCH_FAILED_STATUS = "Fetching a GitHub installation token failed. Status: {}";
    public static final String TOKEN_FETCH_FAILED_IO = "Fetching a GitHub installation token failed without an answer";
    public static final String TOKEN_RESPONSE_INCOMPLETE = "GitHub answered the installation token request without a token or expiry";
    public static final String PRIVATE_KEY_UNREADABLE =
            "The GitHub App private key could not be read. It must be the base64 of a PKCS#8 PEM "
                    + "(openssl pkcs8 -topk8 -nocrypt -in app.pem | base64)";
    public static final String APP_JWT_SIGNING_FAILED = "Signing the GitHub App JWT failed";
}
