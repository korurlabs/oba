package com.korur.oba.apps.kaya.delivery.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

// 4. Internal
import com.korur.oba.apps.kaya.delivery.constant.DeliveryConstant;

/**
 * Hashes for delivery: the ETag of a checkout's current state and the hash of a rebuilt signed
 * manifest.
 */
public final class DeliveryEtagHelper {

    private DeliveryEtagHelper() {
    }

    /**
     * The ETag of what a checkout must hold: the newest release's manifest hash. A new release
     * changes it, so the CLI's If-None-Match answers 304 only when it already holds that release.
     *
     * @param manifestSha256 the newest release's manifest hash, or null without a release
     * @return 64 hex characters, unquoted
     */
    public static String etagOf(String manifestSha256) {
        return sha256Hex(manifestSha256 == null ? DeliveryConstant.ETAG_NO_RELEASE : manifestSha256);
    }

    /**
     * @param text a text
     * @return the lower-case hex SHA-256 of its UTF-8 bytes
     */
    public static String sha256Hex(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(DeliveryConstant.SHA256_ALGORITHM)
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            // Every Java platform must provide SHA-256, so this means a broken runtime.
            throw new IllegalStateException(exception);
        }
    }
}
