package com.korur.oba.dojo.logging.filter;

// 1. Framework-level
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// 3. Java SDK
import java.io.IOException;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.logging.constant.LoggingConstant;
import com.korur.oba.dojo.logging.mdc.LoggingMdc;
import com.korur.oba.dojo.util.SecurityUtils;
import com.korur.oba.dojo.util.UuidUtil;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Populates request-scoped logging context in MDC after authentication.
 *
 * <p>Managed fields set by this filter:
 * organizationId, keycloakId, issuerRealm.</p>
 *
 * <p>Request id is set earlier by {@link RequestCorrelationFilter}.</p>
 */
@Component
public class RequestLoggingContextFilter extends OncePerRequestFilter {

    /**
     * Resolves request and auth context fields and puts them into managed MDC keys.
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        putOrganizationContext(request.getHeader(LoggingConstant.HEADER_ORGANIZATION_ID));
        putAuthenticationContext();

        filterChain.doFilter(request, response);
    }

    /**
     * Resolves organization context from the X-Organization-ID header.
     */
    private void putOrganizationContext(String organizationHeader) {
        if (organizationHeader == null || organizationHeader.isBlank()) {
            LoggingMdc.putManaged(LoggingConstant.KEY_ORGANIZATION_ID, LoggingConstant.VALUE_NONE);
            return;
        }

        String organizationId = UuidUtil.parse(organizationHeader)
                .map(UUID::toString)
                .orElse(LoggingConstant.VALUE_UNEXPECTED);
        LoggingMdc.putManaged(LoggingConstant.KEY_ORGANIZATION_ID, organizationId);
    }

    /**
     * Extracts auth-derived fields (sub + issuer realm) from current JWT when available.
     */
    private void putAuthenticationContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            LoggingMdc.putManaged(LoggingConstant.KEY_KEYCLOAK_ID, LoggingConstant.VALUE_NONE);
            LoggingMdc.putManaged(LoggingConstant.KEY_ISSUER_REALM, LoggingConstant.VALUE_NONE);
            return;
        }

        String keycloakId = jwt.getClaimAsString("sub");
        if (keycloakId == null || keycloakId.isBlank()) {
            LoggingMdc.putManaged(LoggingConstant.KEY_KEYCLOAK_ID, LoggingConstant.VALUE_NONE);
        } else {
            LoggingMdc.putManaged(LoggingConstant.KEY_KEYCLOAK_ID, keycloakId);
        }

        String issuer = jwt.getIssuer() != null ? jwt.getIssuer().toString() : jwt.getClaimAsString("iss");
        LoggingMdc.putManaged(LoggingConstant.KEY_ISSUER_REALM, resolveIssuerRealm(issuer));
    }

    /**
     * Extracts the realm name from the Keycloak issuer URL.
     * For {@code http://localhost:8086/realms/sensey}, returns {@code "sensey"}.
     */
    private String resolveIssuerRealm(String issuer) {
        if (issuer == null || issuer.isBlank()) {
            return LoggingConstant.VALUE_NONE;
        }

        String sanitized = SecurityUtils.sanitizeForLog(issuer.trim());
        if (sanitized.endsWith("/")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1);
        }

        int lastSlash = sanitized.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < sanitized.length() - 1) {
            return sanitized.substring(lastSlash + 1);
        }

        return LoggingConstant.VALUE_UNEXPECTED;
    }
}
