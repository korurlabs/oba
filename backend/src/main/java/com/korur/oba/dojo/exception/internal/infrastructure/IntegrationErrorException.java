package com.korur.oba.dojo.exception.internal.infrastructure;

// 1. Framework-level
import org.springframework.http.HttpStatus;

import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionError;
import com.korur.oba.dojo.exception.enums.ExceptionInternalError;

// 3. Java SDK
import java.util.Map;
import java.util.Set;

public class IntegrationErrorException extends ApplicationException {

    private static final Set<ExceptionCategory> ALLOWED_CATEGORIES = Set.of(ExceptionCategory.UPS);

    public IntegrationErrorException() {
        super(HttpStatus.INTERNAL_SERVER_ERROR, ExceptionInternalError.UNEXPECTED);
    }

    public IntegrationErrorException(ExceptionError exceptionError) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, exceptionError);
        assertCategory(exceptionError, ALLOWED_CATEGORIES);
    }

    public IntegrationErrorException(ExceptionError exceptionError, Map<String, Object> errorValues) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, exceptionError, errorValues);
        assertCategory(exceptionError, ALLOWED_CATEGORIES);
    }
}
