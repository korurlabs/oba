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
import java.time.Duration;
import java.util.Map;
import java.util.Set;

@Getter
public class ApplicationException extends RuntimeException {

    private final HttpStatus statusCode;
    private final String errorMessage;
    private final String errorCode;
    private final Map<String, Object> errorValues;
    // How long the client should wait before it tries again, sent as Retry-After; null for a refusal
    // that waiting does not lift.
    private final Duration retryAfter;

    public ApplicationException(HttpStatus statusCode, ExceptionError exceptionError) {
        this(statusCode, exceptionError, null, null);
    }

    public ApplicationException(HttpStatus statusCode, ExceptionError exceptionError, Map<String, Object> errorValues) {
        this(statusCode, exceptionError, errorValues, null);
    }

    /**
     * A refusal that lifts with time, such as a rate limit or a busy server: the handler sends
     * retryAfter as the Retry-After header of the answer.
     *
     * @param statusCode     the status of the answer, 429 or 503
     * @param exceptionError the error code and message
     * @param errorValues    the details of the error, or null
     * @param retryAfter     how long the client should wait before it tries again, or null
     */
    protected ApplicationException(HttpStatus statusCode, ExceptionError exceptionError, Map<String, Object> errorValues,
                                   Duration retryAfter) {
        super(exceptionError.getMessage());
        this.statusCode = statusCode;
        this.errorMessage = exceptionError.getMessage();
        this.errorCode = exceptionError.getCode();
        this.errorValues = errorValues;
        this.retryAfter = retryAfter;
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
