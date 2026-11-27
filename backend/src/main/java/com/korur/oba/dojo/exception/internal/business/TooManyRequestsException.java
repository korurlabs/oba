package com.korur.oba.dojo.exception.internal.business;

// 1. Framework-level
import org.springframework.http.HttpStatus;

import com.korur.oba.dojo.exception.enums.ExceptionApiError;
import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionError;
import com.korur.oba.dojo.exception.internal.infrastructure.ApplicationException;

// 3. Java SDK
import java.time.Duration;
import java.util.Map;
import java.util.Set;

public class TooManyRequestsException extends ApplicationException {

    private static final Set<ExceptionCategory> ALLOWED_CATEGORIES = Set.of(ExceptionCategory.API);

    public TooManyRequestsException() {
        super(HttpStatus.TOO_MANY_REQUESTS, ExceptionApiError.TOO_MANY_REQUESTS);
    }

    public TooManyRequestsException(ExceptionError exceptionError) {
        super(HttpStatus.TOO_MANY_REQUESTS, exceptionError);
        assertCategory(exceptionError, ALLOWED_CATEGORIES);
    }

    public TooManyRequestsException(ExceptionError exceptionError, Map<String, Object> errorValues) {
        super(HttpStatus.TOO_MANY_REQUESTS, exceptionError, errorValues);
        assertCategory(exceptionError, ALLOWED_CATEGORIES);
    }

    /**
     * A refusal that tells the client when the limit frees up, sent as Retry-After.
     *
     * @param exceptionError the error code and message
     * @param errorValues    the details of the error
     * @param retryAfter     how long until the limit lets the client in again
     */
    public TooManyRequestsException(ExceptionError exceptionError, Map<String, Object> errorValues, Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS, exceptionError, errorValues, retryAfter);
        assertCategory(exceptionError, ALLOWED_CATEGORIES);
    }
}
