package com.korur.oba.connect.oidc.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;

// 2. Third-party
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.oidc.constant.OidcConstant;
import com.korur.oba.connect.oidc.dto.response.OidcDiscoveryResponse;
import com.korur.oba.connect.oidc.exception.ExceptionDomainOidcError;
import com.korur.oba.connect.oidc.helper.OidcSigningKeyProvider;
import com.korur.oba.connect.oidc.service.OidcAssertionService;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.infrastructure.InternalServerErrorException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.constant.SecurityConstant;

import static com.korur.oba.connect.oidc.constant.service.OidcAssertionServiceLogConstant.*;

/**
 * Signs assertions with the key of {@link OidcSigningKeyProvider} and serves the issuer documents.
 *
 * An assertion carries iss, sub, aud, iat, nbf, exp and a random jti, and its header names the
 * kid, so a provider picks the right key from the key set during a rotation. The lifetime is
 * {@link OidcConstant#ASSERTION_LIFETIME} from the injected clock: one token exchange needs no
 * more, and a leaked assertion expires before it is of use.
 */
@Service
@RequiredArgsConstructor
public class OidcAssertionServiceImpl implements OidcAssertionService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(OidcAssertionServiceImpl.class);

    private final OidcSigningKeyProvider signingKeyProvider;
    private final Clock clock;
    private final LoggingService loggingService;

    @Override
    public boolean isConfigured() {
        return signingKeyProvider.isAvailable();
    }

    @Override
    public String issue(String subject, String audience) {
        ensureConfigured();
        RSAKey signingKey = signingKeyProvider.getSigningKey();
        Instant now = clock.instant();
        Date issuedAt = Date.from(now);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(signingKeyProvider.getIssuerUrl())
                .subject(subject)
                .audience(audience)
                .issueTime(issuedAt)
                .notBeforeTime(issuedAt)
                .expirationTime(Date.from(now.plus(OidcConstant.ASSERTION_LIFETIME)))
                .jwtID(UUID.randomUUID().toString())
                .build();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT)
                .keyID(signingKey.getKeyID())
                .build();
        SignedJWT jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(new RSASSASigner(signingKey));
        } catch (JOSEException exception) {
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE, ASSERTION_SIGNING_FAILED, exception,
                    signingKey.getKeyID());
            throw new InternalServerErrorException(ExceptionDomainOidcError.INT_OIDC_SIGNING_FAILED);
        }
        return jwt.serialize();
    }

    @Override
    public OidcDiscoveryResponse getDiscovery() {
        ensureConfigured();
        String issuerUrl = signingKeyProvider.getIssuerUrl();
        return new OidcDiscoveryResponse(
                issuerUrl,
                issuerUrl + SecurityConstant.OIDC_JWKS_PATH,
                OidcConstant.RESPONSE_TYPES_SUPPORTED,
                OidcConstant.SUBJECT_TYPES_SUPPORTED,
                OidcConstant.SIGNING_ALGORITHMS_SUPPORTED);
    }

    @Override
    public Map<String, Object> getJwks() {
        ensureConfigured();
        return signingKeyProvider.getPublicKeySet().toPublicJWKSet().toJSONObject();
    }

    private void ensureConfigured() {
        if (!signingKeyProvider.isAvailable()) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, ISSUER_NOT_CONFIGURED);
            throw new NotFoundException(ExceptionDomainOidcError.NOT_OIDC_ISSUER_NOT_CONFIGURED);
        }
    }
}
