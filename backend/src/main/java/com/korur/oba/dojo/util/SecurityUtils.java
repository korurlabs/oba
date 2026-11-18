package com.korur.oba.dojo.util;

// 1. Framework-level
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.Optional;

import com.korur.oba.dojo.exception.internal.auth.UnauthorizedException;
import com.korur.oba.dojo.exception.enums.ExceptionSecurityError;

/**
 * Utility class for SecurityContext-related operations.
 */
public final class SecurityUtils {

    private SecurityUtils() {
        // Prevent instantiation
    }

    /**
     * Extracts the Keycloak user ID (sub claim) from the current JWT token.
     *
     * @return The Keycloak ID from the JWT subject claim (never null or blank)
     * @throws UnauthorizedException if no authentication, no JWT, or missing/blank sub claim
     */
    public static String getKeycloakIdFromToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new UnauthorizedException(ExceptionSecurityError.UNAUTHORIZED);
        }

        String keycloakId = jwt.getClaimAsString("sub");
        if (keycloakId == null || keycloakId.isBlank()) {
            throw new UnauthorizedException(ExceptionSecurityError.UNAUTHORIZED);
        }

        return keycloakId;
    }

    /**
     * Extracts the Keycloak user ID (sub claim) from the current JWT token,
     * returning empty if no authentication is present.
     *
     * <p>Use this variant in contexts where missing authentication is expected
     * (e.g., logging, public endpoints). For contexts that require authentication,
     * use {@link #getKeycloakIdFromToken()} instead.</p>
     *
     * @return Optional containing the Keycloak ID, or empty if unauthenticated or missing sub claim
     */
    public static Optional<String> findKeycloakIdFromToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }

        String keycloakId = jwt.getClaimAsString("sub");
        if (keycloakId == null || keycloakId.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(keycloakId);
    }

    /**
     * Reads the expiry (exp claim) of the current JWT token, so state derived from the token, such
     * as a realtime session, can end when the token does.
     *
     * Empty when the token carries no exp claim; the caller decides whether such a token is
     * acceptable, because a token that never expires would let derived state live forever.
     *
     * @return Optional containing the token expiry, or empty if the token has no exp claim
     * @throws UnauthorizedException if no authentication or no JWT is present
     */
    public static Optional<Instant> getTokenExpiresAt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new UnauthorizedException(ExceptionSecurityError.UNAUTHORIZED);
        }

        return Optional.ofNullable(jwt.getExpiresAt());
    }

    /**
     * Strips newlines and control characters to prevent log injection attacks.
     * Use this for any value originating from external input (e.g., JWT claims)
     * before writing it to logs.
     *
     * @param value the raw string to sanitize
     * @return sanitized string safe for logging, or {@code "[NULL]"} if input is null
     */
    public static String sanitizeForLog(String value) {
        if (value == null) {
            return "[NULL]";
        }
        return value.replaceAll("[\\r\\n\\t\\p{Cntrl}]", "_");
    }
}
