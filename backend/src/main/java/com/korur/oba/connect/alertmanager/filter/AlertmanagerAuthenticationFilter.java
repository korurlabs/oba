package com.korur.oba.connect.alertmanager.filter;

// 1. Framework-level
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

// 2. Third-party
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.alertmanager.constant.AlertmanagerConstant;
import com.korur.oba.connect.alertmanager.constant.security.AlertmanagerSecurityLogConstant;
import com.korur.oba.connect.alertmanager.dto.AlertmanagerPrincipal;
import com.korur.oba.connect.alertmanager.validator.AlertmanagerTokenValidator;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Authenticates an Alertmanager webhook call on its bearer token alone. Only a
 * request with the configured token becomes an {@link AlertmanagerPrincipal},
 * carrying the organization bound to that token, with the
 * {@link AlertmanagerConstant#AUTHORITY_INTEGRATION_ALERTMANAGER} authority; every
 * other request goes on unauthenticated, and the webhook chain answers it with
 * 401. Who the app records as the author of what a notification causes is the
 * app's own concern. This filter is created by the webhook chain in
 * {@link com.korur.oba.connect.alertmanager.config.AlertmanagerSecurityConfig}
 * and is deliberately no Spring bean, so Spring Boot does not also register it
 * for every request.
 */
@RequiredArgsConstructor
public class AlertmanagerAuthenticationFilter extends OncePerRequestFilter {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AlertmanagerAuthenticationFilter.class);

    private final AlertmanagerTokenValidator alertmanagerTokenValidator;
    private final LoggingService loggingService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String method = request.getMethod();
        String uri = request.getRequestURI();
        String remoteAddress = request.getRemoteAddr();

        Optional<UUID> organizationId =
                alertmanagerTokenValidator.resolveOrganizationId(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (organizationId.isEmpty()) {
            loggingService.info(LOG_PREFIX, LogLayer.FILTER, AlertmanagerSecurityLogConstant.WEBHOOK_AUTHENTICATION,
                    method, uri, remoteAddress, AlertmanagerSecurityLogConstant.OUTCOME_REJECTED);
            filterChain.doFilter(request, response);
            return;
        }

        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                new AlertmanagerPrincipal(organizationId.get()),
                null,
                List.of(new SimpleGrantedAuthority(AlertmanagerConstant.AUTHORITY_INTEGRATION_ALERTMANAGER)));
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        loggingService.info(LOG_PREFIX, LogLayer.FILTER, AlertmanagerSecurityLogConstant.WEBHOOK_AUTHENTICATION,
                method, uri, remoteAddress, AlertmanagerSecurityLogConstant.OUTCOME_AUTHENTICATED);
        filterChain.doFilter(request, response);
    }
}
