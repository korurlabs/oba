package com.korur.oba.apps.kaya.blob.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the blob controller.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class BlobControllerLogConstant {

    private BlobControllerLogConstant() {
    }

    public static final String BLOB_UPLOAD_COMPLETED = "Uploaded blob {} to organization {}";
    public static final String BLOB_UPLOAD_REQUEST = "Received request to upload blob {} to organization {}";
}
