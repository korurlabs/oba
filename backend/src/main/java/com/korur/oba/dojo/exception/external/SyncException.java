package com.korur.oba.dojo.exception.external;

// 1. Framework-level
import org.springframework.http.HttpStatus;

import com.korur.oba.dojo.exception.enums.ExceptionError;
import com.korur.oba.dojo.exception.internal.infrastructure.ApplicationException;

// 3. Java SDK
import java.util.Map;

/**
 * Exception for inbound sync validation failures.
 *
 * <p>Thrown when an external system sends data that fails validation
 * during synchronization. Maps to HTTP 502 Bad Gateway.</p>
 */
public class SyncException extends ApplicationException {
    public SyncException(ExceptionError exceptionError) {
        super(HttpStatus.BAD_GATEWAY, exceptionError);
    }

    public SyncException(ExceptionError exceptionError, Map<String, Object> errorValues) {
        super(HttpStatus.BAD_GATEWAY, exceptionError, errorValues);
    }
}
