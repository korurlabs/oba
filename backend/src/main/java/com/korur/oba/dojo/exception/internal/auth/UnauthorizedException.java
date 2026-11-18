package com.korur.oba.dojo.exception.internal.auth;

// 1. Framework-level
import org.springframework.http.HttpStatus;

import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionError;
import com.korur.oba.dojo.exception.enums.ExceptionSecurityError;
import com.korur.oba.dojo.exception.internal.infrastructure.ApplicationException;

// 3. Java SDK
import java.util.Map;
import java.util.Set;

public class UnauthorizedException extends ApplicationException {

    private static final Set<ExceptionCategory> ALLOWED_CATEGORIES = Set.of(ExceptionCategory.AUT);

    public UnauthorizedException() {
        super(HttpStatus.UNAUTHORIZED, ExceptionSecurityError.UNAUTHORIZED);
    }

    public UnauthorizedException(ExceptionError exceptionError) {
        super(HttpStatus.UNAUTHORIZED, exceptionError);
        assertCategory(exceptionError, ALLOWED_CATEGORIES);
    }

    public UnauthorizedException(ExceptionError exceptionError, Map<String, Object> errorValues) {
        super(HttpStatus.UNAUTHORIZED, exceptionError, errorValues);
        assertCategory(exceptionError, ALLOWED_CATEGORIES);
    }
}
