package com.korur.oba.dojo.logging.exception;

import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionDomainError;

/**
 * Domain-specific error codes for the Logging/Audit domain (LOG).
 *
 * <p>Covers audit logging: log entries, log streams, and log stream pods.</p>
 */
public enum ExceptionDomainLoggingError implements ExceptionDomainError {

    // ── Not Found (LOG_NOT) ──────────────────────────────────────────────────
    NOT_LOG_ENTRY_FOUND("LOG_NOT_001", "Log entry not found.", ExceptionCategory.NOT),
    NOT_LOG_STREAM_FOUND("LOG_NOT_002", "Log stream not found.", ExceptionCategory.NOT),
    NOT_LOG_STREAM_POD_FOUND("LOG_NOT_003", "Log stream pod not found.", ExceptionCategory.NOT);

    private final String code;
    private final String message;
    private final ExceptionCategory category;

    ExceptionDomainLoggingError(String code, String message, ExceptionCategory category) {
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
