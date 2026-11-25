package com.korur.oba.dojo.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

import com.korur.oba.dojo.exception.handler.security_layer.CustomAccessDeniedHandler;
import com.korur.oba.dojo.exception.handler.security_layer.CustomAuthenticationEntryPoint;
import com.korur.oba.dojo.security.constant.SecurityConstant;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;

    /**
     * Main chain for every request no earlier chain matched, authenticated by a
     * Keycloak JWT. Chains with their own credentials, the Alertmanager webhook
     * chain of {@link AlertmanagerSecurityConfig} (order 1) and the API token chain
     * of {@link ApiTokenSecurityConfig} (order 2), are ordered before it, so their
     * tokens and reserved paths never reach the JWT decoder.
     */
    @Bean
    @Order(3)
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource)
            throws Exception {

        http
                .csrf(csrf -> {
                    csrf.disable();
                })
                .cors(cors -> {
                    cors.configurationSource(corsConfigurationSource);
                })
                .sessionManagement(session -> {
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS);
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(customAuthenticationEntryPoint))
                .exceptionHandling(ex -> ex.accessDeniedHandler(customAccessDeniedHandler))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    auth.requestMatchers(
                            SecurityConstant.PUBLIC_PATHS).permitAll();

                    // All other requests must be authenticated
                    auth.anyRequest().authenticated();
                });

        return http.build();
    }
}
