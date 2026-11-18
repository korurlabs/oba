package com.korur.oba.support;

// 1. Framework-level
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Stands in for the Keycloak decoder, which needs a live issuer, in every integration test. A
 * bearer token made by {@link #tokenOf} is turned into the JWT that the MockMvc requests of the
 * other tests carry, so the real filter chain authenticates it and the user lookup runs unchanged.
 *
 * Every other bearer token is refused as a bad JWT, the way Keycloak refuses it. The marker
 * prefix is what makes that hold: a machine token (kyt_ plus URL-safe base64) is itself valid
 * URL-safe base64, and about one in nine decodes to bytes with exactly one bar, which the decoder
 * would otherwise accept as a user and let the user chain answer 403 instead of 401.
 */
@TestConfiguration
public class TestJwtDecoderConfiguration {

    /** Marks a token made by tokenOf; the dot is outside URL-safe base64, so no machine token carries it. */
    private static final String TOKEN_MARKER = "test-jwt.";

    /** The marker plus URL-safe base64 of keycloakId|email, because a bearer token may hold no at sign or bar. */
    public static String tokenOf(String keycloakId, String email) {
        return TOKEN_MARKER + Base64.getUrlEncoder().withoutPadding()
                .encodeToString((keycloakId + "|" + email).getBytes(StandardCharsets.UTF_8));
    }

    @Bean
    JwtDecoder jwtDecoder() {
        return token -> {
            if (!token.startsWith(TOKEN_MARKER)) {
                throw new BadJwtException("Unknown test token");
            }
            String[] parts;
            try {
                parts = new String(Base64.getUrlDecoder().decode(token.substring(TOKEN_MARKER.length())),
                        StandardCharsets.UTF_8).split("\\|");
            } catch (IllegalArgumentException e) {
                throw new BadJwtException("Unknown test token", e);
            }
            if (parts.length != 2) {
                throw new BadJwtException("Unknown test token");
            }
            return Jwt.withTokenValue(token).header("alg", "none")
                    .subject(parts[0]).claim("email", parts[1]).claim("email_verified", true)
                    .issuedAt(Instant.now().minusSeconds(60)).expiresAt(Instant.now().plusSeconds(3600)).build();
        };
    }
}
