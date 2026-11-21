package com.korur.oba.dojo.security.token.config;

// 1. Framework-level
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

// 2. Third-party
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.exception.handler.security_layer.CustomAccessDeniedHandler;
import com.korur.oba.dojo.exception.handler.security_layer.CustomAuthenticationEntryPoint;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.token.filter.ApiTokenAuthenticationFilter;
import com.korur.oba.dojo.security.token.filter.ApiTokenRequestMatcher;
import com.korur.oba.dojo.security.token.helper.ApiTokenKindRegistry;
import com.korur.oba.dojo.security.token.service.ApiTokenQueryService;
import com.korur.oba.dojo.security.token.service.ApiTokenService;

/**
 * Security chain of the requests made with an API token, and of the paths a token kind reserves.
 *
 * An API token is an opaque bearer token, which the resource server of the JWT chain in
 * {@link com.korur.oba.dojo.security.config.SecurityConfig} would try to decode and refuse. This chain
 * selects a request by the prefix of its bearer token or by a reserved path
 * ({@link ApiTokenRequestMatcher}) and has no resource server, so a JWT sent to a reserved path is never
 * read and the {@link ApiTokenAuthenticationFilter} refuses it. CORS stays off because no browser sends
 * these tokens.
 *
 * Ordered after the Alertmanager chain of
 * {@link com.korur.oba.connect.alertmanager.config.AlertmanagerSecurityConfig} and before the JWT
 * chain, which matches every remaining path.
 */
@Configuration
@RequiredArgsConstructor
public class ApiTokenSecurityConfig {

    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;

    /**
     * Builds the API token chain.
     *
     * @param http                  the security builder
     * @param apiTokenKindRegistry  the accepted token kinds and their rules
     * @param apiTokenQueryService  resolves a bearer token to its stored token
     * @param apiTokenService       records the token's last use
     * @param objectMapper          writes the filter's JSON error bodies
     * @param loggingService        logs each authentication outcome
     * @return the API token chain
     * @throws Exception when the chain cannot be built
     */
    @Bean
    @Order(2)
    public SecurityFilterChain apiTokenFilterChain(
            HttpSecurity http,
            ApiTokenKindRegistry apiTokenKindRegistry,
            ApiTokenQueryService apiTokenQueryService,
            ApiTokenService apiTokenService,
            ObjectMapper objectMapper,
            LoggingService loggingService) throws Exception {
        http
                .securityMatcher(new ApiTokenRequestMatcher(apiTokenKindRegistry))
                .csrf(csrf -> {
                    csrf.disable();
                })
                .cors(cors -> {
                    cors.disable();
                })
                .sessionManagement(session -> {
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS);
                })
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(customAuthenticationEntryPoint)
                        .accessDeniedHandler(customAccessDeniedHandler))
                .addFilterBefore(
                        new ApiTokenAuthenticationFilter(
                                apiTokenKindRegistry, apiTokenQueryService, apiTokenService, objectMapper,
                                loggingService),
                        AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> {
                    auth.anyRequest().authenticated();
                });

        return http.build();
    }
}
