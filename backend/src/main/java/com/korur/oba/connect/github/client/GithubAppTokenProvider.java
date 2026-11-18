package com.korur.oba.connect.github.client;

// 1. Framework-level
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

// 2. Third-party
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

// 4. Internal
import com.korur.oba.connect.github.constant.GithubConstant;
import com.korur.oba.connect.github.dto.api.GithubApiAccessToken;
import com.korur.oba.connect.github.exception.ExceptionDomainGithubError;
import com.korur.oba.connect.github.helper.GithubErrorTranslator;
import com.korur.oba.connect.github.properties.GithubProperties;
import com.korur.oba.dojo.exception.internal.infrastructure.InternalServerErrorException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

import static com.korur.oba.connect.github.constant.client.GithubAppTokenProviderLogConstant.*;

/**
 * Authenticates the application as the GitHub App and hands out installation tokens.
 *
 * The App signs a short-lived JWT with its private key and trades it for an installation token
 * limited to reading pull requests and metadata. The token is held in memory only, until five
 * minutes before GitHub's expiry, and is shared by every request of this process; a token GitHub
 * rejects early is dropped through {@link #invalidate} and fetched once more by the caller. The
 * private key, the JWT and the token are never persisted, logged or returned to a client.
 */
public class GithubAppTokenProvider {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(GithubAppTokenProvider.class);

    // Narrows the token below the App's own permissions, so a later widening of the App does
    // not widen what the application can do.
    private static final Map<String, Object> TOKEN_REQUEST_BODY = Map.of(
            GithubConstant.BODY_PERMISSIONS, Map.of(
                    GithubConstant.PERMISSION_PULL_REQUESTS, GithubConstant.PERMISSION_READ,
                    GithubConstant.PERMISSION_METADATA, GithubConstant.PERMISSION_READ));

    private final RestClient restClient;
    private final GithubProperties properties;
    private final Clock clock;
    private final LoggingService loggingService;

    private final Object tokenLock = new Object();
    private volatile CachedToken cachedToken;
    private volatile PrivateKey privateKey;

    /**
     * Creates the provider.
     *
     * @param restClient     a client with the GitHub API base URL, its headers and timeouts
     * @param properties     the App's configuration
     * @param clock          the clock of token lifetimes
     * @param loggingService the logging service
     */
    public GithubAppTokenProvider(RestClient restClient, GithubProperties properties, Clock clock,
                                  LoggingService loggingService) {
        this.restClient = restClient;
        this.properties = properties;
        this.clock = clock;
        this.loggingService = loggingService;
    }

    /**
     * Returns an installation token that stays valid for at least five more minutes, fetching a
     * new one when the cached one is absent or close to its expiry.
     *
     * @return the installation token, for the Authorization header only
     */
    public String installationToken() {
        if (!properties.isConfigured()) {
            loggingService.error(LOG_PREFIX, LogLayer.CLIENT, TOKEN_REFUSED_NOT_CONFIGURED);
            throw new InternalServerErrorException(ExceptionDomainGithubError.INT_GITHUB_NOT_CONFIGURED);
        }
        CachedToken current = cachedToken;
        if (current != null && current.isFreshAt(clock.instant())) {
            return current.token();
        }
        synchronized (tokenLock) {
            current = cachedToken;
            if (current != null && current.isFreshAt(clock.instant())) {
                return current.token();
            }
            CachedToken fetched = fetchInstallationToken();
            cachedToken = fetched;
            return fetched.token();
        }
    }

    /**
     * Drops the cached token after GitHub rejected it, unless another request already replaced it.
     *
     * @param rejectedToken the token GitHub answered 401 to
     */
    public void invalidate(String rejectedToken) {
        synchronized (tokenLock) {
            CachedToken current = cachedToken;
            if (current != null && current.token().equals(rejectedToken)) {
                cachedToken = null;
                loggingService.warn(LOG_PREFIX, LogLayer.CLIENT, TOKEN_DROPPED);
            }
        }
    }

    private CachedToken fetchInstallationToken() {
        String appJwt = createAppJwt(clock.instant());
        GithubApiAccessToken response;
        try {
            response = restClient.post()
                    .uri(GithubConstant.PATH_ACCESS_TOKENS, properties.getInstallationId())
                    .headers(headers -> headers.setBearerAuth(appJwt))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(TOKEN_REQUEST_BODY)
                    .retrieve()
                    .body(GithubApiAccessToken.class);
        } catch (RestClientResponseException exception) {
            loggingService.error(LOG_PREFIX, LogLayer.CLIENT, TOKEN_FETCH_FAILED_STATUS, exception,
                    exception.getStatusCode().value());
            throw GithubErrorTranslator.fromTokenResponse(exception);
        } catch (RestClientException exception) {
            loggingService.error(LOG_PREFIX, LogLayer.CLIENT, TOKEN_FETCH_FAILED_IO, exception);
            throw GithubErrorTranslator.unreachable();
        }
        if (response == null || response.token() == null || response.token().isBlank()
                || response.expiresAt() == null) {
            loggingService.error(LOG_PREFIX, LogLayer.CLIENT, TOKEN_RESPONSE_INCOMPLETE);
            throw GithubErrorTranslator.unreachable();
        }
        loggingService.info(LOG_PREFIX, LogLayer.CLIENT, TOKEN_FETCHED, response.expiresAt());
        return new CachedToken(response.token(), response.expiresAt());
    }

    /**
     * Signs the App JWT: RS256, issued by the App ID, backdated a minute against clock skew and
     * valid for nine minutes, below GitHub's ten-minute ceiling.
     */
    private String createAppJwt(Instant now) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(properties.getAppId().trim())
                .issueTime(Date.from(now.minus(GithubConstant.APP_JWT_BACKDATE)))
                .expirationTime(Date.from(now.plus(GithubConstant.APP_JWT_LIFETIME)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        try {
            jwt.sign(new RSASSASigner(privateKey()));
        } catch (JOSEException exception) {
            loggingService.error(LOG_PREFIX, LogLayer.CLIENT, APP_JWT_SIGNING_FAILED, exception);
            throw new InternalServerErrorException(ExceptionDomainGithubError.INT_GITHUB_PRIVATE_KEY_UNREADABLE);
        }
        return jwt.serialize();
    }

    /**
     * Reads the private key once and keeps it in memory. The configured value is the base64 of a
     * PKCS#8 PEM, so it fits a single-line secret; a raw PEM is accepted too. A PKCS#1 key, the
     * format GitHub downloads, is refused with a log line that names the conversion.
     */
    private PrivateKey privateKey() {
        PrivateKey current = privateKey;
        if (current != null) {
            return current;
        }
        try {
            String configured = properties.getPrivateKey().trim();
            String pem = configured.startsWith(GithubConstant.PEM_HEADER_MARKER)
                    ? configured
                    : new String(Base64.getMimeDecoder().decode(configured), StandardCharsets.UTF_8);
            if (!pem.contains(GithubConstant.PEM_PKCS8_HEADER)) {
                throw new GeneralSecurityException(PRIVATE_KEY_UNREADABLE);
            }
            String body = pem.replace(GithubConstant.PEM_PKCS8_HEADER, "")
                    .replace(GithubConstant.PEM_PKCS8_FOOTER, "");
            byte[] der = Base64.getMimeDecoder().decode(GithubConstant.WHITESPACE.matcher(body).replaceAll(""));
            PrivateKey parsed = KeyFactory.getInstance(GithubConstant.KEY_ALGORITHM_RSA)
                    .generatePrivate(new PKCS8EncodedKeySpec(der));
            privateKey = parsed;
            return parsed;
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            loggingService.error(LOG_PREFIX, LogLayer.CLIENT, PRIVATE_KEY_UNREADABLE, exception);
            throw new InternalServerErrorException(ExceptionDomainGithubError.INT_GITHUB_PRIVATE_KEY_UNREADABLE);
        }
    }

    /**
     * An installation token and its expiry. toString leaves the token out.
     */
    private record CachedToken(String token, Instant expiresAt) {

        private boolean isFreshAt(Instant now) {
            return now.isBefore(expiresAt.minus(GithubConstant.TOKEN_REFRESH_MARGIN));
        }

        @Override
        public String toString() {
            return CachedToken.class.getSimpleName();
        }
    }
}
