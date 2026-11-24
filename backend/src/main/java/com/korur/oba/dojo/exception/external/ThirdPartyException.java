package com.korur.oba.dojo.exception.external;

// 2. Third-party
import lombok.Getter;

// 3. Java SDK
import java.util.Map;

import com.korur.oba.dojo.exception.internal.infrastructure.ApplicationException;
import com.korur.oba.dojo.exception.enums.ExceptionError;

@Getter
public class ThirdPartyException extends RuntimeException {
    private final String code;
    private Map<String, Object> errorValues;

    public ThirdPartyException(ExceptionError exceptionError, Exception ex) {
        super(exceptionError.getMessage(), ex);
        this.code = exceptionError.getCode();

        if (ex instanceof ApplicationException applicationException) {
            this.errorValues = applicationException.getErrorValues();
        }
    }
}
