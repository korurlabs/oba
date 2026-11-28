package com.korur.oba.connect.azure.helper;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientResponseException;

import static org.assertj.core.api.Assertions.assertThat;

// 2. Third-party
import com.azure.core.exception.ClientAuthenticationException;

// 3. Java SDK
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

// 4. Internal
import com.korur.oba.connect.azure.constant.AzureResourceManagerConstant;
import com.korur.oba.connect.azure.exception.ExceptionDomainAzureError;
import com.korur.oba.connect.oidc.exception.ExceptionDomainOidcError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.infrastructure.ApplicationException;

class AzureResourceManagerErrorTranslatorUnitTest {

    private static final String ENTRA_MESSAGE = "AADSTS700213: No matching federated identity record found for "
            + "presented assertion subject 'oba:organization:1:connection:2'. Trace ID: 0000";

    private static RestClientResponseException response(HttpStatus status) {
        byte[] body = "{\"error\":{\"code\":\"AuthorizationFailed\",\"message\":\"object 1234\"}}"
                .getBytes(StandardCharsets.UTF_8);
        if (status.is5xxServerError()) {
            return HttpServerErrorException.create(status, status.getReasonPhrase(), null, body, StandardCharsets.UTF_8);
        }
        return HttpClientErrorException.create(status, status.getReasonPhrase(), null, body, StandardCharsets.UTF_8);
    }

    @Test
    void fromResponse_unauthorized_isCredentialsRefused() {
        ApplicationException result = AzureResourceManagerErrorTranslator.fromResponse(response(HttpStatus.UNAUTHORIZED));

        assertThat(result.getErrorCode()).isEqualTo(ExceptionDomainAzureError.UPS_AZURE_ARM_CREDENTIALS_REFUSED.getCode());
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void fromResponse_forbidden_isCredentialsRefused() {
        ApplicationException result = AzureResourceManagerErrorTranslator.fromResponse(response(HttpStatus.FORBIDDEN));

        assertThat(result.getErrorCode()).isEqualTo(ExceptionDomainAzureError.UPS_AZURE_ARM_CREDENTIALS_REFUSED.getCode());
    }

    @Test
    void fromResponse_tooManyRequests_isRateLimitedWith503() {
        ApplicationException result =
                AzureResourceManagerErrorTranslator.fromResponse(response(HttpStatus.TOO_MANY_REQUESTS));

        assertThat(result.getErrorCode()).isEqualTo(ExceptionDomainAzureError.UPS_AZURE_ARM_RATE_LIMITED.getCode());
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void fromResponse_serverError_isUnreachable() {
        ApplicationException result =
                AzureResourceManagerErrorTranslator.fromResponse(response(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(result.getErrorCode()).isEqualTo(ExceptionDomainAzureError.UPS_AZURE_ARM_UNREACHABLE.getCode());
    }

    @Test
    void fromResponse_otherClientError_isUnexpectedResponseWithoutTheBody() {
        ApplicationException result = AzureResourceManagerErrorTranslator.fromResponse(response(HttpStatus.BAD_REQUEST));

        assertThat(result.getErrorCode()).isEqualTo(ExceptionDomainAzureError.UPS_AZURE_ARM_UNEXPECTED_RESPONSE.getCode());
        assertThat(result.getErrorValues()).isNull();
    }

    @Test
    void unreachable_isUnreachable() {
        assertThat(AzureResourceManagerErrorTranslator.unreachable().getErrorCode())
                .isEqualTo(ExceptionDomainAzureError.UPS_AZURE_ARM_UNREACHABLE.getCode());
    }

    @Test
    void fromTokenExchange_wrappedAadstsCode_keepsOnlyTheCode() {
        RuntimeException thrown = new RuntimeException(
                new ClientAuthenticationException(ENTRA_MESSAGE, null, new IllegalStateException(ENTRA_MESSAGE)));

        ApplicationException result = AzureResourceManagerErrorTranslator.fromTokenExchange(thrown);

        assertThat(result.getErrorCode()).isEqualTo(ExceptionDomainAzureError.UPS_AZURE_TOKEN_EXCHANGE_REFUSED.getCode());
        assertThat(result.getErrorValues())
                .isEqualTo(Map.of(AzureResourceManagerConstant.FIELD_AADSTS_CODE, "AADSTS700213"));
        assertThat(result.getMessage()).doesNotContain("oba:organization");
    }

    @Test
    void fromTokenExchange_authenticationFailureWithoutCode_isRefusedWithoutValues() {
        RuntimeException thrown = new RuntimeException(new ClientAuthenticationException("refused", null));

        ApplicationException result = AzureResourceManagerErrorTranslator.fromTokenExchange(thrown);

        assertThat(result.getErrorCode()).isEqualTo(ExceptionDomainAzureError.UPS_AZURE_TOKEN_EXCHANGE_REFUSED.getCode());
        assertThat(result.getErrorValues()).isNull();
    }

    @Test
    void fromTokenExchange_noAnswer_isUnreachable() {
        RuntimeException thrown = new RuntimeException(new UncheckedIOException(
                new IOException("Connection reset")));

        ApplicationException result = AzureResourceManagerErrorTranslator.fromTokenExchange(thrown);

        assertThat(result.getErrorCode()).isEqualTo(ExceptionDomainAzureError.UPS_AZURE_ARM_UNREACHABLE.getCode());
    }

    @Test
    void fromTokenExchange_signingError_isKeptAsItIs() {
        NotFoundException signing = new NotFoundException(ExceptionDomainOidcError.NOT_OIDC_ISSUER_NOT_CONFIGURED);

        ApplicationException result = AzureResourceManagerErrorTranslator.fromTokenExchange(new RuntimeException(signing));

        assertThat(result).isSameAs(signing);
    }

    @Test
    void findAadstsCode_noCode_isEmpty() {
        assertThat(AzureResourceManagerErrorTranslator.findAadstsCode(new RuntimeException("timeout"))).isEmpty();
    }
}
