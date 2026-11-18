package com.korur.oba.dojo.exception.enums;

/**
 * Contract for all exception error codes in the system.
 *
 * <p>Implemented by category-specific enums (e.g., {@code ExceptionApiError})
 * for generic/cross-cutting errors and by domain-specific enums
 * (e.g., {@code ExceptionDomainKazanError}) for domain-owned errors.</p>
 *
 * <p>Error code format: {@code <DOMAIN>_<CATEGORY>_<NNN>}</p>
 *
 */
public interface ExceptionError {

    /**
     * Returns the unique error code (e.g., "TAM_VAL_001").
     */
    String getCode();

    /**
     * Returns the human-readable error message.
     */
    String getMessage();

    /**
     * Returns the category that this error code belongs to.
     *
     * <p>The category determines which exception class may carry this error code.
     * Exception constructors enforce this mapping at runtime.</p>
     */
    ExceptionCategory getCategory();
}
