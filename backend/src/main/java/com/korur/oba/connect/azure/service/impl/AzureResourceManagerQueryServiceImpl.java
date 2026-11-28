package com.korur.oba.connect.azure.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.azure.client.AzureResourceManagerClient;
import com.korur.oba.connect.azure.constant.AzureConnectionConstant;
import com.korur.oba.connect.azure.dto.AzureSubscriptionPayload;
import com.korur.oba.connect.azure.entity.AzureConnection;
import com.korur.oba.connect.azure.exception.ExceptionDomainAzureError;
import com.korur.oba.connect.azure.repository.AzureConnectionRepository;
import com.korur.oba.connect.azure.service.AzureResourceManagerQueryService;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

import static com.korur.oba.connect.azure.constant.service.AzureResourceManagerQueryServiceLogConstant.*;

/**
 * Reads Azure Resource Manager through one connection: it loads the connection and hands it to
 * {@link AzureResourceManagerClient}, which asks the connection's credential for a token.
 *
 * It runs outside any transaction on purpose. The connection is read in the repository's own short
 * transaction and the Azure call follows with no database connection or row lock held, so a slow
 * Azure answer never holds up a write of the same connection.
 */
@Service
@RequiredArgsConstructor
public class AzureResourceManagerQueryServiceImpl implements AzureResourceManagerQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AzureResourceManagerQueryServiceImpl.class);

    private final AzureConnectionRepository azureConnectionRepository;
    private final AzureResourceManagerClient azureResourceManagerClient;
    private final LoggingService loggingService;

    @Override
    public List<AzureSubscriptionPayload> listSubscriptions(UUID connectionId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, LISTING_SUBSCRIPTIONS, connectionId);
        AzureConnection connection = loadConnection(connectionId);
        return azureResourceManagerClient.listSubscriptions(connection);
    }

    private AzureConnection loadConnection(UUID connectionId) {
        return azureConnectionRepository.findById(connectionId).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, CONNECTION_NOT_FOUND, connectionId);
            return new NotFoundException(ExceptionDomainAzureError.NOT_AZURE_CONNECTION_FOUND,
                    Map.of(AzureConnectionConstant.FIELD_CONNECTION_ID, connectionId));
        });
    }
}
