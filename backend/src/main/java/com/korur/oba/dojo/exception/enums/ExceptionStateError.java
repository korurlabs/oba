package com.korur.oba.dojo.exception.enums;

/**
 * Domain state and lifecycle violation error codes ({@code GEN_STA_*}).
 *
 * <p>These are generic, cross-cutting state errors not tied to any specific domain.
 * Includes: resource state conflicts, lifecycle violations, and duplicate entity detection.</p>
 */
public enum ExceptionStateError implements ExceptionError {

    CONFLICT("GEN_STA_001", "The operation conflicts with the current resource state."),
    OPERATION_NOT_ALLOWED("GEN_STA_002", "This operation is not allowed in the current state."),
    INVALID_UPDATE_STATE("GEN_STA_003", "Cannot update entity due to invalid state."),
    DUPLICATE_ENTITY("GEN_STA_004", "An entity with the same key already exists.");

    private final String code;
    private final String message;

    ExceptionStateError(String code, String message) {
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
        return ExceptionCategory.STA;
    }
}
