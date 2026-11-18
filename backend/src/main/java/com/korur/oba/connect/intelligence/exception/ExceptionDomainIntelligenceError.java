package com.korur.oba.connect.intelligence.exception;

import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionDomainError;

/**
 * Domain-specific error codes for the Intelligence layer domain (INT).
 *
 * <p>Covers multi-model AI abstraction: provider availability, request handling,
 * and configuration errors.</p>
 */
public enum ExceptionDomainIntelligenceError implements ExceptionDomainError {

    // ── Upstream (INT_UPS) ───────────────────────────────────────────────────
    UPS_UNAVAILABLE("INT_UPS_001", "Intelligence provider is currently unavailable.", ExceptionCategory.UPS),
    UPS_REQUEST_FAILED("INT_UPS_002", "Failed to get response from intelligence provider.", ExceptionCategory.UPS),
    UPS_EMPTY_RESPONSE("INT_UPS_003", "Empty response from intelligence provider.", ExceptionCategory.UPS),

    // ── Internal / Configuration (INT_INT) ─────────────────────────────────
    INT_NO_PROVIDER("INT_INT_001", "No provider configured for the requested use case.", ExceptionCategory.INT),
    INT_INVALID_CONFIGURATION("INT_INT_002", "Invalid provider configuration.", ExceptionCategory.INT);

    private final String code;
    private final String message;
    private final ExceptionCategory category;

    ExceptionDomainIntelligenceError(String code, String message, ExceptionCategory category) {
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
