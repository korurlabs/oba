package com.korur.oba.apps.kaya.machine.helper;

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
import com.korur.oba.apps.kaya.machine.constant.MachineConstant;

/**
 * Creates recorder tokens and hashes them. A token is "kyt_" followed by 32 random bytes in
 * unpadded base64url, 47 characters in all. Kaya stores the lower-case hex SHA-256 of the whole
 * token string; 256 random bits need no salt or slow hash, because they cannot be guessed.
 */
@Component
public class MachineTokenHelper {

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Draws a new token.
     *
     * @return the plaintext token, to be shown once and never stored
     */
    public String generate() {
        byte[] bytes = new byte[MachineConstant.TOKEN_RANDOM_BYTES];
        secureRandom.nextBytes(bytes);
        return MachineConstant.TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Hashes a plaintext token the way it is stored.
     *
     * @param token the plaintext token
     * @return 64 lower-case hex characters
     */
    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance(MachineConstant.TOKEN_HASH_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            // Every Java platform must provide SHA-256, so this means a broken runtime.
            throw new IllegalStateException(exception);
        }
    }

    /**
     * The part of a token kept in clear, so a user can tell their tokens apart.
     *
     * @param token the plaintext token
     * @return the first 12 characters
     */
    public String displayPrefix(String token) {
        return token.substring(0, MachineConstant.TOKEN_DISPLAY_PREFIX_LENGTH);
    }

    /**
     * Whether a presented value has the shape of a token, checked before any lookup.
     *
     * @param token the presented value, possibly null
     * @return true when it starts with "kyt_" and has 47 characters
     */
    public boolean hasTokenFormat(String token) {
        return token != null
                && token.length() == MachineConstant.TOKEN_LENGTH
                && token.startsWith(MachineConstant.TOKEN_PREFIX);
    }
}
