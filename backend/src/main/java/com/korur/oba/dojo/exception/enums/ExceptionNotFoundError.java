package com.korur.oba.dojo.exception.enums;

/**
 * Not found and missing data error codes ({@code GEN_NOT_*}).
 *
 * <p>These are generic, cross-cutting not-found errors not tied to any specific domain.</p>
 */
public enum ExceptionNotFoundError implements ExceptionError {

    RESOURCE_FOUND("GEN_NOT_001", "Resource not found.");

    private final String code;
    private final String message;

    ExceptionNotFoundError(String code, String message) {
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
        return ExceptionCategory.NOT;
    }
}
