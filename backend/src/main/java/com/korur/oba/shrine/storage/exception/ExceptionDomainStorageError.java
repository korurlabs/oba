package com.korur.oba.shrine.storage.exception;

import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionDomainError;

/**
 * Domain-specific error codes for the Storage domain (STG).
 *
 * Covers file storage management, upload validation, and file lookups.
 * Codes are namespaced by ExceptionCategory: STG_NOT_* for not-found,
 * STG_VAL_* for validation failures, STG_SEC_* for refused access. Numeric suffixes are append-only:
 * never renumber an existing code, since clients may already match on it.
 */
public enum ExceptionDomainStorageError implements ExceptionDomainError {

    // ── Not Found (STG_NOT) ──────────────────────────────────────────────────
    NOT_FILE_FOUND("STG_NOT_001", "Storage file not found.", ExceptionCategory.NOT),

    // ── Validation (STG_VAL) ─────────────────────────────────────────────────
    VAL_FILE_NAME_MISSING("STG_VAL_001", "File name is required.", ExceptionCategory.VAL),
    VAL_FILE_EMPTY("STG_VAL_002", "Uploaded file is empty.", ExceptionCategory.VAL),
    VAL_METADATA_FILE_COUNT_MISMATCH("STG_VAL_003", "Number of metadata entries must match number of uploaded files.", ExceptionCategory.VAL),
    VAL_FILE_ALREADY_EXISTS("STG_VAL_005", "An attachment with the same content is already attached to this entity.", ExceptionCategory.VAL),

    // ── Security (STG_SEC) ───────────────────────────────────────────────────
    SEC_ENTITY_TYPE_NOT_ATTACHABLE("STG_SEC_001", "Attachments of this entity type are not accessible.", ExceptionCategory.SEC);

    private final String code;
    private final String message;
    private final ExceptionCategory category;

    ExceptionDomainStorageError(String code, String message, ExceptionCategory category) {
        this.code = code;
        this.message = message;
        this.category = category;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public ExceptionCategory getCategory() {
        return category;
    }
}
