package com.korur.oba.connect.alertmanager.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.alertmanager.constant.AlertmanagerConstant;
import com.korur.oba.connect.alertmanager.properties.AlertmanagerProperties;

/**
 * Checks the static bearer token Alertmanager sends with every webhook call and
 * names the organization bound to it. The webhook has no JWT, so this token is
 * the only proof of the caller. It is compared in constant time so the response
 * time reveals nothing about it, and when no token is configured nothing
 * matches, so a deployment without the secret exposes nothing.
 */
@Component
public class AlertmanagerTokenValidator {

    private final AlertmanagerProperties alertmanagerProperties;

    /**
     * Refuses a token without an organization at startup: its alerts would land
     * in no known organization, and failing here keeps the previous pods serving
     * instead of refusing every notification later.
     *
     * @param alertmanagerProperties the webhook configuration
     * @throws IllegalStateException if the token is set and the organization is not
     */
    public AlertmanagerTokenValidator(AlertmanagerProperties alertmanagerProperties) {
        if (isTokenConfigured(alertmanagerProperties.getToken())
                && alertmanagerProperties.getOrganizationId() == null) {
            throw new IllegalStateException(AlertmanagerConstant.TOKEN_WITHOUT_ORGANIZATION);
        }
        this.alertmanagerProperties = alertmanagerProperties;
    }

    /**
     * Returns the organization bound to the bearer token in the Authorization
     * header.
     *
     * @param authorizationHeader the raw Authorization header, nullable
     * @return empty when no token is configured, the header is missing, the scheme
     *         is not Bearer or the token differs; the bound organization otherwise
     */
    public Optional<UUID> resolveOrganizationId(String authorizationHeader) {
        String configuredToken = alertmanagerProperties.getToken();
        if (!isTokenConfigured(configuredToken)) {
            return Optional.empty();
        }

        if (authorizationHeader == null || !authorizationHeader.startsWith(AlertmanagerConstant.BEARER_PREFIX)) {
            return Optional.empty();
        }

        String presentedToken = authorizationHeader.substring(AlertmanagerConstant.BEARER_PREFIX.length());
        boolean matches = MessageDigest.isEqual(
                presentedToken.getBytes(StandardCharsets.UTF_8),
                configuredToken.getBytes(StandardCharsets.UTF_8));
        return matches ? Optional.of(alertmanagerProperties.getOrganizationId()) : Optional.empty();
    }

    private static boolean isTokenConfigured(String token) {
        return token != null && !token.isBlank();
    }
}
