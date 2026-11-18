package com.korur.oba.dojo.exception.internal.business;

// 1. Framework-level
import org.springframework.http.HttpStatus;

import com.korur.oba.dojo.exception.enums.ExceptionApiError;
import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionError;
import com.korur.oba.dojo.exception.internal.infrastructure.ApplicationException;

// 3. Java SDK
import java.util.Map;
import java.util.Set;

/**
 * Purpose:
 * Represents request-level errors where the HTTP request itself is structurally invalid.
 * Restricted to API-category error codes only. Returns HTTP 400 Bad Request.
 *
 * Use this for protocol-level problems: malformed JSON, missing headers or parameters,
 * unsupported media types, invalid content types, or file size violations.
 * Example error codes: API_BAD_REQUEST, API_MISSING_PROPERTY, API_FILE_TOO_LARGE.
 *
 * Do NOT use this for business rule violations — use ValidationException instead.
 * BadRequestException returns HTTP 400 and only accepts API-prefixed error codes.
 * ValidationException returns HTTP 422 and only accepts VAL-prefixed error codes.
 * This distinction is enforced at runtime via assertCategory() in the constructor.
 *
 * Bigger picture:
 * HTTP 400 Bad Request means "the server cannot parse or understand the request."
 * This separates request-level issues (bad syntax, missing headers, 400) from
 * business-level issues (domain validation failures, 422). The frontend can use the
 * HTTP status code directly to decide how to handle errors: 400 shows generic
 * "fix your request" messages, 422 shows field-specific business feedback.
 * Monitoring benefits too: spike in 400 = client bug, spike in 422 = user behavior.
 * Primarily thrown by GlobalExceptionHandler when catching Spring framework exceptions.
 *
 * @docType STANDARD
 * @since 2025-03-01
 * @updated 2026-03-07
 */
public class BadRequestException extends ApplicationException {

    private static final Set<ExceptionCategory> ALLOWED_CATEGORIES = Set.of(ExceptionCategory.API);

    public BadRequestException() {
        super(HttpStatus.BAD_REQUEST, ExceptionApiError.BAD_REQUEST);
    }

    public BadRequestException(ExceptionError exceptionError) {
        super(HttpStatus.BAD_REQUEST, exceptionError);
        assertCategory(exceptionError, ALLOWED_CATEGORIES);
    }

    public BadRequestException(ExceptionError exceptionError, Map<String, Object> errorValues) {
        super(HttpStatus.BAD_REQUEST, exceptionError, errorValues);
        assertCategory(exceptionError, ALLOWED_CATEGORIES);
    }
}
