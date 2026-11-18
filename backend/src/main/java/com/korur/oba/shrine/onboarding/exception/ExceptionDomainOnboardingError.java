package com.korur.oba.shrine.onboarding.exception;

import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionDomainError;

/**
 * Domain-specific error codes for the Onboarding domain (OBD).
 *
 * <p>Covers user onboarding lifecycle and state validation.</p>
 */
public enum ExceptionDomainOnboardingError implements ExceptionDomainError {

    // ── State (OBD_STA) ──────────────────────────────────────────────────────
    STA_ALREADY_ONBOARDED("OBD_STA_001", "User has already completed onboarding.", ExceptionCategory.STA);

    private final String code;
    private final String message;
    private final ExceptionCategory category;

    ExceptionDomainOnboardingError(String code, String message, ExceptionCategory category) {
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
