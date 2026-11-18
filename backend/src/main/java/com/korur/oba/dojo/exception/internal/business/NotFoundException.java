package com.korur.oba.dojo.exception.internal.business;

// 1. Framework-level
import org.springframework.http.HttpStatus;

import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionError;
import com.korur.oba.dojo.exception.enums.ExceptionNotFoundError;
import com.korur.oba.dojo.exception.internal.infrastructure.ApplicationException;

// 3. Java SDK
import java.util.Map;
import java.util.Set;

public class NotFoundException extends ApplicationException {

    private static final Set<ExceptionCategory> ALLOWED_CATEGORIES = Set.of(ExceptionCategory.NOT);

    public NotFoundException() {
        super(HttpStatus.NOT_FOUND, ExceptionNotFoundError.RESOURCE_FOUND);
    }

    public NotFoundException(ExceptionError exceptionError) {
        super(HttpStatus.NOT_FOUND, exceptionError);
        assertCategory(exceptionError, ALLOWED_CATEGORIES);
    }

    public NotFoundException(ExceptionError exceptionError, Map<String, Object> errorValues) {
        super(HttpStatus.NOT_FOUND, exceptionError, errorValues);
        assertCategory(exceptionError, ALLOWED_CATEGORIES);
    }
}
