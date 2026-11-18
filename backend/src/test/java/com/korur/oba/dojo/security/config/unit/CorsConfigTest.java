package com.korur.oba.dojo.security.config.unit;

// 1. Framework-level
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

// 2. Third-party
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.dojo.security.config.CorsConfig;
import com.korur.oba.dojo.security.constant.SecurityConstant;

class CorsConfigTest {

    private static final String PROD_PROFILE = "prod";
    private static final String LOCAL_PROFILE = "local";

    private static final String PRODUCTION_ORIGIN = "https://kazan.sensey.io";
    private static final String KORUR_ORIGIN = "https://kazan.korur.com";
    private static final String LOCALHOST_ORIGIN = "http://localhost:3002";
    private static final String INSTANCE_ORIGIN = "http://oba-x.localhost:26101";

    private static MockEnvironment environmentWithProfile(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return environment;
    }

    private static CorsConfiguration configurationFor(boolean allowLocalOrigins) {
        CorsConfigurationSource source = new CorsConfig(allowLocalOrigins, environmentWithProfile(LOCAL_PROFILE))
                .corsConfigurationSource();
        CorsConfiguration configuration = source.getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/boards"));
        assertNotNull(configuration);
        return configuration;
    }

    @Test
    void corsConfigurationSource_localOriginsOff_refusesLocalOriginsAndKeepsProductionOrigin() {
        // Arrange and act
        CorsConfiguration configuration = configurationFor(false);

        // Assert
        assertNull(configuration.checkOrigin(LOCALHOST_ORIGIN));
        assertNull(configuration.checkOrigin(INSTANCE_ORIGIN));
        assertEquals(PRODUCTION_ORIGIN, configuration.checkOrigin(PRODUCTION_ORIGIN));
        assertEquals(KORUR_ORIGIN, configuration.checkOrigin(KORUR_ORIGIN));
    }

    @Test
    void corsConfigurationSource_localOriginsOn_allowsLocalhostAndInstanceOrigins() {
        // Arrange and act
        CorsConfiguration configuration = configurationFor(true);

        // Assert
        assertEquals(LOCALHOST_ORIGIN, configuration.checkOrigin(LOCALHOST_ORIGIN));
        assertEquals(INSTANCE_ORIGIN, configuration.checkOrigin(INSTANCE_ORIGIN));
        assertEquals(PRODUCTION_ORIGIN, configuration.checkOrigin(PRODUCTION_ORIGIN));
        assertEquals(KORUR_ORIGIN, configuration.checkOrigin(KORUR_ORIGIN));
        assertNull(configuration.checkOrigin("https://localhost:3002"));
    }

    @Test
    void corsConfigurationSource_localOriginsOn_keepsCredentialsMethodsAndHeaders() {
        // Arrange and act
        CorsConfiguration configuration = configurationFor(true);

        // Assert
        assertTrue(configuration.getAllowCredentials());
        assertEquals(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"), configuration.getAllowedMethods());
        assertTrue(configuration.getAllowedHeaders().contains("X-Organization-ID"));
        assertTrue(configuration.getAllowedHeaders().contains("Last-Event-ID"));
        assertEquals(List.of("Content-Disposition"), configuration.getExposedHeaders());
    }

    @Test
    void constructor_localOriginsOnUnderProdProfile_throwsIllegalStateException() {
        // Arrange
        MockEnvironment environment = environmentWithProfile(PROD_PROFILE);

        // Act
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new CorsConfig(true, environment));

        // Assert
        assertTrue(exception.getMessage().contains(SecurityConstant.CORS_ALLOW_LOCAL_ORIGINS_PROPERTY));
    }

    @Test
    void constructor_localOriginsOffUnderProdProfile_starts() {
        // Arrange
        MockEnvironment environment = environmentWithProfile(PROD_PROFILE);

        // Act and assert
        assertDoesNotThrow(() -> new CorsConfig(false, environment));
    }
}
