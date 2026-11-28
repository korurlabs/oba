package com.korur.oba.connect.azure.constant.client;

/**
 * Log messages of the provider of Azure Resource Manager tokens per connection. No message carries
 * an assertion, a token or the message Entra ID answers with; a failure names only its error code.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AzureConnectionTokenProviderLogConstant {

    private AzureConnectionTokenProviderLogConstant() {
        throw new UnsupportedOperationException("Constant class should not be instantiated");
    }

    public static final String ASSERTION_SIGNED =
            "Signed an assertion for an Azure token exchange. Organization: {}, Connection: {}";
    public static final String CREDENTIAL_BUILT =
            "Built the Azure credential of a connection. Organization: {}, Connection: {}";
    public static final String CREDENTIAL_EVICTED = "Dropped the Azure credential of connection {}";
    public static final String CLIENT_ID_MISSING =
            "Azure token refused: the connection has no client id. Organization: {}, Connection: {}";
    public static final String TOKEN_EXCHANGE_FAILED =
            "Azure token exchange failed. Organization: {}, Connection: {}, Error: {}, AADSTS code: {}, Cause: {}";
    public static final String TOKEN_EMPTY =
            "Azure answered the token exchange without a token. Organization: {}, Connection: {}";
    public static final String AADSTS_CODE_ABSENT = "none";
}
