package com.korur.oba.dojo.util;

// 1. Framework-level

// 2. Third-party
import org.apache.commons.codec.digest.DigestUtils;

// 3. Java SDK
import java.nio.charset.StandardCharsets;

// 4. Internal
import com.korur.oba.dojo.exception.internal.infrastructure.InternalServerErrorException;
import com.korur.oba.shrine.identity.exception.ExceptionDomainIdentityError;

/**
 * Utility class for authentication-related operations.
 */
public final class AuthenticationUtil {

    private static final int SHA256_HEX_LENGTH = 64;

    private AuthenticationUtil() {
        // Prevent instantiation
    }

    /**
     * Hashes a raw token using SHA-256 algorithm.
     *
     * @param rawToken The raw token to hash
     * @return The SHA-256 hash as a 64-character hexadecimal string
     * @throws InternalServerErrorException if the token is null or hash output is invalid
     */
    public static String hashToken(String rawToken) {
        // Validate raw token is not null
        if (rawToken == null || rawToken.isBlank()) {
            throw new InternalServerErrorException(
                    ExceptionDomainIdentityError.INT_TOKEN_HASH_NULL_INPUT);
        }

        // Convert to UTF-8 bytes for platform independence
        byte[] tokenBytes = rawToken.getBytes(StandardCharsets.UTF_8);

        // Hash using SHA-256 (returns 64-character hex string)
        String hash = DigestUtils.sha256Hex(tokenBytes);

        // Guard: Verify output is exactly 64 hex characters
        if (hash == null || hash.length() != SHA256_HEX_LENGTH) {
            throw new InternalServerErrorException(
                    ExceptionDomainIdentityError.INT_TOKEN_HASH_INVALID_OUTPUT);
        }

        return hash;
    }
}
