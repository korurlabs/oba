package com.korur.oba.apps.kaya.core.security.config;

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
import com.korur.oba.apps.kaya.core.security.constant.KayaSecurityConstant;
import com.korur.oba.apps.kaya.core.security.filter.MachineTokenAuthenticationFilter;
import com.korur.oba.apps.kaya.identity.service.KayaUserQueryService;
import com.korur.oba.apps.kaya.identity.service.OrganizationAccessQueryService;
import com.korur.oba.apps.kaya.machine.service.MachineService;
import com.korur.oba.apps.kaya.machine.service.MachineTokenQueryService;
import com.korur.oba.dojo.exception.handler.security_layer.CustomAccessDeniedHandler;
import com.korur.oba.dojo.exception.handler.security_layer.CustomAuthenticationEntryPoint;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.service.AppAccessQueryService;

/**
 * Security chain of the paths the Kaya recorder and CLI call with a machine token,
 * {@link KayaSecurityConstant#MACHINE_TOKEN_PATHS}.
 *
 * A machine token is an opaque bearer token, which the resource server of the JWT chain in
 * {@link com.korur.oba.dojo.security.config.SecurityConfig} would try to decode and refuse. This
 * chain has no resource server, so a Keycloak JWT sent here is never read and the
 * {@link MachineTokenAuthenticationFilter} refuses it as an unknown machine token; a machine token sent
 * to any other path reaches the JWT chain and fails decoding. Neither credential therefore opens
 * the other's paths. The filter also caps the body at
 * {@link KayaSecurityConstant#MACHINE_TOKEN_MAX_BODY_BYTES} before anything reaches a controller.
 * CORS stays off because no browser calls these paths.
 *
 * Ordered after the Alertmanager chain of
 * {@link com.korur.oba.connect.alertmanager.config.AlertmanagerSecurityConfig} and before the JWT
 * chain, which matches every remaining path.
 */
@Configuration
@RequiredArgsConstructor
public class KayaMachineTokenSecurityConfig {

    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;

    /**
     * Builds the machine token chain.
     *
     * @param http                   the security builder
     * @param machineTokenQueryService resolves a bearer token to its machine
     * @param machineService           records the token's last use
     * @param kayaUserQueryService     loads the token's user for the delivery access check
     * @param organizationAccessQueryService checks that the user still acts in the organization
     * @param appAccessQueryService    checks that the user may open the Kaya app on delivery paths
     * @param objectMapper           writes the filter's JSON error bodies
     * @param loggingService         logs each authentication outcome
     * @return the machine token chain
     * @throws Exception when the chain cannot be built
     */
    @Bean
    @Order(2)
    public SecurityFilterChain kayaMachineTokenFilterChain(
            HttpSecurity http,
            MachineTokenQueryService machineTokenQueryService,
            MachineService machineService,
            KayaUserQueryService kayaUserQueryService,
            OrganizationAccessQueryService organizationAccessQueryService,
            AppAccessQueryService appAccessQueryService,
            ObjectMapper objectMapper,
            LoggingService loggingService) throws Exception {
        http
                .securityMatcher(KayaSecurityConstant.MACHINE_TOKEN_PATHS)
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
                        new MachineTokenAuthenticationFilter(
                                machineTokenQueryService, machineService, kayaUserQueryService,
                                organizationAccessQueryService, appAccessQueryService, objectMapper, loggingService),
                        AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> {
                    auth.anyRequest().authenticated();
                });

        return http.build();
    }
}
