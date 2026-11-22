package com.korur.oba.shrine.document.exception;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionDomainError;

/**
 * Domain-specific error codes for documents and document templates (DOC): the designed HTML
 * files of an organization and the PDFs rendered from them.
 *
 * Codes are namespaced by ExceptionCategory: DOC_NOT_* for not-found, DOC_STA_* for a state
 * that refuses the action, DOC_VAL_* for a request that breaks a rule. Numeric suffixes are
 * append-only: never renumber an existing code, since clients may already match on it.
 */
public enum ExceptionDomainDocumentError implements ExceptionDomainError {

    // ── Not Found (DOC_NOT) ─────────────────────────────────────────────────
    // Also the answer for a template of another organization, so an id reveals nothing to another tenant.
    NOT_DOCUMENT_TEMPLATE_FOUND("DOC_NOT_001", "Document template not found.", ExceptionCategory.NOT),

    // ── State (DOC_STA) ─────────────────────────────────────────────────────
    STA_DOCUMENT_TEMPLATE_NAME_TAKEN("DOC_STA_001",
            "The organization already has a template with this name in this language.", ExceptionCategory.STA),

    // ── Validation (DOC_VAL) ────────────────────────────────────────────────
    VAL_FILE_NOT_HTML_OR_PDF("DOC_VAL_001", "The file is not the HTML or PDF file it should be.", ExceptionCategory.VAL),
    VAL_DOCUMENT_NOT_USABLE("DOC_VAL_002", "The document template can not be used here.", ExceptionCategory.VAL);

    private final String code;
    private final String message;
    private final ExceptionCategory category;

    ExceptionDomainDocumentError(String code, String message, ExceptionCategory category) {
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
