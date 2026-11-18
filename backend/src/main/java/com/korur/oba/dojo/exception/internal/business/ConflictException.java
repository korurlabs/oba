package com.korur.oba.dojo.exception.internal.business;

// 1. Framework-level
import org.springframework.http.HttpStatus;

import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionError;
import com.korur.oba.dojo.exception.enums.ExceptionStateError;
import com.korur.oba.dojo.exception.internal.infrastructure.ApplicationException;

// 3. Java SDK
import java.util.Map;
import java.util.Set;

public class ConflictException extends ApplicationException {

    private static final Set<ExceptionCategory> ALLOWED_CATEGORIES = Set.of(ExceptionCategory.STA);

    public ConflictException() {
        super(HttpStatus.CONFLICT, ExceptionStateError.CONFLICT);
    }

    public ConflictException(ExceptionError exceptionError) {
        super(HttpStatus.CONFLICT, exceptionError);
        assertCategory(exceptionError, ALLOWED_CATEGORIES);
    }

    public ConflictException(ExceptionError exceptionError, Map<String, Object> errorValues) {
        super(HttpStatus.CONFLICT, exceptionError, errorValues);
        assertCategory(exceptionError, ALLOWED_CATEGORIES);
    }
}
