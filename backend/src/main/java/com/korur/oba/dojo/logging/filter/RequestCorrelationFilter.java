package com.korur.oba.dojo.logging.filter;

// 1. Framework-level
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// 3. Java SDK
import java.io.IOException;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.logging.constant.LoggingConstant;
import com.korur.oba.dojo.logging.mdc.LoggingMdc;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Adds request correlation id to MDC and response headers.
 *
 * <p>The filter always generates a backend-owned request id, stores it in MDC
 * for all downstream logs, and returns it in the response header for
 * client-side troubleshooting.</p>
 *
 * <p>This filter runs at highest precedence so all later framework/application
 * logs share the same request identifier.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    /**
     * Initializes request id context for the current request and cleans up managed
     * MDC keys afterward.
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        LoggingMdc.putManaged(LoggingConstant.KEY_REQUEST_ID, requestId);
        response.setHeader(LoggingConstant.HEADER_REQUEST_ID, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            LoggingMdc.clearManagedContext();
        }
    }
}
