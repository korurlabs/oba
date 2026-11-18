package com.korur.oba.connect.outbox.exception;

import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionDomainError;

/**
 * Domain-specific error codes for the Outbox domain (OBX).
 *
 * <p>Covers transactional outbox for reliable event delivery.</p>
 */
public enum ExceptionDomainOutboxError implements ExceptionDomainError {

    // ── Not Found (OBX_NOT) ──────────────────────────────────────────────────
    NOT_EVENT_FOUND("OBX_NOT_001", "Outbox event not found.", ExceptionCategory.NOT),

    // ── Internal (OBX_INT) ────────────────────────────────────────────────────
    INT_NO_HANDLER_FOUND("OBX_INT_001", "No outbox handler found for target system.", ExceptionCategory.INT),
    INT_UNSUPPORTED_EVENT_TYPE("OBX_INT_002", "Unsupported event type for handler.", ExceptionCategory.INT),
    INT_DEAD_LETTER_MOVE_FAILED("OBX_INT_003", "Failed to move outbox event to dead letter table.", ExceptionCategory.INT);

    private final String code;
    private final String message;
    private final ExceptionCategory category;

    ExceptionDomainOutboxError(String code, String message, ExceptionCategory category) {
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
