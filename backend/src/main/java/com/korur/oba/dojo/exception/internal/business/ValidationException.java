package com.korur.oba.dojo.exception.internal.business;

// 1. Framework-level
import org.springframework.http.HttpStatus;

import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionError;
import com.korur.oba.dojo.exception.enums.ExceptionValidationError;
import com.korur.oba.dojo.exception.internal.infrastructure.ApplicationException;

// 3. Java SDK
import java.util.Map;
import java.util.Set;

/**
 * Purpose:
 * Represents business rule violations where the request is structurally valid
 * but fails domain-level validation. Restricted to VAL-category error codes only.
 * Returns HTTP 422 Unprocessable Entity.
 *
 * Use this when the request was well-formed but its content violates a business rule:
 * duplicate names, invalid date ranges, closed fiscal periods, exceeded limits,
 * missing required business fields. Example error codes: VAL_INVOICE_ALREADY_SENT,
 * VAL_FISCAL_PERIOD_CLOSED, VAL_PASSCHAT_TOO_MANY_RECIPIENTS.
 *
 * Do NOT use this for malformed requests — use BadRequestException instead.
 * ValidationException returns HTTP 422 and only accepts VAL-prefixed error codes.
 * BadRequestException returns HTTP 400 and only accepts API-prefixed error codes.
 * This distinction is enforced at runtime via assertCategory() in the constructor.
 *
 * Bigger picture:
 * HTTP 422 Unprocessable Entity means "the server understood the request syntax but
 * cannot process the contained instructions." This separates business-level issues
 * (domain validation failures, 422) from request-level issues (bad syntax, missing
 * headers, 400). The frontend can use the HTTP status code directly to decide how to
 * handle errors: 400 shows generic "fix your request" messages, 422 shows field-specific
 * business feedback. Monitoring benefits too: spike in 400 = client bug, spike in
 * 422 = user behavior. Thrown by validators and service methods throughout the domain layer.
 *
 * @docType STANDARD
 * @since 2025-03-01
 * @updated 2026-03-07
 */
public class ValidationException extends ApplicationException {

    private static final Set<ExceptionCategory> ALLOWED_CATEGORIES = Set.of(ExceptionCategory.VAL);

    public ValidationException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, ExceptionValidationError.VALIDATION);
    }

    public ValidationException(ExceptionError exceptionError) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, exceptionError);
        assertCategory(exceptionError, ALLOWED_CATEGORIES);
    }

    public ValidationException(ExceptionError exceptionError, Map<String, Object> errorValues) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, exceptionError, errorValues);
        assertCategory(exceptionError, ALLOWED_CATEGORIES);
    }
}
