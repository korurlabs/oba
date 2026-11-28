package com.korur.oba.connect.oidc.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

// 2. Third-party
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;

// 4. Internal
import com.korur.oba.connect.oidc.dto.response.OidcDiscoveryResponse;
import com.korur.oba.connect.oidc.helper.OidcSigningKeyProvider;
import com.korur.oba.connect.oidc.properties.OidcIssuerProperties;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class OidcAssertionServiceImplUnitTest {

    private static final String ISSUER = "https://issuer.example.com";
    private static final Instant NOW = Instant.parse("2026-11-28T10:00:00Z");

    private final LoggingService loggingService = mock(LoggingService.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private OidcAssertionServiceImpl service(String issuerUrl) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        OidcSigningKeyProvider provider = new OidcSigningKeyProvider(
                new OidcIssuerProperties(issuerUrl, null, null, null, null), environment, loggingService);
        return new OidcAssertionServiceImpl(provider, clock, loggingService);
    }

    @Test
    void issue_signsRs256WithKidAndClaims_verifiableAgainstTheKeySet() throws Exception {
        OidcAssertionServiceImpl service = service(ISSUER);

        SignedJWT jwt = SignedJWT.parse(service.issue("subject-1", "audience-1"));

        JWKSet keySet = JWKSet.parse(service.getJwks());
        RSAKey key = (RSAKey) keySet.getKeyByKeyId(jwt.getHeader().getKeyID());
        assertNotNull(key);
        assertEquals(JWSAlgorithm.RS256, jwt.getHeader().getAlgorithm());
        assertTrue(jwt.verify(new RSASSAVerifier(key)));
        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        assertEquals(ISSUER, claims.getIssuer());
        assertEquals("subject-1", claims.getSubject());
        assertEquals(List.of("audience-1"), claims.getAudience());
        assertEquals(Date.from(NOW), claims.getIssueTime());
        assertEquals(Date.from(NOW), claims.getNotBeforeTime());
        assertEquals(Date.from(NOW.plusSeconds(300)), claims.getExpirationTime());
        assertNotNull(claims.getJWTID());
    }

    @Test
    void getJwks_holdsNoPrivateHalf() {
        Map<String, Object> jwks = service(ISSUER).getJwks();

        List<?> keys = (List<?>) jwks.get("keys");
        assertEquals(1, keys.size());
        Map<?, ?> key = (Map<?, ?>) keys.get(0);
        assertEquals("RSA", key.get("kty"));
        assertEquals("sig", key.get("use"));
        assertEquals("RS256", key.get("alg"));
        assertFalse(key.containsKey("d"));
    }

    @Test
    void getDiscovery_pointsAtTheKeySetBelowTheIssuer() {
        OidcDiscoveryResponse discovery = service(ISSUER).getDiscovery();

        assertEquals(ISSUER, discovery.issuer());
        assertEquals(ISSUER + "/.well-known/jwks.json", discovery.jwksUri());
        assertEquals(List.of("RS256"), discovery.idTokenSigningAlgValuesSupported());
    }

    @Test
    void withoutIssuer_everyCallIsNotFound() {
        OidcAssertionServiceImpl service = service(null);

        assertFalse(service.isConfigured());
        assertThrows(NotFoundException.class, service::getDiscovery);
        assertThrows(NotFoundException.class, service::getJwks);
        assertThrows(NotFoundException.class, () -> service.issue("subject-1", "audience-1"));
    }
}
