package com.korur.oba.dojo.exception.internal.infrastructure;

// 1. Framework-level
import org.springframework.http.HttpStatus;

import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionError;
import com.korur.oba.dojo.exception.enums.ExceptionUpstreamError;

// 3. Java SDK
import java.util.Map;
import java.util.Set;

public class BadGatewayException extends ApplicationException {

    private static final Set<ExceptionCategory> ALLOWED_CATEGORIES = Set.of(ExceptionCategory.UPS);

    public BadGatewayException() {
        super(HttpStatus.BAD_GATEWAY, ExceptionUpstreamError.BAD_GATEWAY);
    }

    public BadGatewayException(ExceptionError exceptionError) {
        super(HttpStatus.BAD_GATEWAY, exceptionError);
        assertCategory(exceptionError, ALLOWED_CATEGORIES);
    }

    public BadGatewayException(ExceptionError exceptionError, Map<String, Object> errorValues) {
        super(HttpStatus.BAD_GATEWAY, exceptionError, errorValues);
        assertCategory(exceptionError, ALLOWED_CATEGORIES);
    }
}
