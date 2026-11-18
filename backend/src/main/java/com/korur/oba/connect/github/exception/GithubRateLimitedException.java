package com.korur.oba.connect.github.exception;

// 1. Framework-level
import org.springframework.http.HttpStatus;

// 2. Third-party

// 3. Java SDK
import java.util.Set;

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionError;
import com.korur.oba.dojo.exception.internal.infrastructure.ApplicationException;

/**
 * GitHub is limiting the requests of the GitHub App. Answers 503 rather than the 502 of other
 * upstream failures, because the request is sound and succeeds once the limit resets, so the
 * client tells the user to wait instead of reporting a fault.
 */
public class GithubRateLimitedException extends ApplicationException {

    private static final Set<ExceptionCategory> ALLOWED_CATEGORIES = Set.of(ExceptionCategory.UPS);

    public GithubRateLimitedException(ExceptionError exceptionError) {
        super(HttpStatus.SERVICE_UNAVAILABLE, exceptionError);
        assertCategory(exceptionError, ALLOWED_CATEGORIES);
    }
}
