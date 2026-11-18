package com.korur.oba.connect.azure.constant.client;

/**
 * Shared values used by the Azure Blob Storage client.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AzureBlobStorageClientConstant {

    private AzureBlobStorageClientConstant() {
        throw new UnsupportedOperationException("Constant class should not be instantiated");
    }

    // URL prefix of a storage endpoint served over TLS; only such endpoints get HTTPS-only SAS tokens
    public static final String HTTPS_URL_PREFIX = "https://";
}
