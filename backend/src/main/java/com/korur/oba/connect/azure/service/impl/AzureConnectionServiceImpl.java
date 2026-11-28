package com.korur.oba.connect.azure.service.impl;

// 1. Framework-level
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import static com.korur.oba.connect.azure.constant.service.AzureConnectionServiceLogConstant.*;

import com.korur.oba.connect.azure.client.AzureConnectionTokenProvider;
import com.korur.oba.connect.azure.constant.AzureConnectionConstant;
import com.korur.oba.connect.azure.constant.AzureConnectionExceptionLogConstant;
import com.korur.oba.connect.azure.dto.AzureSubscriptionPayload;
import com.korur.oba.connect.azure.dto.request.AzureConnectionRequest;
import com.korur.oba.connect.azure.dto.response.AzureConnectionResponse;
import com.korur.oba.connect.azure.entity.AzureConnection;
import com.korur.oba.connect.azure.entity.AzureConnectionSubscription;
import com.korur.oba.connect.azure.enums.AzureConnectionStatus;
import com.korur.oba.connect.azure.exception.ExceptionDomainAzureError;
import com.korur.oba.connect.azure.mapper.AzureConnectionMapper;
import com.korur.oba.connect.azure.repository.AzureConnectionRepository;
import com.korur.oba.connect.azure.repository.AzureConnectionSubscriptionRepository;
import com.korur.oba.connect.azure.service.AzureConnectionQueryService;
import com.korur.oba.connect.azure.service.AzureConnectionService;
import com.korur.oba.connect.azure.validator.AzureConnectionValidator;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Implements the writes to an organization's Azure connections.
 *
 * The admin's writes run behind the ADMIN role the access interceptor checks, and load the
 * connection by id and organization, so another organization's connection reads as missing. The
 * two check results lock the row with {@link AzureConnectionRepository#findByIdForUpdate}, so a
 * result is written against the identity the row holds at that moment and two checks of one
 * connection never interleave their subscription lists.
 */
@Service
@RequiredArgsConstructor
public class AzureConnectionServiceImpl implements AzureConnectionService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AzureConnectionServiceImpl.class);

    private final AzureConnectionRepository connectionRepository;
    private final AzureConnectionSubscriptionRepository subscriptionRepository;
    private final AzureConnectionMapper connectionMapper;
    private final AzureConnectionValidator connectionValidator;
    private final AzureConnectionQueryService connectionQueryService;
    private final AzureConnectionTokenProvider tokenProvider;
    private final Clock clock;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public AzureConnectionResponse createConnection(User user, UUID organizationId, AzureConnectionRequest request) {
        connectionValidator.ensureIssuerConfigured(organizationId);
        connectionValidator.ensureNameIsUnique(organizationId, request.name());
        connectionValidator.ensureIdentityIsNew(organizationId, request.tenantId(), request.clientId());

        AzureConnection connection = connectionMapper.toEntity(request);
        connection.setOrganizationId(organizationId);
        connection.setCreatedById(user.getId());
        connection = saveConnection(connection);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, CONNECTION_CREATED, connection.getId(), organizationId,
                connection.getClientId() != null);

        return connectionQueryService.getConnection(organizationId, connection.getId());
    }

    @Override
    @Transactional
    public AzureConnectionResponse updateConnection(UUID organizationId, UUID connectionId,
                                                    AzureConnectionRequest request) {
        AzureConnection connection = connectionValidator.ensureConnectionExists(organizationId, connectionId);
        connectionValidator.ensureNameIsUnique(organizationId, request.name(), connectionId);
        connectionValidator.ensureIdentityIsNew(organizationId, request.tenantId(), request.clientId(), connectionId);

        boolean identityChanged = !Objects.equals(connection.getTenantId(), request.tenantId())
                || !Objects.equals(connection.getClientId(), request.clientId());

        connectionMapper.updateEntity(connection, request);
        if (identityChanged) {
            resetCheckResults(connection);
        }
        saveConnection(connection);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, CONNECTION_UPDATED, connectionId, organizationId,
                identityChanged);

        return connectionQueryService.getConnection(organizationId, connectionId);
    }

    @Override
    @Transactional
    public void deleteConnection(UUID organizationId, UUID connectionId) {
        AzureConnection connection = connectionValidator.ensureConnectionExists(organizationId, connectionId);

        deleteOrRefuse(connection);
        // Dropping a cache entry is safe before the commit: should the commit fail, the next use
        // of the connection builds its credential again.
        tokenProvider.evict(connectionId);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, CONNECTION_DELETED, connectionId, organizationId);
    }

    @Override
    @Transactional
    public void recordCheckSucceeded(UUID connectionId, List<AzureSubscriptionPayload> subscriptions) {
        Optional<AzureConnection> locked = connectionRepository.findByIdForUpdate(connectionId);
        if (locked.isEmpty()) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, CHECK_RESULT_CONNECTION_GONE, connectionId);
            return;
        }
        AzureConnection connection = locked.get();

        subscriptionRepository.deleteByConnectionId(connectionId);
        List<AzureConnectionSubscription> rows = toSubscriptionRows(connection, subscriptions);
        subscriptionRepository.saveAll(rows);

        connection.setStatus(AzureConnectionStatus.CONNECTED);
        connection.setLastVerifiedAt(Instant.now(clock));
        connection.setLastError(null);
        connectionRepository.save(connection);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, CHECK_SUCCEEDED_RECORDED, connectionId, rows.size());
    }

    @Override
    @Transactional
    public void recordCheckFailed(UUID connectionId, String errorCode) {
        Optional<AzureConnection> locked = connectionRepository.findByIdForUpdate(connectionId);
        if (locked.isEmpty()) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, CHECK_RESULT_CONNECTION_GONE, connectionId);
            return;
        }
        AzureConnection connection = locked.get();

        // A connection that never succeeded stays PENDING: FAILING says that a trust which worked
        // has stopped working.
        if (connection.getStatus() != AzureConnectionStatus.PENDING) {
            connection.setStatus(AzureConnectionStatus.FAILING);
        }
        connection.setLastError(truncate(errorCode, AzureConnectionConstant.LAST_ERROR_MAX_LENGTH));
        connectionRepository.save(connection);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, CHECK_FAILED_RECORDED, connectionId, errorCode,
                connection.getStatus());
    }

    /**
     * Clears what the checks found for the old identity: the status, the verification time, the
     * error and the subscriptions.
     */
    private void resetCheckResults(AzureConnection connection) {
        connection.setStatus(AzureConnectionStatus.PENDING);
        connection.setLastVerifiedAt(null);
        connection.setLastError(null);
        subscriptionRepository.deleteByConnectionId(connection.getId());
    }

    /**
     * Builds the subscription rows of a successful check. Azure's text is cut to the column
     * lengths, and a subscription listed twice is kept once, because the list is unique per
     * connection.
     */
    private List<AzureConnectionSubscription> toSubscriptionRows(AzureConnection connection,
                                                                 List<AzureSubscriptionPayload> subscriptions) {
        List<AzureConnectionSubscription> rows = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (AzureSubscriptionPayload subscription : subscriptions) {
            if (!seen.add(subscription.subscriptionId())) {
                loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, SUBSCRIPTION_DUPLICATE_SKIPPED,
                        connection.getId(), subscription.subscriptionId());
                continue;
            }
            rows.add(AzureConnectionSubscription.builder()
                    .connection(connection)
                    .subscriptionId(subscription.subscriptionId())
                    .displayName(truncate(subscription.displayName(),
                            AzureConnectionConstant.SUBSCRIPTION_DISPLAY_NAME_MAX_LENGTH))
                    .state(truncate(subscription.state(), AzureConnectionConstant.SUBSCRIPTION_STATE_MAX_LENGTH))
                    .build());
        }
        return rows;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * Writes a connection at once, so a concurrent write that took the same name or identity after
     * the validator's check is caught here and answered with the conflict that check gives. Every
     * other integrity failure goes up as it is.
     *
     * @throws ConflictException STA_AZURE_CONNECTION_NAME_DUPLICATE or STA_AZURE_CONNECTION_IDENTITY_DUPLICATE
     */
    private AzureConnection saveConnection(AzureConnection connection) {
        try {
            return connectionRepository.saveAndFlush(connection);
        } catch (DataIntegrityViolationException exception) {
            String constraint = violatedConstraint(exception);
            if (AzureConnectionConstant.NAME_UNIQUE_CONSTRAINT.equalsIgnoreCase(constraint)) {
                loggingService.warn(LOG_PREFIX, LogLayer.SERVICE,
                        AzureConnectionExceptionLogConstant.CONNECTION_NAME_RACE_LOST,
                        connection.getOrganizationId(), connection.getName());
                throw new ConflictException(ExceptionDomainAzureError.STA_AZURE_CONNECTION_NAME_DUPLICATE,
                        Map.of(AzureConnectionConstant.FIELD_NAME, connection.getName()));
            }
            if (AzureConnectionConstant.IDENTITY_UNIQUE_CONSTRAINT.equalsIgnoreCase(constraint)) {
                loggingService.warn(LOG_PREFIX, LogLayer.SERVICE,
                        AzureConnectionExceptionLogConstant.CONNECTION_IDENTITY_RACE_LOST,
                        connection.getOrganizationId(), connection.getTenantId(), connection.getClientId());
                throw new ConflictException(ExceptionDomainAzureError.STA_AZURE_CONNECTION_IDENTITY_DUPLICATE,
                        Map.of(AzureConnectionConstant.FIELD_TENANT_ID, connection.getTenantId(),
                                AzureConnectionConstant.FIELD_CLIENT_ID, connection.getClientId()));
            }
            throw exception;
        }
    }

    /**
     * Deletes a connection and flushes at once, so a row of another domain that still references
     * it (a backup source) refuses the delete here. Only such a row can reference a connection, so
     * every integrity failure of the delete means the connection is in use.
     *
     * @throws ConflictException STA_AZURE_CONNECTION_IN_USE when a reference refuses the delete
     */
    private void deleteOrRefuse(AzureConnection connection) {
        try {
            connectionRepository.delete(connection);
            connectionRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, AzureConnectionExceptionLogConstant.CONNECTION_IN_USE,
                    connection.getId());
            throw new ConflictException(ExceptionDomainAzureError.STA_AZURE_CONNECTION_IN_USE,
                    Map.of(AzureConnectionConstant.FIELD_CONNECTION_ID, connection.getId()));
        }
    }

    /**
     * The name of the constraint an integrity failure broke, or null when none is named.
     */
    private static String violatedConstraint(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
            cause = cause.getCause();
        }
        return null;
    }
}
