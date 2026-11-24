package com.korur.oba.dojo.exception.external;

import com.korur.oba.dojo.exception.enums.ExceptionError;

// 2. Third-party
import lombok.Getter;

@Getter
public class InternalProcessException extends RuntimeException {
    private final String code;

    public InternalProcessException(ExceptionError exceptionError, Throwable cause) {
        super(exceptionError.getMessage(), cause);
        this.code = exceptionError.getCode();
    }
}
