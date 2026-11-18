package com.korur.oba.dojo.exception.enums;

/**
 * Purpose:
 * Defines generic, cross-cutting error codes for business validation failures (GEN_VAL_*).
 * These codes represent issues where the request was well-formed but violated a
 * generic business rule not specific to any domain.
 *
 * Includes codes for: general validation failure (used by @Valid DTO constraints)
 * and null required fields.
 * All codes in this enum belong to the VAL category and are thrown via ValidationException.
 *
 * Bigger picture:
 * This is the error code enum for the VAL category at the generic (GEN) level.
 * It pairs with ValidationException (the throwable type). Domain-specific validation
 * codes live in their respective ExceptionDomain*Error enums (e.g., VAL_SPRINT_NOT_PLANNED
 * in ExceptionDomainKazanError). The distinction from ExceptionApiError is important:
 * ExceptionValidationError codes signal that the request was well-formed but failed a
 * business rule (HTTP 422 Unprocessable Entity), while ExceptionApiError codes signal
 * that the request itself was structurally invalid (HTTP 400 Bad Request). The different
 * HTTP status codes allow frontends and monitoring to distinguish them without parsing
 * error code prefixes.
 *
 * @docType STANDARD
 * @author Claude
 * @since 2025-03-01
 * @updated 2026-03-07
 */
public enum ExceptionValidationError implements ExceptionError {

    VALIDATION("GEN_VAL_001", "One or more fields are invalid."),
    FIELD_IS_NULL("GEN_VAL_002", "Required field is null.");

    private final String code;
    private final String message;

    ExceptionValidationError(String code, String message) {
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
        return ExceptionCategory.VAL;
    }
}
