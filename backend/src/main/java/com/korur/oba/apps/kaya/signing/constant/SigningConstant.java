package com.korur.oba.apps.kaya.signing.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Algorithms, encodings and messages of release signing. Key material is never logged, only key ids.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class SigningConstant {

    private SigningConstant() {
    }

    // --- Algorithms and encodings ---
    public static final String ALGORITHM = "Ed25519";
    public static final String DIGEST = "SHA-256";
    public static final int RAW_KEY_LENGTH = 32;
    public static final int KEY_ID_MAX_LENGTH = 40;
    public static final int FINGERPRINT_LENGTH = 16;
    public static final String EPHEMERAL_KEY_ID_PREFIX = "test-";
    /** The only profile that may sign with a key generated at startup. */
    public static final String EPHEMERAL_KEY_PROFILE = "test";
    public static final String PEM_BEGIN = "-----BEGIN PRIVATE KEY-----";
    public static final String PEM_END = "-----END PRIVATE KEY-----";
    public static final String X509_ED25519_PREFIX_HEX = "302a300506032b6570032100";
    public static final String ROUND_TRIP_PROBE = "kaya-config-signing-probe";

    // --- Startup failures ---
    public static final String KEY_MISSING_MESSAGE =
            "kaya.config.signing.private-key (CONFIG_SIGNING_KEY) is required; set it in the Kagi app /kaya/api";
    public static final String KEY_ID_INVALID_MESSAGE =
            "kaya.config.signing.key-id (CONFIG_SIGNING_KEY_ID) is required with a signing key and has at most 40 characters";
    public static final String KEY_INVALID_MESSAGE =
            "kaya.config.signing.private-key is not an Ed25519 PKCS#8 private key, base64 DER or PEM";
    public static final String PREVIOUS_KEY_ID_INVALID_MESSAGE =
            "kaya.config.signing.previous-key-id is required with a previous public key and differs from the current key id";
    public static final String PREVIOUS_KEY_INVALID_MESSAGE =
            "kaya.config.signing.previous-public-key is not a raw 32-byte Ed25519 public key in base64";

    // --- Logs ---
    public static final String EPHEMERAL_KEY_LOG =
            "No release signing key configured; signing with the ephemeral key {}, which no machine trusts after a restart";
    public static final String CONFIGURED_KEY_LOG = "Signing releases with key {}";
    public static final String SIGNING_FAILED_LOG = "Signing the release of {} numbered {} failed";
    public static final String VERIFY_FAILED_LOG = "A signature by key {} could not be checked: {}";
}
