package com.korur.oba.dojo.security.token.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

// 4. Internal
import com.korur.oba.dojo.security.token.constant.ApiTokenConstant;
import com.korur.oba.dojo.security.token.enums.ApiTokenKind;

/**
 * Creates API tokens and hashes them. A token is the prefix of its kind followed by 32 random bytes
 * in unpadded base64url. The lower-case hex SHA-256 of the whole token string is stored; 256 random
 * bits need no salt or slow hash, because they cannot be guessed.
 */
@Component
public class ApiTokenHelper {

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Draws a new token of a kind.
     *
     * @param kind the kind, which gives the prefix
     * @return the plaintext token, to be shown once and never stored
     */
    public String generate(ApiTokenKind kind) {
        byte[] bytes = new byte[ApiTokenConstant.TOKEN_RANDOM_BYTES];
        secureRandom.nextBytes(bytes);
        return kind.getPrefix() + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Hashes a plaintext token the way it is stored.
     *
     * @param token the plaintext token
     * @return 64 lower-case hex characters
     */
    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ApiTokenConstant.TOKEN_HASH_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            // Every Java platform must provide SHA-256, so this means a broken runtime.
            throw new IllegalStateException(exception);
        }
    }

    /**
     * The part of a token kept in clear, so a person can tell tokens apart.
     *
     * @param token the plaintext token
     * @return the first 12 characters
     */
    public String displayPrefix(String token) {
        return token.substring(0, ApiTokenConstant.TOKEN_DISPLAY_PREFIX_LENGTH);
    }

    /**
     * Whether a presented value has the shape of a token of a kind, checked before any lookup.
     *
     * @param token the presented value, possibly null
     * @param kind  the kind the value claims by its prefix
     * @return true when it starts with the kind's prefix and 43 base64url characters follow
     */
    public boolean hasTokenFormat(String token, ApiTokenKind kind) {
        if (token == null
                || !token.startsWith(kind.getPrefix())
                || token.length() != kind.getPrefix().length() + ApiTokenConstant.TOKEN_BODY_LENGTH) {
            return false;
        }
        return token.substring(kind.getPrefix().length()).chars().allMatch(ApiTokenHelper::isBase64UrlCharacter);
    }

    private static boolean isBase64UrlCharacter(int character) {
        return (character >= 'A' && character <= 'Z')
                || (character >= 'a' && character <= 'z')
                || (character >= '0' && character <= '9')
                || character == '-'
                || character == '_';
    }
}
