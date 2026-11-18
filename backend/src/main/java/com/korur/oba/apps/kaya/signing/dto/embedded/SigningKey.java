package com.korur.oba.apps.kaya.signing.dto.embedded;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.security.PublicKey;
import java.time.Instant;

// 4. Internal

/**
 * A public key a release may be verified with.
 *
 * @param keyId     the id stored with each release it signed
 * @param publicKey the Ed25519 public key
 * @param validFrom from when it signs; null when not configured
 * @param validTo   until when it is trusted; null for the signing key
 */
public record SigningKey(
        String keyId,
        PublicKey publicKey,
        Instant validFrom,
        Instant validTo
) {
}
