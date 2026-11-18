package com.korur.oba.dojo.exception.internal.infrastructure;

// 1. Framework-level
import org.springframework.http.HttpStatus;

import com.korur.oba.dojo.exception.constant.ExceptionConstant;
import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionError;
import com.korur.oba.dojo.exception.enums.ExceptionInternalError;

// 2. Third-party
import lombok.Getter;

// 3. Java SDK
import java.util.Map;
import java.util.Set;

@Getter
public class ApplicationException extends RuntimeException {

    private final HttpStatus statusCode;
    private final String errorMessage;
    private final String errorCode;
    private final Map<String, Object> errorValues;

    public ApplicationException(HttpStatus statusCode, ExceptionError exceptionError) {
        super(exceptionError.getMessage());
        this.statusCode = statusCode;
        this.errorMessage = exceptionError.getMessage();
        this.errorCode = exceptionError.getCode();
        this.errorValues = null;
    }

    public ApplicationException(HttpStatus statusCode, ExceptionError exceptionError, Map<String, Object> errorValues) {
        super(exceptionError.getMessage());
        this.statusCode = statusCode;
        this.errorMessage = exceptionError.getMessage();
        this.errorCode = exceptionError.getCode();
        this.errorValues = errorValues;
    }

    /**
     * Validates that the given error code's category is in the allowed set.
     *
     * <p>Called by exception subclass constructors to enforce the category-to-exception
     * mapping at runtime. A mismatch indicates a programming error and throws
     * {@link InternalServerErrorException} with {@code GEN_INT_004}.</p>
     *
     * @param error   the error code being used
     * @param allowed the set of categories permitted by this exception type
     */
    protected static void assertCategory(ExceptionError error, Set<ExceptionCategory> allowed) {
        if (!allowed.contains(error.getCategory())) {
            throw new InternalServerErrorException(
                    ExceptionInternalError.CATEGORY_MISMATCH,
                    Map.of(
                            ExceptionConstant.EXPECTED, allowed.toString(),
                            ExceptionConstant.ACTUAL, error.getCategory().name(),
                            ExceptionConstant.CODE, error.getCode()
                    )
            );
        }
    }
}
