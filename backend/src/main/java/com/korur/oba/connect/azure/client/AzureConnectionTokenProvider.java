package com.korur.oba.connect.azure.client;

// 1. Framework-level

// 2. Third-party
import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.ClientAssertionCredentialBuilder;

// 3. Java SDK
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

// 4. Internal
import com.korur.oba.connect.azure.constant.AzureConnectionConstant;
import com.korur.oba.connect.azure.constant.AzureResourceManagerConstant;
import com.korur.oba.connect.azure.dto.AzureCachedCredential;
import com.korur.oba.connect.azure.entity.AzureConnection;
import com.korur.oba.connect.azure.exception.ExceptionDomainAzureError;
import com.korur.oba.connect.azure.helper.AzureResourceManagerErrorTranslator;
import com.korur.oba.connect.oidc.service.OidcAssertionService;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.exception.internal.infrastructure.ApplicationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

import static com.korur.oba.connect.azure.constant.client.AzureConnectionTokenProviderLogConstant.*;

/**
 * Hands out Azure Resource Manager tokens for one connection at a time, through workload identity
 * federation: no client secret exists anywhere.
 *
 * Each connection gets one ClientAssertionCredential for its tenant and client id. When the
 * credential needs a token it asks for an assertion, which this class signs through
 * {@link OidcAssertionService#issue} for the connection's stored subject and Entra ID's token
 * exchange audience; Entra ID trusts it only when the organization created a federated credential
 * for that subject. Every signed assertion is logged with its organization and connection, so each
 * use of the signing key can be traced, and never the assertion itself.
 *
 * The credential caches the token and renews it before expiry, so a connection makes one exchange
 * per token lifetime and replica. The credential is kept per connection id and rebuilt when the row
 * names another tenant, client or subject; {@link #evict} drops it when the connection is deleted.
 * A refused exchange is translated by {@link AzureResourceManagerErrorTranslator#fromTokenExchange},
 * which keeps only Entra's AADSTS code.
 */
public class AzureConnectionTokenProvider {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AzureConnectionTokenProvider.class);

    private static final TokenRequestContext ARM_TOKEN_REQUEST =
            new TokenRequestContext().addScopes(AzureResourceManagerConstant.TOKEN_SCOPE);

    private final OidcAssertionService oidcAssertionService;
    private final LoggingService loggingService;

    // Keyed by connection id; holds one entry per connection used since start, dropped on delete.
    private final ConcurrentMap<UUID, AzureCachedCredential> credentials = new ConcurrentHashMap<>();

    /**
     * Creates the provider.
     *
     * @param oidcAssertionService the issuer that signs the assertions
     * @param loggingService       the logging service
     */
    public AzureConnectionTokenProvider(OidcAssertionService oidcAssertionService, LoggingService loggingService) {
        this.oidcAssertionService = oidcAssertionService;
        this.loggingService = loggingService;
    }

    /**
     * Returns a Resource Manager token for the connection, exchanging a new assertion when the
     * credential holds no fresh token.
     *
     * @param connection the connection, loaded by the caller; it must have a client id
     * @return the access token, for the Authorization header only
     */
    public String getAccessToken(AzureConnection connection) {
        TokenCredential credential = credentialFor(connection);
        AccessToken token;
        try {
            token = credential.getTokenSync(ARM_TOKEN_REQUEST);
        } catch (RuntimeException exception) {
            ApplicationException translated = AzureResourceManagerErrorTranslator.fromTokenExchange(exception);
            Optional<String> aadstsCode = AzureResourceManagerErrorTranslator.findAadstsCode(exception);
            // The exception is not attached: its message repeats Entra ID's answer.
            loggingService.error(LOG_PREFIX, LogLayer.CLIENT, TOKEN_EXCHANGE_FAILED,
                    connection.getOrganizationId(), connection.getId(), translated.getErrorCode(),
                    aadstsCode.orElse(AADSTS_CODE_ABSENT), exception.getClass().getName());
            throw translated;
        }
        if (token == null || token.getToken() == null || token.getToken().isBlank()) {
            loggingService.error(LOG_PREFIX, LogLayer.CLIENT, TOKEN_EMPTY,
                    connection.getOrganizationId(), connection.getId());
            throw AzureResourceManagerErrorTranslator.unexpectedResponse();
        }
        return token.getToken();
    }

    /**
     * Drops the credential of a deleted connection, so its cached token is not kept.
     *
     * @param connectionId the connection
     */
    public void evict(UUID connectionId) {
        if (credentials.remove(connectionId) != null) {
            loggingService.info(LOG_PREFIX, LogLayer.CLIENT, CREDENTIAL_EVICTED, connectionId);
        }
    }

    /**
     * Returns the credential of the connection, building it when none is cached or the cached one
     * was built for another tenant, client or subject. Two requests may build one at the same
     * time; the later one replaces the earlier, which costs one extra token exchange at most.
     */
    TokenCredential credentialFor(AzureConnection connection) {
        if (connection.getClientId() == null) {
            loggingService.warn(LOG_PREFIX, LogLayer.CLIENT, CLIENT_ID_MISSING,
                    connection.getOrganizationId(), connection.getId());
            throw new ValidationException(ExceptionDomainAzureError.VAL_AZURE_CONNECTION_CLIENT_ID_MISSING);
        }
        AzureCachedCredential cached = credentials.get(connection.getId());
        if (cached != null && isBuiltFor(cached, connection)) {
            return cached.credential();
        }
        AzureCachedCredential built = buildCredential(connection);
        credentials.put(connection.getId(), built);
        loggingService.info(LOG_PREFIX, LogLayer.CLIENT, CREDENTIAL_BUILT,
                connection.getOrganizationId(), connection.getId());
        return built.credential();
    }

    private boolean isBuiltFor(AzureCachedCredential cached, AzureConnection connection) {
        return Objects.equals(cached.tenantId(), connection.getTenantId())
                && Objects.equals(cached.clientId(), connection.getClientId())
                && Objects.equals(cached.subject(), connection.getSubject());
    }

    private AzureCachedCredential buildCredential(AzureConnection connection) {
        UUID organizationId = connection.getOrganizationId();
        UUID connectionId = connection.getId();
        String subject = connection.getSubject();
        TokenCredential credential = new ClientAssertionCredentialBuilder()
                .tenantId(connection.getTenantId().toString())
                .clientId(connection.getClientId().toString())
                .clientAssertion(() -> signAssertion(organizationId, connectionId, subject))
                .build();
        return new AzureCachedCredential(organizationId, connectionId, connection.getTenantId(),
                connection.getClientId(), subject, credential);
    }

    private String signAssertion(UUID organizationId, UUID connectionId, String subject) {
        String assertion = oidcAssertionService.issue(subject, AzureConnectionConstant.TOKEN_EXCHANGE_AUDIENCE);
        loggingService.info(LOG_PREFIX, LogLayer.CLIENT, ASSERTION_SIGNED, organizationId, connectionId);
        return assertion;
    }
}
