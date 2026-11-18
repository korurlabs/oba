package com.korur.oba.shrine.identity.exception;

import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionDomainError;

/**
 * Domain-specific error codes for the Identity domain (IDN).
 *
 * Covers user management, Keycloak linkage, and profile operations.
 */
public enum ExceptionDomainIdentityError implements ExceptionDomainError {

    // ── Security (IDN_SEC) ───────────────────────────────────────────────────
    SEC_USER_DELETED("IDN_SEC_001", "User account has been deleted.", ExceptionCategory.SEC),

    // ── Not Found (IDN_NOT) ───────────────────────────────────────────────────
    NOT_USER_FOUND("IDN_NOT_001", "User not found.", ExceptionCategory.NOT),
    NOT_USER_FIRST_AND_LAST_NAME_FOUND("IDN_NOT_004", "User first and last name is blank.", ExceptionCategory.NOT),
    NOT_USER_EMAIL_IS_MISSING("IDN_NOT_005", "User email is missing.", ExceptionCategory.NOT),
    NOT_USER_NOT_SYNCED("IDN_NOT_006", "User has not been synced yet. Call POST /users/me first.", ExceptionCategory.NOT),

    // ── Validation (IDN_VAL) ─────────────────────────────────────────────────
    VAL_USER_NOT_MEMBER_OF_ORGANIZATION("IDN_VAL_001", "User is not a member of the specified organization.", ExceptionCategory.VAL),

    // ── State (IDN_STA) ──────────────────────────────────────────────────────
    STA_USER_IS_NOT_ACTIVE("IDN_STA_001", "User is not active.", ExceptionCategory.STA),
    STA_USER_EMAIL_ALREADY_EXISTS("IDN_STA_004", "A user with this email already exists.", ExceptionCategory.STA),
    STA_USER_ALREADY_DISABLED("IDN_STA_005", "User is already disabled.", ExceptionCategory.STA),
    // ── Internal (IDN_INT) ───────────────────────────────────────────────────
    INT_TOKEN_HASH_NULL_INPUT("IDN_INT_001", "Token hash input cannot be null or blank.", ExceptionCategory.INT),
    INT_TOKEN_HASH_INVALID_OUTPUT("IDN_INT_002", "Token hash output is invalid (expected 64-character hex string).", ExceptionCategory.INT);

    private final String code;
    private final String message;
    private final ExceptionCategory category;

    ExceptionDomainIdentityError(String code, String message, ExceptionCategory category) {
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
