package com.korur.oba.connect.oidc.helper;

// 1. Framework-level
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

// 2. Third-party
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

// 3. Java SDK
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

// 4. Internal
import com.korur.oba.connect.oidc.constant.OidcConstant;
import com.korur.oba.connect.oidc.properties.OidcIssuerProperties;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

import static com.korur.oba.connect.oidc.constant.helper.OidcSigningKeyProviderLogConstant.*;

/**
 * Holds the issuer's signing key and the key set it publishes, read once at startup.
 *
 * A deployment without an issuer URL runs without cloud connections. A deployment with one must
 * also configure a key, or the backend refuses to start: an assertion signed by a throw-away key
 * would stop verifying at the next restart and break every connection without a visible cause.
 * Only the test profile signs with a key generated at startup, so the integration tests need no
 * secret, and says so in the log.
 *
 * Every key must be RSA of at least 2048 bits and the issuer URL must be https, because cloud
 * providers fetch the key set from that URL and refuse anything weaker. The key set lists the
 * signing key first and the published key, the next or the replaced one during a rotation,
 * second.
 */
@Component
public class OidcSigningKeyProvider {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(OidcSigningKeyProvider.class);

    private final String issuerUrl;
    private final RSAKey signingKey;
    private final JWKSet publicKeySet;

    public OidcSigningKeyProvider(OidcIssuerProperties properties, Environment environment,
                                  LoggingService loggingService) {
        this.issuerUrl = isBlank(properties.issuerUrl()) ? null : requireIssuerUrl(properties.issuerUrl().strip());
        boolean ephemeralAllowed = environment.acceptsProfiles(Profiles.of(OidcConstant.EPHEMERAL_KEY_PROFILE));

        if (isBlank(properties.signingKey())) {
            if (ephemeralAllowed) {
                KeyPair keyPair = generate();
                RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
                String keyId = OidcConstant.EPHEMERAL_KEY_ID_PREFIX + fingerprint(publicKey);
                this.signingKey = toJwk(publicKey, (RSAPrivateKey) keyPair.getPrivate(), keyId);
                loggingService.warn(LOG_PREFIX, LogLayer.HELPER, EPHEMERAL_KEY, keyId);
            } else if (issuerUrl != null) {
                throw new IllegalStateException(OidcConstant.KEY_MISSING_MESSAGE);
            } else {
                this.signingKey = null;
                loggingService.info(LOG_PREFIX, LogLayer.HELPER, ISSUER_DISABLED);
            }
        } else {
            String keyId = requireKeyId(properties.signingKeyId(), null, OidcConstant.KEY_ID_INVALID_MESSAGE);
            RSAPrivateCrtKey privateKey = privateKeyOf(properties.signingKey());
            this.signingKey = toJwk(publicKeyOf(privateKey), privateKey, keyId);
            if (issuerUrl == null) {
                loggingService.warn(LOG_PREFIX, LogLayer.HELPER, KEY_WITHOUT_ISSUER, keyId);
            } else {
                loggingService.info(LOG_PREFIX, LogLayer.HELPER, CONFIGURED_KEY, issuerUrl, keyId);
            }
        }

        List<JWK> keys = new ArrayList<>();
        if (signingKey != null) {
            keys.add(signingKey);
        }
        if (!isBlank(properties.publishedPublicKey())) {
            String publishedKeyId = requireKeyId(properties.publishedKeyId(),
                    signingKey == null ? null : signingKey.getKeyID(), OidcConstant.PUBLISHED_KEY_ID_INVALID_MESSAGE);
            keys.add(toJwk(publishedKeyOf(properties.publishedPublicKey()), null, publishedKeyId));
            loggingService.info(LOG_PREFIX, LogLayer.HELPER, PUBLISHED_KEY, publishedKeyId);
        }
        this.publicKeySet = new JWKSet(keys).toPublicJWKSet();
    }

    /**
     * Whether the issuer can sign: an issuer URL is set and a key is held.
     */
    public boolean isAvailable() {
        return issuerUrl != null && signingKey != null;
    }

    /**
     * The issuer URL, or null when none is configured.
     */
    public String getIssuerUrl() {
        return issuerUrl;
    }

    /**
     * The signing key with its private half and kid, or null when none is held. Never logged or
     * returned to a client.
     */
    public RSAKey getSigningKey() {
        return signingKey;
    }

    /**
     * The public halves of the signing key and of the published key, in that order.
     */
    public JWKSet getPublicKeySet() {
        return publicKeySet;
    }

    private static String requireIssuerUrl(String value) {
        try {
            URI uri = new URI(value);
            if (!OidcConstant.ISSUER_SCHEME.equals(uri.getScheme()) || isBlank(uri.getHost())
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || value.endsWith(OidcConstant.PATH_SEPARATOR)) {
                throw new IllegalStateException(OidcConstant.ISSUER_URL_INVALID_MESSAGE);
            }
            return value;
        } catch (URISyntaxException e) {
            throw new IllegalStateException(OidcConstant.ISSUER_URL_INVALID_MESSAGE, e);
        }
    }

    private static String requireKeyId(String value, String otherKeyId, String message) {
        if (isBlank(value)) {
            throw new IllegalStateException(message);
        }
        String keyId = value.strip();
        if (keyId.length() > OidcConstant.KEY_ID_MAX_LENGTH || keyId.equals(otherKeyId)) {
            throw new IllegalStateException(message);
        }
        return keyId;
    }

    private static RSAKey toJwk(RSAPublicKey publicKey, RSAPrivateKey privateKey, String keyId) {
        RSAKey.Builder builder = new RSAKey.Builder(publicKey)
                .keyID(keyId)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256);
        if (privateKey != null) {
            builder.privateKey(privateKey);
        }
        return builder.build();
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(OidcConstant.KEY_ALGORITHM);
            generator.initialize(OidcConstant.MIN_KEY_SIZE_BITS);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(OidcConstant.KEY_INVALID_MESSAGE, e);
        }
    }

    private static RSAPrivateCrtKey privateKeyOf(String configured) {
        RSAPrivateCrtKey key;
        try {
            byte[] der = decode(configured, OidcConstant.PEM_PRIVATE_BEGIN, OidcConstant.PEM_PRIVATE_END);
            key = (RSAPrivateCrtKey) KeyFactory.getInstance(OidcConstant.KEY_ALGORITHM)
                    .generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException e) {
            throw new IllegalStateException(OidcConstant.KEY_INVALID_MESSAGE, e);
        }
        requireKeySize(key.getModulus().bitLength());
        return key;
    }

    private static RSAPublicKey publicKeyOf(RSAPrivateCrtKey privateKey) {
        try {
            return (RSAPublicKey) KeyFactory.getInstance(OidcConstant.KEY_ALGORITHM)
                    .generatePublic(new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(OidcConstant.KEY_INVALID_MESSAGE, e);
        }
    }

    private static RSAPublicKey publishedKeyOf(String configured) {
        RSAPublicKey key;
        try {
            byte[] der = decode(configured, OidcConstant.PEM_PUBLIC_BEGIN, OidcConstant.PEM_PUBLIC_END);
            key = (RSAPublicKey) KeyFactory.getInstance(OidcConstant.KEY_ALGORITHM)
                    .generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException e) {
            throw new IllegalStateException(OidcConstant.PUBLISHED_KEY_INVALID_MESSAGE, e);
        }
        requireKeySize(key.getModulus().bitLength());
        return key;
    }

    private static void requireKeySize(int bits) {
        if (bits < OidcConstant.MIN_KEY_SIZE_BITS) {
            throw new IllegalStateException(OidcConstant.KEY_TOO_SHORT_MESSAGE);
        }
    }

    /**
     * Reads a PEM or the base64 of its DER body; a PEM may arrive with or without line breaks,
     * which the MIME decoder skips.
     */
    private static byte[] decode(String configured, String pemBegin, String pemEnd) {
        return Base64.getMimeDecoder().decode(configured
                .replace(pemBegin, "")
                .replace(pemEnd, "")
                .strip());
    }

    private static String fingerprint(RSAPublicKey publicKey) {
        try {
            byte[] digest = MessageDigest.getInstance(OidcConstant.DIGEST).digest(publicKey.getEncoded());
            return HexFormat.of().formatHex(digest).substring(0, OidcConstant.FINGERPRINT_LENGTH);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(OidcConstant.KEY_INVALID_MESSAGE, e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
