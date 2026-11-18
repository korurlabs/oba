package com.korur.oba.connect.github.mock_data;

// 1. Framework-level
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.github.constant.GithubConstant;
import com.korur.oba.connect.github.properties.GithubProperties;

/**
 * Test data for the GitHub transport: a freshly generated RSA key (the tests never hold a real
 * GitHub App key), properties around it, a movable clock and a bound-ready RestClient builder.
 */
public final class GithubMocks {

    public static final String APP_ID = "424242";
    public static final String INSTALLATION_ID = "777";
    public static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");

    private GithubMocks() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /**
     * The key as the configuration holds it: base64 of a PKCS#8 PEM.
     */
    public static String privateKeyConfig(KeyPair keyPair) {
        return Base64.getEncoder().encodeToString(pkcs8Pem(keyPair).getBytes(StandardCharsets.UTF_8));
    }

    public static String pkcs8Pem(KeyPair keyPair) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(keyPair.getPrivate().getEncoded());
        return GithubConstant.PEM_PKCS8_HEADER + "\n" + body + "\n" + GithubConstant.PEM_PKCS8_FOOTER + "\n";
    }

    public static RSAPublicKey publicKey(KeyPair keyPair) {
        return (RSAPublicKey) keyPair.getPublic();
    }

    public static GithubProperties properties(String privateKey, UUID organizationId) {
        GithubProperties properties = new GithubProperties();
        properties.setAppId(APP_ID);
        properties.setInstallationId(INSTALLATION_ID);
        properties.setPrivateKey(privateKey);
        properties.setOrganizationId(organizationId);
        return properties;
    }

    public static RestClient.Builder restClientBuilder() {
        return RestClient.builder()
                .baseUrl(GithubConstant.API_BASE_URL)
                .requestFactory(new SimpleClientHttpRequestFactory());
    }

    public static MutableClock clock() {
        return new MutableClock(NOW);
    }

    /**
     * A clock the test moves forward.
     */
    public static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        public void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
