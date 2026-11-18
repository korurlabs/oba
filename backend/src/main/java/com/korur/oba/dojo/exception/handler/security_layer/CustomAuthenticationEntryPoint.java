package com.korur.oba.dojo.exception.handler.security_layer;

// 1. Framework-level

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// 3. Java SDK
import java.io.IOException;
import java.util.Map;

// 4. Internal
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.exception.constant.ExceptionConstant;
import com.korur.oba.dojo.exception.enums.ExceptionInternalError;
import com.korur.oba.dojo.exception.enums.ExceptionSecurityError;
import com.korur.oba.dojo.exception.internal.infrastructure.ErrorResponse;
import com.korur.oba.dojo.exception.internal.infrastructure.InternalServerErrorException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(CustomAuthenticationEntryPoint.class);
    private final ObjectMapper objectMapper;
    private final LoggingService loggingService;

    /**
     * Called when an authentication request fails.
     *
     * <p>
     * Logs the authentication failure and writes a JSON response with
     * HTTP status 401 (Unauthorized) to the client.
     * </p>
     *
     * @param request       the request that triggered the authentication failure
     * @param response      the response to write the error to
     * @param authException the exception that triggered the authentication failure
     * @throws InternalServerErrorException if an error occurs while writing the response
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) {
        final String message = authException.getMessage();

        loggingService.error(
                LOG_PREFIX,
                LogLayer.HANDLER,
                "Authentication failed for request: {} - {}",
                authException,
                request.getRequestURI(),
                message
        );

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        final CustomResponse<Void> responseBody = new CustomResponse<>(
                false,
                message != null
                        ? message
                        : ExceptionSecurityError.FORBIDDEN.getMessage(),
                null,
                null,
                new ErrorResponse(
                        ExceptionSecurityError.FORBIDDEN.getCode(),
                        ExceptionSecurityError.FORBIDDEN.getMessage(),
                        null
                )
        );

        try {
            objectMapper.writeValue(response.getWriter(), responseBody);
        } catch (IOException ex) {
            throw new InternalServerErrorException(ExceptionInternalError.UNEXPECTED, Map.of(ExceptionConstant.MESSAGE, ex.getMessage()));
        }
    }
}