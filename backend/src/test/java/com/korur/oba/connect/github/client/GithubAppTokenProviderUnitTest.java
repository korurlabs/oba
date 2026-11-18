package com.korur.oba.connect.github.client;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// 2. Third-party
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

// 3. Java SDK
import java.io.IOException;
import java.security.KeyPair;
import java.text.ParseException;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

// 4. Internal
import com.korur.oba.connect.github.exception.ExceptionDomainGithubError;
import com.korur.oba.connect.github.mock_data.GithubMocks;
import com.korur.oba.connect.github.properties.GithubProperties;
import com.korur.oba.dojo.exception.internal.infrastructure.BadGatewayException;
import com.korur.oba.dojo.exception.internal.infrastructure.InternalServerErrorException;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class GithubAppTokenProviderUnitTest {

    private static final String TOKEN_URL = "https://api.github.com/app/installations/777/access_tokens";
    private static final String SECRET_TOKEN = "ghs_secretInstallationToken";

    @Mock
    private LoggingService loggingService;

    private KeyPair keyPair;
    private GithubMocks.MutableClock clock;
    private MockRestServiceServer server;
    private GithubProperties properties;
    private GithubAppTokenProvider provider;

    @BeforeEach
    void setUp() {
        keyPair = GithubMocks.generateKeyPair();
        clock = GithubMocks.clock();
        properties = GithubMocks.properties(GithubMocks.privateKeyConfig(keyPair), UUID.randomUUID());
        RestClient.Builder builder = GithubMocks.restClientBuilder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new GithubAppTokenProvider(builder.build(), properties, clock, loggingService);
    }

    private static String tokenBody(String token, String expiresAt) {
        return "{\"token\":\"" + token + "\",\"expires_at\":\"" + expiresAt + "\"}";
    }

    private void expectTokenRequest(String token, String expiresAt) {
        server.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(tokenBody(token, expiresAt), MediaType.APPLICATION_JSON));
    }

    @Nested
    class InstallationToken {

        @Test
        void validConfig_requestsTokenWithSignedAppJwtAndNarrowPermissions() throws Exception {
            // Arrange
            AtomicReference<String> authorization = new AtomicReference<>();
            server.expect(requestTo(TOKEN_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                    .andExpect(content().json("{\"permissions\":{\"pull_requests\":\"read\",\"metadata\":\"read\"}}"))
                    .andExpect(request -> authorization.set(request.getHeaders().getFirst("Authorization")))
                    .andRespond(withSuccess(tokenBody(SECRET_TOKEN, "2026-10-28T11:00:00Z"),
                            MediaType.APPLICATION_JSON));

            // Act
            String token = provider.installationToken();

            // Assert
            assertThat(token).isEqualTo(SECRET_TOKEN);
            assertThat(authorization.get()).startsWith("Bearer ");
            SignedJWT jwt = SignedJWT.parse(authorization.get().substring("Bearer ".length()));
            assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
            assertThat(jwt.verify(new RSASSAVerifier(GithubMocks.publicKey(keyPair)))).isTrue();
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            assertThat(claims.getIssuer()).isEqualTo(GithubMocks.APP_ID);
            assertThat(claims.getIssueTime().toInstant()).isEqualTo(GithubMocks.NOW.minusSeconds(60));
            assertThat(claims.getExpirationTime().toInstant()).isEqualTo(GithubMocks.NOW.plus(Duration.ofMinutes(9)));
            server.verify();
        }

        @Test
        void rawPemKey_isAccepted() {
            // Arrange
            properties.setPrivateKey(GithubMocks.pkcs8Pem(keyPair));
            expectTokenRequest(SECRET_TOKEN, "2026-10-28T11:00:00Z");

            // Act
            String token = provider.installationToken();

            // Assert
            assertThat(token).isEqualTo(SECRET_TOKEN);
        }

        @Test
        void tokenStillFresh_isServedFromCacheWithoutSecondRequest() {
            // Arrange
            expectTokenRequest(SECRET_TOKEN, "2026-10-28T11:00:00Z");
            String first = provider.installationToken();
            clock.advance(Duration.ofMinutes(54));

            // Act
            String second = provider.installationToken();

            // Assert
            assertThat(second).isEqualTo(first);
            server.verify();
        }

        @Test
        void tokenWithinFiveMinutesOfExpiry_isFetchedAgain() {
            // Arrange
            expectTokenRequest("first", "2026-10-28T11:00:00Z");
            expectTokenRequest("second", "2026-10-28T12:00:00Z");
            provider.installationToken();
            clock.advance(Duration.ofMinutes(55));

            // Act
            String token = provider.installationToken();

            // Assert
            assertThat(token).isEqualTo("second");
            server.verify();
        }

        @Test
        void notConfigured_throwsNotConfiguredWithoutRequest() {
            // Arrange
            properties.setPrivateKey(" ");

            // Act
            InternalServerErrorException exception =
                    assertThrows(InternalServerErrorException.class, () -> provider.installationToken());

            // Assert
            assertThat(exception.getErrorCode())
                    .isEqualTo(ExceptionDomainGithubError.INT_GITHUB_NOT_CONFIGURED.getCode());
            server.verify();
        }
    }

    @Nested
    class Invalidate {

        @Test
        void rejectedTokenIsCurrent_dropsItSoNextCallFetches() {
            // Arrange
            expectTokenRequest("first", "2026-10-28T11:00:00Z");
            expectTokenRequest("second", "2026-10-28T11:00:00Z");
            String first = provider.installationToken();

            // Act
            provider.invalidate(first);
            String next = provider.installationToken();

            // Assert
            assertThat(next).isEqualTo("second");
            server.verify();
        }

        @Test
        void rejectedTokenAlreadyReplaced_keepsCurrentToken() {
            // Arrange
            expectTokenRequest("first", "2026-10-28T11:00:00Z");
            provider.installationToken();

            // Act
            provider.invalidate("some-older-token");
            String next = provider.installationToken();

            // Assert
            assertThat(next).isEqualTo("first");
            server.verify();
        }
    }

    @Nested
    class Failures {

        @Test
        void gitHubAnswers401_throwsCredentialsRefused() {
            // Arrange
            server.expect(requestTo(TOKEN_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

            // Act
            BadGatewayException exception =
                    assertThrows(BadGatewayException.class, () -> provider.installationToken());

            // Assert
            assertThat(exception.getErrorCode())
                    .isEqualTo(ExceptionDomainGithubError.UPS_GITHUB_CREDENTIALS_REFUSED.getCode());
        }

        @Test
        void gitHubAnswers404_throwsCredentialsRefused() {
            // Arrange
            server.expect(requestTo(TOKEN_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

            // Act
            BadGatewayException exception =
                    assertThrows(BadGatewayException.class, () -> provider.installationToken());

            // Assert
            assertThat(exception.getErrorCode())
                    .isEqualTo(ExceptionDomainGithubError.UPS_GITHUB_CREDENTIALS_REFUSED.getCode());
        }

        @Test
        void gitHubAnswers500_throwsUnreachable() {
            // Arrange
            server.expect(requestTo(TOKEN_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

            // Act
            BadGatewayException exception =
                    assertThrows(BadGatewayException.class, () -> provider.installationToken());

            // Assert
            assertThat(exception.getErrorCode())
                    .isEqualTo(ExceptionDomainGithubError.UPS_GITHUB_UNREACHABLE.getCode());
        }

        @Test
        void connectionFails_throwsUnreachable() {
            // Arrange
            server.expect(requestTo(TOKEN_URL)).andRespond(request -> {
                throw new IOException("connection reset");
            });

            // Act
            BadGatewayException exception =
                    assertThrows(BadGatewayException.class, () -> provider.installationToken());

            // Assert
            assertThat(exception.getErrorCode())
                    .isEqualTo(ExceptionDomainGithubError.UPS_GITHUB_UNREACHABLE.getCode());
        }

        @Test
        void responseWithoutToken_throwsUnreachable() {
            // Arrange
            server.expect(requestTo(TOKEN_URL))
                    .andRespond(withSuccess("{\"expires_at\":\"2026-10-28T11:00:00Z\"}", MediaType.APPLICATION_JSON));

            // Act
            BadGatewayException exception =
                    assertThrows(BadGatewayException.class, () -> provider.installationToken());

            // Assert
            assertThat(exception.getErrorCode())
                    .isEqualTo(ExceptionDomainGithubError.UPS_GITHUB_UNREACHABLE.getCode());
        }

        @Test
        void privateKeyNotBase64_throwsKeyUnreadableWithoutRequest() {
            // Arrange
            properties.setPrivateKey("%%% not a key %%%");

            // Act
            InternalServerErrorException exception =
                    assertThrows(InternalServerErrorException.class, () -> provider.installationToken());

            // Assert
            assertThat(exception.getErrorCode())
                    .isEqualTo(ExceptionDomainGithubError.INT_GITHUB_PRIVATE_KEY_UNREADABLE.getCode());
            server.verify();
        }

        @Test
        void privateKeyIsPkcs1_throwsKeyUnreadable() {
            // Arrange
            String pkcs1 = "-----BEGIN RSA PRIVATE KEY-----\nMIIB\n-----END RSA PRIVATE KEY-----\n";
            properties.setPrivateKey(Base64.getEncoder().encodeToString(pkcs1.getBytes()));

            // Act
            InternalServerErrorException exception =
                    assertThrows(InternalServerErrorException.class, () -> provider.installationToken());

            // Assert
            assertThat(exception.getErrorCode())
                    .isEqualTo(ExceptionDomainGithubError.INT_GITHUB_PRIVATE_KEY_UNREADABLE.getCode());
        }

        @Test
        void privateKeyIsCorruptPkcs8_throwsKeyUnreadable() {
            // Arrange
            String pem = "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----\n";
            properties.setPrivateKey(pem);

            // Act
            InternalServerErrorException exception =
                    assertThrows(InternalServerErrorException.class, () -> provider.installationToken());

            // Assert
            assertThat(exception.getErrorCode())
                    .isEqualTo(ExceptionDomainGithubError.INT_GITHUB_PRIVATE_KEY_UNREADABLE.getCode());
        }
    }

    @Nested
    class Secrecy {

        @Test
        void tokenAndKey_neverReachTheLog() throws ParseException {
            // Arrange
            expectTokenRequest(SECRET_TOKEN, "2026-10-28T11:00:00Z");

            // Act
            provider.installationToken();
            provider.invalidate(SECRET_TOKEN);

            // Assert
            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(loggingService, atLeastOnce())
                    .info(any(), any(), any(String.class), captor.capture());
            assertThat(captor.getAllValues().toString()).doesNotContain(SECRET_TOKEN)
                    .doesNotContain(properties.getPrivateKey());
            verify(loggingService, never()).error(any(), any(), any(String.class), any(Object[].class));
        }
    }
}
