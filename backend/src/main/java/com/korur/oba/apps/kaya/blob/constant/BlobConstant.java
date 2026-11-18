package com.korur.oba.apps.kaya.blob.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Field names, limits, patterns and error value keys of the blob domain.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class BlobConstant {

    private BlobConstant() {
    }

    public static final String FIELD_SHA256 = "sha256";
    public static final int CONTENT_MAX_BYTES = 5_242_880;
    public static final String FIELD_ACTUAL_SHA256 = "actualSha256";
    public static final String FIELD_EXPECTED_SHA256 = "expectedSha256";
    public static final String FIELD_LIMIT_BYTES = "limitBytes";
    public static final String SHA256_ALGORITHM = "SHA-256";
    public static final String SHA256_INVALID_MESSAGE = "must be 64 lower-case hex characters";
    public static final String SHA256_PATTERN = "^[0-9a-f]{64}$";
}
