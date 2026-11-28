package com.korur.oba.connect.oidc.helper;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

// 2. Third-party
import com.nimbusds.jose.jwk.JWK;

// 3. Java SDK
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;

// 4. Internal
import com.korur.oba.connect.oidc.properties.OidcIssuerProperties;
import com.korur.oba.dojo.logging.service.LoggingService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class OidcSigningKeyProviderUnitTest {

    private static final String ISSUER = "https://issuer.example.com";

    private final LoggingService loggingService = mock(LoggingService.class);

    private static KeyPair newKeyPair(int bits) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(bits);
        return generator.generateKeyPair();
    }

    private static String base64(byte[] der) {
        return Base64.getEncoder().encodeToString(der);
    }

    private static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder().encodeToString(der) + "\n-----END " + type + "-----\n";
    }

    private OidcSigningKeyProvider provider(OidcIssuerProperties properties, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new OidcSigningKeyProvider(properties, environment, loggingService);
    }

    @Test
    void base64DerKey_isAvailableWithItsKeyId() throws Exception {
        KeyPair keyPair = newKeyPair(2048);

        OidcSigningKeyProvider provider = provider(new OidcIssuerProperties(ISSUER,
                base64(keyPair.getPrivate().getEncoded()), "oba-1", null, null));

        assertTrue(provider.isAvailable());
        assertEquals("oba-1", provider.getSigningKey().getKeyID());
        assertEquals(keyPair.getPublic(), provider.getSigningKey().toRSAPublicKey());
    }

    @Test
    void pemKey_isAccepted() throws Exception {
        KeyPair keyPair = newKeyPair(2048);

        OidcSigningKeyProvider provider = provider(new OidcIssuerProperties(ISSUER,
                pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()), "oba-1", null, null));

        assertEquals(keyPair.getPublic(), provider.getSigningKey().toRSAPublicKey());
    }

    @Test
    void keyUnder2048Bits_isRefused() throws Exception {
        KeyPair keyPair = newKeyPair(1024);
        OidcIssuerProperties properties = new OidcIssuerProperties(ISSUER,
                base64(keyPair.getPrivate().getEncoded()), "oba-1", null, null);

        assertThrows(IllegalStateException.class, () -> provider(properties));
    }

    @Test
    void httpIssuer_isRefused() throws Exception {
        KeyPair keyPair = newKeyPair(2048);
        OidcIssuerProperties properties = new OidcIssuerProperties("http://issuer.example.com",
                base64(keyPair.getPrivate().getEncoded()), "oba-1", null, null);

        assertThrows(IllegalStateException.class, () -> provider(properties));
    }

    @Test
    void issuerWithTrailingSlash_isRefused() throws Exception {
        KeyPair keyPair = newKeyPair(2048);
        OidcIssuerProperties properties = new OidcIssuerProperties(ISSUER + "/",
                base64(keyPair.getPrivate().getEncoded()), "oba-1", null, null);

        assertThrows(IllegalStateException.class, () -> provider(properties));
    }

    @Test
    void issuerWithoutKey_outsideTestProfile_failsTheStart() {
        OidcIssuerProperties properties = new OidcIssuerProperties(ISSUER, null, null, null, null);

        assertThrows(IllegalStateException.class, () -> provider(properties, "prod"));
    }

    @Test
    void issuerWithoutKey_underTestProfile_signsWithAGeneratedKey() {
        OidcSigningKeyProvider provider = provider(new OidcIssuerProperties(ISSUER, null, null, null, null), "test");

        assertTrue(provider.isAvailable());
        assertTrue(provider.getSigningKey().getKeyID().startsWith("test-"));
    }

    @Test
    void noIssuerAndNoKey_isNotAvailable() {
        OidcSigningKeyProvider provider = provider(new OidcIssuerProperties(null, null, null, null, null));

        assertFalse(provider.isAvailable());
        assertNull(provider.getSigningKey());
        assertTrue(provider.getPublicKeySet().getKeys().isEmpty());
    }

    @Test
    void publishedKey_isListedSecondWithoutPrivateHalf() throws Exception {
        KeyPair signing = newKeyPair(2048);
        KeyPair published = newKeyPair(2048);

        OidcSigningKeyProvider provider = provider(new OidcIssuerProperties(ISSUER,
                base64(signing.getPrivate().getEncoded()), "oba-2",
                pem("PUBLIC KEY", published.getPublic().getEncoded()), "oba-1"));

        List<JWK> keys = provider.getPublicKeySet().getKeys();
        assertEquals(2, keys.size());
        assertEquals("oba-2", keys.get(0).getKeyID());
        assertEquals("oba-1", keys.get(1).getKeyID());
        assertEquals(published.getPublic(), keys.get(1).toRSAKey().toRSAPublicKey());
        for (JWK key : keys) {
            assertFalse(key.isPrivate());
        }
    }

    @Test
    void publishedKeyWithTheSigningKeyId_isRefused() throws Exception {
        KeyPair signing = newKeyPair(2048);
        KeyPair published = newKeyPair(2048);
        OidcIssuerProperties properties = new OidcIssuerProperties(ISSUER,
                base64(signing.getPrivate().getEncoded()), "oba-1",
                base64(published.getPublic().getEncoded()), "oba-1");

        assertThrows(IllegalStateException.class, () -> provider(properties));
    }
}
