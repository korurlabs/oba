package com.korur.oba.dojo.exception.handler.security_layer;

// 1. Framework-level
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

// 2. Third-party
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

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
public class CustomAccessDeniedHandler implements AccessDeniedHandler {
    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(CustomAccessDeniedHandler.class);

    private final ObjectMapper objectMapper;
    private final LoggingService loggingService;

    /**
     * Handles access denied exceptions by logging the event and responding with a JSON
     * formatted error message indicating forbidden access.
     *
     * @param request               the HttpServletRequest that resulted in an AccessDeniedException
     * @param response              the HttpServletResponse to send the error response
     * @param accessDeniedException the exception thrown due to denied access
     * @throws InternalServerErrorException if an input or output exception occurs
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        String message = accessDeniedException.getMessage();
        loggingService.warn(
                LOG_PREFIX,
                LogLayer.HANDLER,
                "Access denied for request: {} {} - {}",
                method,
                uri,
                message
        );

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        CustomResponse<Void> responseBody = new CustomResponse<>(
                false,
                message != null ? message : ExceptionSecurityError.FORBIDDEN.getMessage(),
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
            throw new InternalServerErrorException(ExceptionInternalError.UNEXPECTED, Map.of(ExceptionConstant.ERROR, ex.getMessage()));
        }
    }
}