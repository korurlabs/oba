package com.korur.oba.dojo.exception.enums;

/**
 * Authentication, authorization, and security error codes ({@code GEN_SEC_*}).
 *
 * <p>These are generic, cross-cutting security errors not tied to any specific domain.</p>
 */
public enum ExceptionSecurityError implements ExceptionError {

    UNAUTHORIZED("GEN_AUT_001", "Authorization failed.", ExceptionCategory.AUT),
    FORBIDDEN("GEN_SEC_002", "Access forbidden.", ExceptionCategory.SEC),
    INCORRECT_ROLE("GEN_SEC_003", "User does not have the required role.", ExceptionCategory.SEC),
    HANDLER_UNANNOTATED("GEN_SEC_004", "Endpoint declares no access rule.", ExceptionCategory.SEC),
    API_TOKEN_ORGANIZATION_MISMATCH("GEN_SEC_005", "The API token may not act in this organization.", ExceptionCategory.SEC);

    private final String code;
    private final String message;
    private final ExceptionCategory category;

    ExceptionSecurityError(String code, String message, ExceptionCategory category) {
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
