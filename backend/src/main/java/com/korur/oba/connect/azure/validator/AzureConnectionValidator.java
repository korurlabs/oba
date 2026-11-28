package com.korur.oba.connect.azure.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.azure.constant.AzureConnectionConstant;
import com.korur.oba.connect.azure.constant.AzureConnectionExceptionLogConstant;
import com.korur.oba.connect.azure.entity.AzureConnection;
import com.korur.oba.connect.azure.enums.AzureConnectionStatus;
import com.korur.oba.connect.azure.exception.ExceptionDomainAzureError;
import com.korur.oba.connect.azure.repository.AzureConnectionRepository;
import com.korur.oba.connect.oidc.service.OidcAssertionService;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Validator for the rules of an organization's Azure connections that a request annotation cannot
 * express: an issuer to sign with, the connection's organization, one name and one identity per
 * organization, a client id before a check, and a connected state before a connection is used.
 *
 * Each refusal logs a warn line and throws, so services call these methods first and never inline
 * a check.
 */
@Component
@RequiredArgsConstructor
public class AzureConnectionValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AzureConnectionValidator.class);

    private final AzureConnectionRepository connectionRepository;
    private final OidcAssertionService oidcAssertionService;
    private final LoggingService loggingService;

    /**
     * Ensures the deployment has an issuer that can sign, because without one Entra ID can never
     * accept a connection's assertion.
     *
     * @param organizationId organization of the request, for the log line
     * @throws NotFoundException NOT_AZURE_CONNECTIONS_UNAVAILABLE when no issuer is configured
     */
    public void ensureIssuerConfigured(UUID organizationId) {
        if (!oidcAssertionService.isConfigured()) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    AzureConnectionExceptionLogConstant.CONNECTIONS_UNAVAILABLE, organizationId);
            throw new NotFoundException(ExceptionDomainAzureError.NOT_AZURE_CONNECTIONS_UNAVAILABLE);
        }
    }

    /**
     * Ensures the organization has the connection and returns it. A connection of another
     * organization is a missing one, so its existence does not leak.
     *
     * @param organizationId organization of the request
     * @param connectionId   ID of the connection
     * @return the connection
     * @throws NotFoundException NOT_AZURE_CONNECTION_FOUND if the organization has no such connection
     */
    public AzureConnection ensureConnectionExists(UUID organizationId, UUID connectionId) {
        return connectionRepository.findByIdAndOrganizationId(connectionId, organizationId)
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                            AzureConnectionExceptionLogConstant.CONNECTION_NOT_FOUND, connectionId, organizationId);
                    return new NotFoundException(ExceptionDomainAzureError.NOT_AZURE_CONNECTION_FOUND,
                            Map.of(AzureConnectionConstant.FIELD_CONNECTION_ID, connectionId));
                });
    }

    /**
     * Ensures no connection of the organization has the name yet. The unique constraint on
     * (organization_id, name) backs this check against a concurrent write.
     *
     * @param organizationId organization of the request
     * @param name           name of the new connection
     * @throws ConflictException STA_AZURE_CONNECTION_NAME_DUPLICATE if the name is taken
     */
    public void ensureNameIsUnique(UUID organizationId, String name) {
        if (connectionRepository.existsByOrganizationIdAndName(organizationId, name)) {
            throw nameTaken(organizationId, name);
        }
    }

    /**
     * Ensures no other connection of the organization has the name, for an edit.
     *
     * @param organizationId organization of the request
     * @param name           new name of the connection
     * @param connectionId   ID of the connection being edited, which may keep its name
     * @throws ConflictException STA_AZURE_CONNECTION_NAME_DUPLICATE if another connection has the name
     */
    public void ensureNameIsUnique(UUID organizationId, String name, UUID connectionId) {
        if (connectionRepository.existsByOrganizationIdAndNameAndIdNot(organizationId, name, connectionId)) {
            throw nameTaken(organizationId, name);
        }
    }

    /**
     * Ensures the organization does not connect the identity yet. Passes when the client id is
     * null, because connections without one name no identity and never collide, as in the unique
     * constraint.
     *
     * @param organizationId organization of the request
     * @param tenantId       tenant id of the new connection
     * @param clientId       client id of the new connection, may be null
     * @throws ConflictException STA_AZURE_CONNECTION_IDENTITY_DUPLICATE if a connection names the identity
     */
    public void ensureIdentityIsNew(UUID organizationId, UUID tenantId, UUID clientId) {
        if (clientId == null) {
            return;
        }
        if (connectionRepository.existsByOrganizationIdAndTenantIdAndClientId(organizationId, tenantId, clientId)) {
            throw identityTaken(organizationId, tenantId, clientId);
        }
    }

    /**
     * Ensures no other connection of the organization connects the identity, for an edit. Passes
     * when the client id is null.
     *
     * @param organizationId organization of the request
     * @param tenantId       new tenant id of the connection
     * @param clientId       new client id of the connection, may be null
     * @param connectionId   ID of the connection being edited, which may keep its identity
     * @throws ConflictException STA_AZURE_CONNECTION_IDENTITY_DUPLICATE if another connection names the identity
     */
    public void ensureIdentityIsNew(UUID organizationId, UUID tenantId, UUID clientId, UUID connectionId) {
        if (clientId == null) {
            return;
        }
        if (connectionRepository.existsByOrganizationIdAndTenantIdAndClientIdAndIdNot(organizationId, tenantId,
                clientId, connectionId)) {
            throw identityTaken(organizationId, tenantId, clientId);
        }
    }

    /**
     * Ensures the connection names its identity's client id, without which no token can be
     * requested.
     *
     * @param connection the connection to check
     * @throws ValidationException VAL_AZURE_CONNECTION_CLIENT_ID_MISSING if no client id is set
     */
    public void ensureClientIdSet(AzureConnection connection) {
        if (connection.getClientId() == null) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    AzureConnectionExceptionLogConstant.CONNECTION_CLIENT_ID_MISSING, connection.getId());
            throw new ValidationException(ExceptionDomainAzureError.VAL_AZURE_CONNECTION_CLIENT_ID_MISSING,
                    Map.of(AzureConnectionConstant.FIELD_CONNECTION_ID, connection.getId()));
        }
    }

    /**
     * Ensures the last check of the connection succeeded, so it is only used while Azure accepts it.
     *
     * @param connection the connection to check
     * @throws ConflictException STA_AZURE_CONNECTION_NOT_CONNECTED if the status is not
     *                           {@link AzureConnectionStatus#CONNECTED}
     */
    public void ensureConnected(AzureConnection connection) {
        if (connection.getStatus() != AzureConnectionStatus.CONNECTED) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    AzureConnectionExceptionLogConstant.CONNECTION_NOT_CONNECTED, connection.getId(),
                    connection.getStatus());
            throw new ConflictException(ExceptionDomainAzureError.STA_AZURE_CONNECTION_NOT_CONNECTED,
                    Map.of(AzureConnectionConstant.FIELD_CONNECTION_ID, connection.getId(),
                            AzureConnectionConstant.FIELD_STATUS, connection.getStatus()));
        }
    }

    /**
     * Logs a taken name and builds the conflict the client shows on the name field.
     */
    private ConflictException nameTaken(UUID organizationId, String name) {
        loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                AzureConnectionExceptionLogConstant.CONNECTION_NAME_DUPLICATE, organizationId, name);
        return new ConflictException(ExceptionDomainAzureError.STA_AZURE_CONNECTION_NAME_DUPLICATE,
                Map.of(AzureConnectionConstant.FIELD_NAME, name));
    }

    /**
     * Logs an identity the organization already connects and builds the conflict the client shows
     * on the client id field.
     */
    private ConflictException identityTaken(UUID organizationId, UUID tenantId, UUID clientId) {
        loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                AzureConnectionExceptionLogConstant.CONNECTION_IDENTITY_DUPLICATE, organizationId, tenantId, clientId);
        return new ConflictException(ExceptionDomainAzureError.STA_AZURE_CONNECTION_IDENTITY_DUPLICATE,
                Map.of(AzureConnectionConstant.FIELD_TENANT_ID, tenantId, AzureConnectionConstant.FIELD_CLIENT_ID,
                        clientId));
    }
}
