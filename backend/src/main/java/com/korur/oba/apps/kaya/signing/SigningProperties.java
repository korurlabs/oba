package com.korur.oba.apps.kaya.signing;

// 1. Framework-level
import org.springframework.boot.context.properties.ConfigurationProperties;

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * The Ed25519 key that signs releases, and the public key of the key it replaced. Kagi supplies
 * CONFIG_SIGNING_KEY and CONFIG_SIGNING_KEY_ID from the Kagi app /oba/api, through kagi-init in
 * production and 'kagi run' locally; the private key never leaves Kagi and the backend process. During a key rotation the old public key stays
 * listed until validTo, so a CLI holding a release signed with it still verifies that release.
 *
 * @param privateKey        PKCS#8 Ed25519 private key, base64 DER or PEM; required except under
 *                          the test profile, which signs with a key generated at startup
 * @param keyId             id of that key, 1 to 40 characters, stored with each release
 * @param validFrom         ISO-8601 instant from which the key signs; optional
 * @param previousPublicKey raw 32-byte Ed25519 public key of the replaced key, base64; optional
 * @param previousKeyId     id of the replaced key; set together with previousPublicKey
 * @param previousValidTo   ISO-8601 instant until which the replaced key is trusted; optional
 */
@ConfigurationProperties(prefix = "kaya.config.signing")
public record SigningProperties(
        String privateKey,
        String keyId,
        String validFrom,
        String previousPublicKey,
        String previousKeyId,
        String previousValidTo
) {
}
