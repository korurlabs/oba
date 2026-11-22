package com.korur.oba.shrine.legalentity.exception;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionDomainError;

/**
 * Domain-specific error codes for legal entities (LGE), the companies of an organization that
 * issue its documents.
 *
 * Codes are namespaced by ExceptionCategory: LGE_NOT_* for not-found, LGE_STA_* for a state
 * that refuses the action. Numeric suffixes are append-only: never renumber an existing code,
 * since clients may already match on it.
 */
public enum ExceptionDomainLegalEntityError implements ExceptionDomainError {

    // ── Not Found (LGE_NOT) ─────────────────────────────────────────────────
    // Also the answer for a legal entity of another organization, so an id reveals nothing to another tenant.
    NOT_LEGAL_ENTITY_FOUND("LGE_NOT_001", "Legal entity not found.", ExceptionCategory.NOT),

    // ── State (LGE_STA) ─────────────────────────────────────────────────────
    STA_LEGAL_ENTITY_SHORT_NAME_TAKEN("LGE_STA_001",
            "Another legal entity of this organization already has this short name.", ExceptionCategory.STA);

    private final String code;
    private final String message;
    private final ExceptionCategory category;

    ExceptionDomainLegalEntityError(String code, String message, ExceptionCategory category) {
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
