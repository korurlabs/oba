package com.korur.oba.dojo.security.config;

// 1. Framework-level
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

// 2. Third-party

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;

// 4. Internal
import com.korur.oba.dojo.security.constant.SecurityConstant;

@Configuration
public class CorsConfig {

    private final boolean allowLocalOrigins;

    /**
     * Local origins let a developer's frontend on http://localhost or on a local stack
     * instance host such as http://oba-kazan-epics.localhost:26101 call this
     * backend. Production must never allow them, and an environment variable set in a
     * deployed environment outranks every properties file, so the prod profile with the
     * switch on stops startup instead of reopening CORS to localhost.
     *
     * @throws IllegalStateException when the switch is on and the prod profile is active
     */
    public CorsConfig(
            @Value("${" + SecurityConstant.CORS_ALLOW_LOCAL_ORIGINS_PROPERTY + ":false}") boolean allowLocalOrigins,
            Environment environment) {
        if (allowLocalOrigins && environment.acceptsProfiles(Profiles.of(SecurityConstant.PROFILE_PROD))) {
            throw new IllegalStateException(SecurityConstant.CORS_LOCAL_ORIGINS_IN_PROD);
        }
        this.allowLocalOrigins = allowLocalOrigins;
    }

    /**
     * The origins trusted to call this backend: the Sensey domains, plus the local origins when
     * the switch is on. The realtime handshake accepts the same list, so a client allowed over
     * HTTP is allowed over WebSocket and the prod guard above covers both.
     *
     * @return the allowed origin patterns, a new list
     */
    public List<String> allowedOriginPatterns() {
        List<String> originPatterns = new ArrayList<>(SecurityConstant.CORS_ORIGIN_PATTERNS);
        if (allowLocalOrigins) {
            originPatterns.addAll(SecurityConstant.CORS_LOCAL_ORIGIN_PATTERNS);
        }
        return originPatterns;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // 🔹 Allowed origins (Restrict to trusted frontend domains)
        config.setAllowedOriginPatterns(allowedOriginPatterns());

        // 🔹 Allowed HTTP methods
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // 🔹 Allowed headers
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "x-xsrf-token",
                "Content-Length", "Content-Disposition", "Accept", "X-Organization-ID",
                SecurityConstant.HEADER_LAST_EVENT_ID));

        // 🔹 Expose headers & allow credentials
        config.setAllowCredentials(true);
        config.setExposedHeaders(List.of("Content-Disposition"));

        // 🔹 Apply to all endpoints
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }
}
