package com.korur.oba.dojo.exception.enums;

/**
 * Internal, unexpected, and programming error codes (GEN_INT_*).
 *
 * Generic, cross-cutting internal errors not tied to any specific domain.
 * New codes belong here when they signal a bug or a contract violation
 * caught by infrastructure code rather than a domain rule the user could
 * have anticipated.
 */
public enum ExceptionInternalError implements ExceptionError {

    UNEXPECTED("GEN_INT_001", "An unexpected internal server error occurred."),
    UNREACHABLE_CODE_PATH("GEN_INT_002",
            "A code path marked as unreachable has been executed. This indicates a programming or control flow error."),
    JSON_SERIALIZATION("GEN_INT_003", "Failed to serialize object to JSON."),
    CATEGORY_MISMATCH("GEN_INT_004", "Exception category mismatch: error code category does not match exception type."),
    HASH_COMPUTATION_ERROR("GEN_INT_005", "Cannot compute hash: input content is not valid.");

    private final String code;
    private final String message;

    ExceptionInternalError(String code, String message) {
        this.code = code;
        this.message = message;
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
        return ExceptionCategory.INT;
    }
}
