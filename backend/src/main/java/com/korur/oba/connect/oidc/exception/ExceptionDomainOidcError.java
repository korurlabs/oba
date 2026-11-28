package com.korur.oba.connect.oidc.exception;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionDomainError;

/**
 * Domain-specific error codes for the OpenID Connect issuer (OID).
 *
 * A deployment without an issuer answers {@link #NOT_OIDC_ISSUER_NOT_CONFIGURED} on both issuer
 * documents, so a provider that fetches them learns there is nothing to trust. The internal code
 * marks a configured key that could not sign, which the startup checks should have prevented.
 */
public enum ExceptionDomainOidcError implements ExceptionDomainError {

    // ── Not found (OID_NOT) ─────────────────────────────────────────────────
    NOT_OIDC_ISSUER_NOT_CONFIGURED("OID_NOT_001",
            "This deployment has no OpenID Connect issuer.", ExceptionCategory.NOT),

    // ── Internal (OID_INT) ──────────────────────────────────────────────────
    INT_OIDC_SIGNING_FAILED("OID_INT_001",
            "The issuer could not sign the assertion.", ExceptionCategory.INT);

    private final String code;
    private final String message;
    private final ExceptionCategory category;

    ExceptionDomainOidcError(String code, String message, ExceptionCategory category) {
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
