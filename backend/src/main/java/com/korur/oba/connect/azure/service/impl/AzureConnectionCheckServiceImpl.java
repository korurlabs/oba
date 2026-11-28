package com.korur.oba.connect.azure.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import static com.korur.oba.connect.azure.constant.service.AzureConnectionCheckServiceLogConstant.*;

import com.korur.oba.connect.azure.constant.AzureConnectionConstant;
import com.korur.oba.connect.azure.constant.AzureResourceManagerConstant;
import com.korur.oba.connect.azure.dto.AzureSubscriptionPayload;
import com.korur.oba.connect.azure.dto.response.AzureConnectionResponse;
import com.korur.oba.connect.azure.entity.AzureConnection;
import com.korur.oba.connect.azure.exception.ExceptionDomainAzureError;
import com.korur.oba.connect.azure.repository.AzureConnectionRepository;
import com.korur.oba.connect.azure.service.AzureConnectionCheckService;
import com.korur.oba.connect.azure.service.AzureConnectionQueryService;
import com.korur.oba.connect.azure.service.AzureConnectionService;
import com.korur.oba.connect.azure.service.AzureResourceManagerQueryService;
import com.korur.oba.connect.azure.validator.AzureConnectionValidator;
import com.korur.oba.dojo.exception.internal.infrastructure.ApplicationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Checks exactly one Azure connection, reading Entra ID and Azure Resource Manager outside any
 * transaction so no row lock is held during a network call, and commits the result in that
 * connection's own transaction ({@link AzureConnectionService#recordCheckSucceeded} or
 * {@link AzureConnectionService#recordCheckFailed}). A failure therefore touches only that
 * connection, which is why the daily check calls it per connection and not once for all.
 *
 * An Azure or Entra refusal is the result of the check, stored as the connection's error code, not
 * an error of the caller. Any other failure, such as a database error, goes up to the caller.
 *
 * The check takes the tenant and client id of the row before it reads Azure and hands them with
 * the result, so a result read with an identity the admin has since replaced is dropped instead of
 * landing on the row of the new identity.
 */
@Service
@RequiredArgsConstructor
public class AzureConnectionCheckServiceImpl implements AzureConnectionCheckService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AzureConnectionCheckServiceImpl.class);

    private final AzureConnectionValidator connectionValidator;
    private final AzureConnectionRepository connectionRepository;
    private final AzureConnectionService connectionService;
    private final AzureConnectionQueryService connectionQueryService;
    private final AzureResourceManagerQueryService resourceManagerQueryService;
    private final LoggingService loggingService;

    @Override
    public AzureConnectionResponse verifyConnection(UUID organizationId, UUID connectionId) {
        AzureConnection connection = connectionValidator.ensureConnectionExists(organizationId, connectionId);
        connectionValidator.ensureClientIdSet(connection);

        checkConnection(connectionId);

        return connectionQueryService.getConnection(organizationId, connectionId);
    }

    @Override
    public void checkConnection(UUID connectionId) {
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, CHECK_STARTED, connectionId);

        Optional<AzureConnection> found = connectionRepository.findById(connectionId);
        if (found.isEmpty()) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, CHECK_SKIPPED_CONNECTION_GONE, connectionId);
            return;
        }
        UUID checkedTenantId = found.get().getTenantId();
        UUID checkedClientId = found.get().getClientId();

        List<AzureSubscriptionPayload> subscriptions;
        try {
            subscriptions = resourceManagerQueryService.listSubscriptions(connectionId);
        } catch (ApplicationException exception) {
            String errorCode = toLastError(exception);
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, CHECK_FAILED, connectionId, errorCode);
            connectionService.recordCheckFailed(connectionId, checkedTenantId, checkedClientId, errorCode);
            return;
        }

        if (subscriptions.isEmpty()) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, CHECK_NO_SUBSCRIPTION, connectionId);
            connectionService.recordCheckFailed(connectionId, checkedTenantId, checkedClientId,
                    ExceptionDomainAzureError.STA_AZURE_CONNECTION_NO_SUBSCRIPTION.getCode());
            return;
        }

        connectionService.recordCheckSucceeded(connectionId, checkedTenantId, checkedClientId, subscriptions);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, CHECK_SUCCEEDED, connectionId, subscriptions.size());
    }

    /**
     * The error code stored for a failed read: Oba's code, followed by Entra's AADSTS code when
     * Entra refused the token exchange and named one, so the admin can look up which part of the
     * federated credential does not match. Entra's message itself is never kept.
     */
    private static String toLastError(ApplicationException exception) {
        Object aadstsCode = exception.getErrorValues() == null
                ? null
                : exception.getErrorValues().get(AzureResourceManagerConstant.FIELD_AADSTS_CODE);
        if (aadstsCode == null) {
            return exception.getErrorCode();
        }
        return String.format(AzureConnectionConstant.LAST_ERROR_WITH_DETAIL_FORMAT, exception.getErrorCode(),
                aadstsCode);
    }
}
