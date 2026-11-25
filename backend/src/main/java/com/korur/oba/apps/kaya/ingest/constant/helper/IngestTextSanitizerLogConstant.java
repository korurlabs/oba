package com.korur.oba.apps.kaya.ingest.constant.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the step that makes the text of a recorder batch storable.
 */
public final class IngestTextSanitizerLogConstant {

    private IngestTextSanitizerLogConstant() {
    }

    public static final String HANDOFF_SKIPPED = "Skipped handoff {} of session {} from machine {}: its content of {} "
            + "bytes holds U+0000, so it is a binary file and is not stored";
    public static final String PLAN_SKIPPED = "Skipped {} plan {} of session {} from machine {}: its content of {} "
            + "bytes holds U+0000, so it is a binary file and is not stored";
    public static final String NUL_REPLACED = "Replaced U+0000 with U+FFFD in the text of the batch of session {} "
            + "from machine {}";
    public static final String RECORD_READ_FAILED = "Could not read component {} of request record {} to replace U+0000";
    public static final String RECORD_COPY_FAILED = "Could not copy request record {} to replace U+0000";
}
