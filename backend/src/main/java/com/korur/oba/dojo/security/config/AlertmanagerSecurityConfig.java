package com.korur.oba.dojo.security.config;

// 1. Framework-level
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK

// 4. Internal
import com.korur.oba.connect.alertmanager.validator.AlertmanagerTokenValidator;
import com.korur.oba.dojo.exception.handler.security_layer.CustomAuthenticationEntryPoint;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.constant.SecurityConstant;
import com.korur.oba.dojo.security.filter.AlertmanagerAuthenticationFilter;

/**
 * Security chain of the Alertmanager webhook. Alertmanager sends a static bearer
 * token, which the resource server of the main chain in
 * {@link com.korur.oba.dojo.security.config.SecurityConfig} would try to decode
 * as a JWT and answer 401. This chain has no resource server: its own
 * {@link AlertmanagerAuthenticationFilter} turns a valid token into the
 * {@link SecurityConstant#AUTHORITY_INTEGRATION_ALERTMANAGER} authority, and
 * every request without that authority is answered 401. It is ordered before the
 * main chain, so a webhook call never reaches the JWT chain.
 *
 * The token is a deployment secret of one organization, with no owning user, expiry or lifecycle, so it
 * is not an API token kind and keeps this chain of its own.
 */
@Configuration
@RequiredArgsConstructor
public class AlertmanagerSecurityConfig {

    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;

    /**
     * Builds the webhook chain for {@link SecurityConstant#ALERTMANAGER_WEBHOOK_PATH_PATTERN}.
     *
     * @param http                       the security builder
     * @param alertmanagerTokenValidator checks the bearer token
     * @param loggingService             logs each authentication outcome
     * @return the webhook chain
     * @throws Exception when the chain cannot be built
     */
    @Bean
    @Order(1)
    public SecurityFilterChain alertmanagerWebhookFilterChain(
            HttpSecurity http,
            AlertmanagerTokenValidator alertmanagerTokenValidator,
            LoggingService loggingService) throws Exception {
        http
                .securityMatcher(SecurityConstant.ALERTMANAGER_WEBHOOK_PATH_PATTERN)
                .csrf(csrf -> {
                    csrf.disable();
                })
                .sessionManagement(session -> {
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS);
                })
                .addFilterBefore(
                        new AlertmanagerAuthenticationFilter(alertmanagerTokenValidator, loggingService),
                        AuthorizationFilter.class)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(customAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> {
                    auth.anyRequest().hasAuthority(SecurityConstant.AUTHORITY_INTEGRATION_ALERTMANAGER);
                });

        return http.build();
    }
}
