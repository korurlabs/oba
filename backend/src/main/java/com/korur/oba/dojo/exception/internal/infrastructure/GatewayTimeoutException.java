package com.korur.oba.dojo.exception.internal.infrastructure;

// 1. Framework-level
import org.springframework.http.HttpStatus;

import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionError;
import com.korur.oba.dojo.exception.enums.ExceptionUpstreamError;

// 3. Java SDK
import java.util.Map;
import java.util.Set;

public class GatewayTimeoutException extends ApplicationException {

    private static final Set<ExceptionCategory> ALLOWED_CATEGORIES = Set.of(ExceptionCategory.UPS);

    public GatewayTimeoutException() {
        super(HttpStatus.GATEWAY_TIMEOUT, ExceptionUpstreamError.GATEWAY_TIMEOUT);
    }

    public GatewayTimeoutException(ExceptionError exceptionError) {
        super(HttpStatus.GATEWAY_TIMEOUT, exceptionError);
        assertCategory(exceptionError, ALLOWED_CATEGORIES);
    }

    public GatewayTimeoutException(ExceptionError exceptionError, Map<String, Object> errorValues) {
        super(HttpStatus.GATEWAY_TIMEOUT, exceptionError, errorValues);
        assertCategory(exceptionError, ALLOWED_CATEGORIES);
    }
}
