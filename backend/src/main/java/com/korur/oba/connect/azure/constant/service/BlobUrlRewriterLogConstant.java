package com.korur.oba.connect.azure.constant.service;

/**
 * Log message constants for BlobUrlRewriter operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class BlobUrlRewriterLogConstant {

    private BlobUrlRewriterLogConstant() {
        throw new UnsupportedOperationException("Constant class should not be instantiated");
    }

    public static final String REWRITE_FAILED = "BlobUrlRewriter: failed to generate SAS URL for container: {}, blob: {}. Returning original URL.";
    public static final String REWRITE_UNKNOWN_CONTAINER = "BlobUrlRewriter: URL path segment does not match any configured container name: {}. Returning original URL.";
    public static final String REWRITE_HTML_COUNT = "BlobUrlRewriter: rewrote {} blob URL(s) in HTML/text body.";
    public static final String REWRITE_SCHEME_PREPENDED = "BlobUrlRewriter: no scheme found in blob-base-url; auto-prepended https://. Effective base URL: {}";
}
