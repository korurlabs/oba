package com.korur.oba.dojo.exception.internal.infrastructure;

// 1. Framework-level
import org.springframework.http.HttpStatus;

// 2. Third-party

// 3. Java SDK
import java.util.Map;
import java.util.Set;

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionError;
import com.korur.oba.dojo.exception.enums.ExceptionUpstreamError;

/**
 * 503: the server cannot take the request now and the client may try again later. Unlike 429, the
 * refusal is about the capacity of the server, not about what the client sent.
 */
public class ServiceUnavailableException extends ApplicationException {

    private static final Set<ExceptionCategory> ALLOWED_CATEGORIES = Set.of(ExceptionCategory.UPS);

    public ServiceUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, ExceptionUpstreamError.SERVER_BUSY);
    }

    public ServiceUnavailableException(ExceptionError exceptionError) {
        super(HttpStatus.SERVICE_UNAVAILABLE, exceptionError);
        assertCategory(exceptionError, ALLOWED_CATEGORIES);
    }

    public ServiceUnavailableException(ExceptionError exceptionError, Map<String, Object> errorValues) {
        super(HttpStatus.SERVICE_UNAVAILABLE, exceptionError, errorValues);
        assertCategory(exceptionError, ALLOWED_CATEGORIES);
    }
}
