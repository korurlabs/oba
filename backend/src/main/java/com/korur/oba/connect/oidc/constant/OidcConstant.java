package com.korur.oba.connect.oidc.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Duration;
import java.util.List;

// 4. Internal

/**
 * Algorithms, lifetimes, document values and startup failures of the OpenID Connect issuer. Key
 * material is never logged, only key ids.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class OidcConstant {

    private OidcConstant() {
    }

    // --- Properties ---
    public static final String PROPERTY_PREFIX = "oba.oidc";

    // --- Algorithms and encodings ---
    public static final String KEY_ALGORITHM = "RSA";
    public static final String DIGEST = "SHA-256";
    /** Below this modulus length a key is refused, as cloud providers refuse to trust it. */
    public static final int MIN_KEY_SIZE_BITS = 2048;
    public static final int KEY_ID_MAX_LENGTH = 64;
    public static final int FINGERPRINT_LENGTH = 16;
    public static final String EPHEMERAL_KEY_ID_PREFIX = "test-";
    /** The only profile that may sign with a key generated at startup. */
    public static final String EPHEMERAL_KEY_PROFILE = "test";
    public static final String PEM_PRIVATE_BEGIN = "-----BEGIN PRIVATE KEY-----";
    public static final String PEM_PRIVATE_END = "-----END PRIVATE KEY-----";
    public static final String PEM_PUBLIC_BEGIN = "-----BEGIN PUBLIC KEY-----";
    public static final String PEM_PUBLIC_END = "-----END PUBLIC KEY-----";
    public static final String ISSUER_SCHEME = "https";
    public static final String PATH_SEPARATOR = "/";

    // --- Assertions ---
    /** Short enough that a leaked assertion is useless within minutes, long enough for one token exchange. */
    public static final Duration ASSERTION_LIFETIME = Duration.ofMinutes(5);

    // --- Discovery document ---
    public static final List<String> RESPONSE_TYPES_SUPPORTED = List.of("id_token");
    public static final List<String> SUBJECT_TYPES_SUPPORTED = List.of("public");
    public static final List<String> SIGNING_ALGORITHMS_SUPPORTED = List.of("RS256");
    /** Both documents change only on a deployment, so a provider may cache them for an hour. */
    public static final Duration DOCUMENT_CACHE_MAX_AGE = Duration.ofHours(1);

    // --- Startup failures ---
    public static final String KEY_MISSING_MESSAGE =
            "oba.oidc.signing-key (OBA_OIDC_SIGNING_KEY) is required when oba.oidc.issuer-url is set";
    public static final String KEY_ID_INVALID_MESSAGE =
            "oba.oidc.signing-key-id (OBA_OIDC_SIGNING_KEY_ID) is required with a signing key and has at most 64 characters";
    public static final String KEY_INVALID_MESSAGE =
            "oba.oidc.signing-key is not an RSA PKCS#8 private key, base64 DER or PEM";
    public static final String KEY_TOO_SHORT_MESSAGE =
            "oba.oidc keys must be RSA keys of at least 2048 bits";
    public static final String ISSUER_URL_INVALID_MESSAGE =
            "oba.oidc.issuer-url (OBA_OIDC_ISSUER_URL) must be an https URL with a host, no query, no fragment "
                    + "and no trailing slash";
    public static final String PUBLISHED_KEY_ID_INVALID_MESSAGE =
            "oba.oidc.published-key-id is required with a published public key, has at most 64 characters "
                    + "and differs from the signing key id";
    public static final String PUBLISHED_KEY_INVALID_MESSAGE =
            "oba.oidc.published-public-key is not an RSA X.509 public key, base64 DER or PEM";
}
