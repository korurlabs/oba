package com.korur.oba.apps.kaya.signing.dto.embedded;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * The signature of one canonical document.
 *
 * @param canonicalJson  the exact signed text; delivery serves these bytes, never a re-serialisation
 * @param manifestSha256 lower-case hex SHA-256 of the UTF-8 bytes of canonicalJson
 * @param signature      base64 Ed25519 signature of those bytes, 88 characters
 * @param keyId          the key that signed
 */
public record Signature(
        String canonicalJson,
        String manifestSha256,
        String signature,
        String keyId
) {
}
