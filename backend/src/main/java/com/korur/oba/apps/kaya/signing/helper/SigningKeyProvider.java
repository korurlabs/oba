package com.korur.oba.apps.kaya.signing.helper;

// 1. Framework-level
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.Getter;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.EdECPrivateKey;
import java.security.spec.NamedParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

// 4. Internal
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.signing.SigningProperties;
import com.korur.oba.apps.kaya.signing.constant.SigningConstant;
import com.korur.oba.apps.kaya.signing.dto.embedded.SigningKey;

/**
 * Holds the release signing key, read once at startup. Without a configured key the backend
 * refuses to start, because a release signed by a throw-away key could never be verified by a CLI
 * and every environment, local runs included, gets its key from Kagi. Only the test profile signs
 * with a key generated at startup, so the integration tests need no secret, and says so in the log.
 *
 * The JDK has no call that derives an Ed25519 public key from a private one, so the configured
 * private key's seed is fed to the key pair generator, which derives the pair from exactly those
 * 32 bytes; a sign and verify round trip at startup proves the pair matches.
 */
@Component
public class SigningKeyProvider {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(SigningKeyProvider.class);

    @Getter
    private final String keyId;
    @Getter
    private final PrivateKey privateKey;
    private final List<SigningKey> trustedKeys;

    public SigningKeyProvider(SigningProperties properties, Environment environment,
                                    LoggingService loggingService) {
        KeyPair keyPair;
        if (isBlank(properties.privateKey())) {
            if (!environment.acceptsProfiles(Profiles.of(SigningConstant.EPHEMERAL_KEY_PROFILE))) {
                throw new IllegalStateException(SigningConstant.KEY_MISSING_MESSAGE);
            }
            keyPair = generate(null);
            this.keyId = SigningConstant.EPHEMERAL_KEY_ID_PREFIX + fingerprint(keyPair.getPublic());
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, SigningConstant.EPHEMERAL_KEY_LOG, keyId);
        } else {
            if (isBlank(properties.keyId()) || properties.keyId().length() > SigningConstant.KEY_ID_MAX_LENGTH) {
                throw new IllegalStateException(SigningConstant.KEY_ID_INVALID_MESSAGE);
            }
            keyPair = generate(seedOf(properties.privateKey()));
            this.keyId = properties.keyId();
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, SigningConstant.CONFIGURED_KEY_LOG, keyId);
        }
        requireRoundTrip(keyPair);
        this.privateKey = keyPair.getPrivate();

        List<SigningKey> keys = new ArrayList<>();
        keys.add(new SigningKey(keyId, keyPair.getPublic(), parseInstant(properties.validFrom()), null));
        if (!isBlank(properties.previousPublicKey())) {
            if (isBlank(properties.previousKeyId()) || properties.previousKeyId().equals(keyId)) {
                throw new IllegalStateException(SigningConstant.PREVIOUS_KEY_ID_INVALID_MESSAGE);
            }
            keys.add(new SigningKey(properties.previousKeyId(), publicKeyOf(properties.previousPublicKey()),
                    null, parseInstant(properties.previousValidTo())));
        }
        this.trustedKeys = List.copyOf(keys);
    }

    /**
     * The keys a release may be verified with: the signing key first, then the replaced one.
     */
    public List<SigningKey> getTrustedKeys() {
        return trustedKeys;
    }

    /**
     * Finds a trusted key by id.
     *
     * @param id the key id stored with a release
     * @return the key, or empty when no trusted key has that id
     */
    public Optional<SigningKey> findTrustedKey(String id) {
        return trustedKeys.stream().filter(key -> key.keyId().equals(id)).findFirst();
    }

    /**
     * The raw 32-byte public key, base64: the form Go's crypto/ed25519 reads in the CLI.
     */
    public static String rawPublicKey(PublicKey publicKey) {
        byte[] encoded = publicKey.getEncoded();
        byte[] raw = Arrays.copyOfRange(encoded, encoded.length - SigningConstant.RAW_KEY_LENGTH, encoded.length);
        return Base64.getEncoder().encodeToString(raw);
    }

    private static KeyPair generate(byte[] seed) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(SigningConstant.ALGORITHM);
            generator.initialize(NamedParameterSpec.ED25519, seed == null ? new SecureRandom() : new SeedRandom(seed));
            KeyPair keyPair = generator.generateKeyPair();
            if (seed != null && !Arrays.equals(seed, ((EdECPrivateKey) keyPair.getPrivate()).getBytes().orElseThrow())) {
                throw new IllegalStateException(SigningConstant.KEY_INVALID_MESSAGE);
            }
            return keyPair;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(SigningConstant.KEY_INVALID_MESSAGE, e);
        }
    }

    private static byte[] seedOf(String configured) {
        try {
            byte[] der = Base64.getMimeDecoder().decode(configured
                    .replace(SigningConstant.PEM_BEGIN, "")
                    .replace(SigningConstant.PEM_END, "")
                    .strip());
            PrivateKey key = KeyFactory.getInstance(SigningConstant.ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(der));
            return ((EdECPrivateKey) key).getBytes()
                    .orElseThrow(() -> new IllegalStateException(SigningConstant.KEY_INVALID_MESSAGE));
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException e) {
            throw new IllegalStateException(SigningConstant.KEY_INVALID_MESSAGE, e);
        }
    }

    private static PublicKey publicKeyOf(String rawBase64) {
        try {
            byte[] raw = Base64.getDecoder().decode(rawBase64.strip());
            if (raw.length != SigningConstant.RAW_KEY_LENGTH) {
                throw new IllegalStateException(SigningConstant.PREVIOUS_KEY_INVALID_MESSAGE);
            }
            byte[] prefix = HexFormat.of().parseHex(SigningConstant.X509_ED25519_PREFIX_HEX);
            byte[] encoded = Arrays.copyOf(prefix, prefix.length + raw.length);
            System.arraycopy(raw, 0, encoded, prefix.length, raw.length);
            return KeyFactory.getInstance(SigningConstant.ALGORITHM).generatePublic(new X509EncodedKeySpec(encoded));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException(SigningConstant.PREVIOUS_KEY_INVALID_MESSAGE, e);
        }
    }

    private static void requireRoundTrip(KeyPair keyPair) {
        try {
            byte[] probe = SigningConstant.ROUND_TRIP_PROBE.getBytes(StandardCharsets.UTF_8);
            Signature signer = Signature.getInstance(SigningConstant.ALGORITHM);
            signer.initSign(keyPair.getPrivate());
            signer.update(probe);
            byte[] signature = signer.sign();
            Signature verifier = Signature.getInstance(SigningConstant.ALGORITHM);
            verifier.initVerify(keyPair.getPublic());
            verifier.update(probe);
            if (!verifier.verify(signature)) {
                throw new IllegalStateException(SigningConstant.KEY_INVALID_MESSAGE);
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(SigningConstant.KEY_INVALID_MESSAGE, e);
        }
    }

    private static String fingerprint(PublicKey publicKey) {
        try {
            byte[] digest = MessageDigest.getInstance(SigningConstant.DIGEST).digest(publicKey.getEncoded());
            return HexFormat.of().formatHex(digest).substring(0, SigningConstant.FINGERPRINT_LENGTH);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(SigningConstant.KEY_INVALID_MESSAGE, e);
        }
    }

    private static Instant parseInstant(String value) {
        return isBlank(value) ? null : Instant.parse(value.strip());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Hands the generator the configured seed instead of random bytes.
     */
    private static final class SeedRandom extends SecureRandom {

        private final byte[] seed;

        private SeedRandom(byte[] seed) {
            this.seed = seed.clone();
        }

        @Override
        public void nextBytes(byte[] bytes) {
            if (bytes.length != seed.length) {
                throw new IllegalStateException(SigningConstant.KEY_INVALID_MESSAGE);
            }
            System.arraycopy(seed, 0, bytes, 0, seed.length);
        }
    }
}
