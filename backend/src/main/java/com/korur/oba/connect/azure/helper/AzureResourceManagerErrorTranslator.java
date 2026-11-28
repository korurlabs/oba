package com.korur.oba.connect.azure.helper;

// 1. Framework-level
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;

// 2. Third-party
import com.azure.core.exception.ClientAuthenticationException;

// 3. Java SDK
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;

// 4. Internal
import com.korur.oba.connect.azure.constant.AzureResourceManagerConstant;
import com.korur.oba.connect.azure.exception.ExceptionDomainAzureError;
import com.korur.oba.dojo.exception.internal.infrastructure.ApplicationException;
import com.korur.oba.dojo.exception.internal.infrastructure.BadGatewayException;
import com.korur.oba.dojo.exception.internal.infrastructure.ServiceUnavailableException;

/**
 * Turns a failed Azure Resource Manager call or a refused token exchange into the error the admin
 * sees, so the answer says whether to retry, to wait or to fix the identity.
 *
 * - 401 and 403 from Resource Manager: the identity lacks a role there
 *   ({@link ExceptionDomainAzureError#UPS_AZURE_ARM_CREDENTIALS_REFUSED}, 502).
 * - 429: Resource Manager is limiting requests
 *   ({@link ExceptionDomainAzureError#UPS_AZURE_ARM_RATE_LIMITED}, 503), lifted by waiting.
 * - 5xx and calls without an answer: {@link ExceptionDomainAzureError#UPS_AZURE_ARM_UNREACHABLE} (502).
 * - Every other status and a body that cannot be read:
 *   {@link ExceptionDomainAzureError#UPS_AZURE_ARM_UNEXPECTED_RESPONSE} (502).
 * - A refused token exchange: {@link ExceptionDomainAzureError#UPS_AZURE_TOKEN_EXCHANGE_REFUSED} (502)
 *   carrying only Entra ID's AADSTS code, such as AADSTS700213 for a subject no federated
 *   credential trusts. Entra's message is dropped, because it repeats the subject and other
 *   identifiers of the organization's tenant.
 *
 * No error value carries an Azure or Entra message, a token or an assertion.
 */
public final class AzureResourceManagerErrorTranslator {

    private AzureResourceManagerErrorTranslator() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * Translates an answer of Resource Manager with an error status other than 404, which the
     * client answers itself as an absent resource.
     *
     * @param exception the error answer
     * @return the exception to throw
     */
    public static ApplicationException fromResponse(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
            return new BadGatewayException(ExceptionDomainAzureError.UPS_AZURE_ARM_CREDENTIALS_REFUSED);
        }
        if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return new ServiceUnavailableException(ExceptionDomainAzureError.UPS_AZURE_ARM_RATE_LIMITED);
        }
        if (exception.getStatusCode().is5xxServerError()) {
            return unreachable();
        }
        return unexpectedResponse();
    }

    /**
     * The error of a call that got no answer: a timeout or a connection failure.
     *
     * @return the exception to throw
     */
    public static ApplicationException unreachable() {
        return new BadGatewayException(ExceptionDomainAzureError.UPS_AZURE_ARM_UNREACHABLE);
    }

    /**
     * The error of an answer the client cannot use: a body that does not parse, a missing body or
     * a next page link outside Resource Manager.
     *
     * @return the exception to throw
     */
    public static ApplicationException unexpectedResponse() {
        return new BadGatewayException(ExceptionDomainAzureError.UPS_AZURE_ARM_UNEXPECTED_RESPONSE);
    }

    /**
     * Translates a failed token exchange. azure-identity wraps the cause, so the causes are walked:
     * an application error raised while signing the assertion is kept as it is; an AADSTS code in
     * any message makes a refused exchange with that code; an authentication failure without a
     * code makes a refused exchange without one; anything else got no answer from Entra ID.
     *
     * @param exception what the credential threw
     * @return the exception to throw
     */
    public static ApplicationException fromTokenExchange(Throwable exception) {
        Optional<ApplicationException> applicationException = findApplicationException(exception);
        if (applicationException.isPresent()) {
            return applicationException.get();
        }
        Optional<String> aadstsCode = findAadstsCode(exception);
        if (aadstsCode.isPresent()) {
            return new BadGatewayException(ExceptionDomainAzureError.UPS_AZURE_TOKEN_EXCHANGE_REFUSED,
                    Map.of(AzureResourceManagerConstant.FIELD_AADSTS_CODE, aadstsCode.get()));
        }
        if (hasAuthenticationFailure(exception)) {
            return new BadGatewayException(ExceptionDomainAzureError.UPS_AZURE_TOKEN_EXCHANGE_REFUSED);
        }
        return unreachable();
    }

    /**
     * Finds Entra ID's error code in the messages of an exception and its causes.
     *
     * @param exception what the credential threw
     * @return the first AADSTS code, or empty when no message holds one
     */
    public static Optional<String> findAadstsCode(Throwable exception) {
        Throwable current = exception;
        int depth = 0;
        while (current != null && depth < AzureResourceManagerConstant.MAX_CAUSE_DEPTH) {
            String message = current.getMessage();
            if (message != null) {
                Matcher matcher = AzureResourceManagerConstant.AADSTS_CODE_PATTERN.matcher(message);
                if (matcher.find()) {
                    return Optional.of(matcher.group());
                }
            }
            current = current.getCause();
            depth++;
        }
        return Optional.empty();
    }

    private static Optional<ApplicationException> findApplicationException(Throwable exception) {
        Throwable current = exception;
        int depth = 0;
        while (current != null && depth < AzureResourceManagerConstant.MAX_CAUSE_DEPTH) {
            if (current instanceof ApplicationException applicationException) {
                return Optional.of(applicationException);
            }
            current = current.getCause();
            depth++;
        }
        return Optional.empty();
    }

    private static boolean hasAuthenticationFailure(Throwable exception) {
        Throwable current = exception;
        int depth = 0;
        while (current != null && depth < AzureResourceManagerConstant.MAX_CAUSE_DEPTH) {
            if (current instanceof ClientAuthenticationException) {
                return true;
            }
            current = current.getCause();
            depth++;
        }
        return false;
    }
}
