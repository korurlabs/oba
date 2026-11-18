package com.korur.oba.connect.github.helper;

// 1. Framework-level
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;

// 2. Third-party

// 3. Java SDK
import java.util.Locale;

// 4. Internal
import com.korur.oba.connect.github.constant.GithubConstant;
import com.korur.oba.connect.github.exception.ExceptionDomainGithubError;
import com.korur.oba.connect.github.exception.GithubRateLimitedException;
import com.korur.oba.dojo.exception.internal.infrastructure.ApplicationException;
import com.korur.oba.dojo.exception.internal.infrastructure.BadGatewayException;

/**
 * Turns a failed GitHub call into the error the user sees.
 *
 * GitHub answers an exhausted rate limit with 403 or 429, and a refused credential with 401 or
 * with a plain 403. A 403 is a rate limit when it carries the rate-limit headers or, for a
 * secondary limit that may carry neither, when its message says so. That decides between "wait"
 * ({@link ExceptionDomainGithubError#UPS_GITHUB_RATE_LIMITED}, 503) and "fix the App"
 * ({@link ExceptionDomainGithubError#UPS_GITHUB_CREDENTIALS_REFUSED}, 502). Every other failure is
 * {@link ExceptionDomainGithubError#UPS_GITHUB_UNREACHABLE} (502).
 */
public final class GithubErrorTranslator {

    private GithubErrorTranslator() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * Translates an answer of the REST API with an error status.
     *
     * @param exception the error answer
     * @return the exception to throw
     */
    public static ApplicationException fromResponse(RestClientResponseException exception) {
        if (isRateLimited(exception)) {
            return new GithubRateLimitedException(ExceptionDomainGithubError.UPS_GITHUB_RATE_LIMITED);
        }
        int status = exception.getStatusCode().value();
        if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
            return new BadGatewayException(ExceptionDomainGithubError.UPS_GITHUB_CREDENTIALS_REFUSED);
        }
        return unreachable();
    }

    /**
     * Translates an answer of the installation token endpoint. A 404 there means the configured
     * installation is not one of the App's, which is a credential problem, not a missing resource.
     *
     * @param exception the error answer
     * @return the exception to throw
     */
    public static ApplicationException fromTokenResponse(RestClientResponseException exception) {
        if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
            return new BadGatewayException(ExceptionDomainGithubError.UPS_GITHUB_CREDENTIALS_REFUSED);
        }
        return fromResponse(exception);
    }

    /**
     * The error of a call that got no usable answer: a timeout, a connection failure or a body
     * that could not be read.
     *
     * @return the exception to throw
     */
    public static ApplicationException unreachable() {
        return new BadGatewayException(ExceptionDomainGithubError.UPS_GITHUB_UNREACHABLE);
    }

    private static boolean isRateLimited(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return true;
        }
        if (status != HttpStatus.FORBIDDEN.value()) {
            return false;
        }
        return hasRateLimitHeaders(exception.getResponseHeaders()) || namesRateLimit(exception);
    }

    private static boolean hasRateLimitHeaders(HttpHeaders headers) {
        if (headers == null) {
            return false;
        }
        return GithubConstant.RATE_LIMIT_EXHAUSTED.equals(headers.getFirst(GithubConstant.HEADER_RATE_LIMIT_REMAINING))
                || headers.containsKey(GithubConstant.HEADER_RETRY_AFTER);
    }

    private static boolean namesRateLimit(RestClientResponseException exception) {
        return exception.getResponseBodyAsString().toLowerCase(Locale.ROOT)
                .contains(GithubConstant.RATE_LIMIT_MESSAGE_MARKER);
    }
}
