package com.korur.oba.apps.kaya.blob.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the blob services, including the not-found and refusal lines.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class BlobServiceLogConstant {

    private BlobServiceLogConstant() {
    }

    public static final String BLOB_HASH_MISMATCH = "Blob for organization {} hashes to {}, not to the named {}";
    public static final String BLOB_READ_FAILED = "Reading the body of blob {} for organization {} failed: {}";
    public static final String BLOB_STORED = "Blob {} of {} bytes stored for organization {}: {}";
    public static final String BLOB_TOO_LARGE = "Blob {} for organization {} is over the limit of {} bytes";
    public static final String UPLOADING_BLOB = "User {} uploads blob {} to organization {}";
    public static final String BLOB_NOT_FOUND = "Blob {} not found in organization {}";
    public static final String LOADING_BLOBS = "Loading {} blobs of organization {}";
}
