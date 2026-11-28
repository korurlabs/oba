package com.korur.oba.connect.azure.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.azure.constant.AzureResourceManagerConstant;
import com.korur.oba.connect.azure.dto.AzureSubscriptionPayload;
import com.korur.oba.connect.azure.dto.response.AzureConnectionResponse;
import com.korur.oba.connect.azure.entity.AzureConnection;
import com.korur.oba.connect.azure.exception.ExceptionDomainAzureError;
import com.korur.oba.connect.azure.mock_data.AzureConnectionMocks;
import com.korur.oba.connect.azure.repository.AzureConnectionRepository;
import com.korur.oba.connect.azure.service.AzureConnectionQueryService;
import com.korur.oba.connect.azure.service.AzureConnectionService;
import com.korur.oba.connect.azure.service.AzureResourceManagerQueryService;
import com.korur.oba.connect.azure.validator.AzureConnectionValidator;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.exception.internal.infrastructure.ApplicationException;
import com.korur.oba.dojo.exception.internal.infrastructure.BadGatewayException;
import com.korur.oba.dojo.logging.service.LoggingService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AzureConnectionCheckServiceImplUnitTest {

    private final AzureConnectionValidator validator = mock(AzureConnectionValidator.class);
    private final AzureConnectionRepository connectionRepository = mock(AzureConnectionRepository.class);
    private final AzureConnectionService connectionService = mock(AzureConnectionService.class);
    private final AzureConnectionQueryService queryService = mock(AzureConnectionQueryService.class);
    private final AzureResourceManagerQueryService resourceManagerQueryService =
            mock(AzureResourceManagerQueryService.class);
    private final AzureConnectionCheckServiceImpl service = new AzureConnectionCheckServiceImpl(validator,
            connectionRepository, connectionService, queryService, resourceManagerQueryService,
            mock(LoggingService.class));

    private final AzureConnection checked = AzureConnectionMocks.connection();
    private final UUID connectionId = checked.getId();
    private final UUID tenantId = checked.getTenantId();
    private final UUID clientId = checked.getClientId();

    @BeforeEach
    void setUp() {
        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(checked));
    }

    @Test
    void checkConnection_subscriptionsVisible_recordsSuccessWithThem() {
        List<AzureSubscriptionPayload> subscriptions =
                List.of(new AzureSubscriptionPayload(UUID.randomUUID(), "Production", "Enabled"));
        when(resourceManagerQueryService.listSubscriptions(connectionId)).thenReturn(subscriptions);

        service.checkConnection(connectionId);

        verify(connectionService).recordCheckSucceeded(connectionId, tenantId, clientId, subscriptions);
        verify(connectionService, never()).recordCheckFailed(any(), any(), any(), anyString());
    }

    @Test
    void checkConnection_noSubscriptionVisible_recordsNoSubscriptionFailure() {
        when(resourceManagerQueryService.listSubscriptions(connectionId)).thenReturn(List.of());

        service.checkConnection(connectionId);

        verify(connectionService).recordCheckFailed(connectionId, tenantId, clientId,
                ExceptionDomainAzureError.STA_AZURE_CONNECTION_NO_SUBSCRIPTION.getCode());
        verify(connectionService, never()).recordCheckSucceeded(any(), any(), any(), any());
    }

    @Test
    void checkConnection_tokenExchangeRefusedWithAadstsCode_recordsCodeWithAadstsCode() {
        when(resourceManagerQueryService.listSubscriptions(connectionId)).thenThrow(new BadGatewayException(
                ExceptionDomainAzureError.UPS_AZURE_TOKEN_EXCHANGE_REFUSED,
                Map.of(AzureResourceManagerConstant.FIELD_AADSTS_CODE, "AADSTS700213")));

        service.checkConnection(connectionId);

        verify(connectionService).recordCheckFailed(connectionId, tenantId, clientId, "AZR_UPS_009:AADSTS700213");
    }

    @Test
    void checkConnection_credentialsRefused_recordsErrorCodeOnly() {
        when(resourceManagerQueryService.listSubscriptions(connectionId)).thenThrow(
                new BadGatewayException(ExceptionDomainAzureError.UPS_AZURE_ARM_CREDENTIALS_REFUSED));

        service.checkConnection(connectionId);

        verify(connectionService).recordCheckFailed(connectionId, tenantId, clientId,
                ExceptionDomainAzureError.UPS_AZURE_ARM_CREDENTIALS_REFUSED.getCode());
    }

    @Test
    void checkConnection_databaseFailure_goesUpWithoutRecording() {
        when(resourceManagerQueryService.listSubscriptions(connectionId))
                .thenThrow(new IllegalStateException("database down"));

        assertThatThrownBy(() -> service.checkConnection(connectionId)).isInstanceOf(IllegalStateException.class);

        verify(connectionService, never()).recordCheckFailed(any(), any(), any(), anyString());
    }

    @Test
    void checkConnection_identityChangedDuringRead_passesTheIdentityItReadWith() {
        List<AzureSubscriptionPayload> subscriptions =
                List.of(new AzureSubscriptionPayload(UUID.randomUUID(), "Production", "Enabled"));
        when(resourceManagerQueryService.listSubscriptions(connectionId)).thenAnswer(invocation -> {
            checked.setTenantId(UUID.randomUUID());
            checked.setClientId(UUID.randomUUID());
            return subscriptions;
        });

        service.checkConnection(connectionId);

        verify(connectionService).recordCheckSucceeded(connectionId, tenantId, clientId, subscriptions);
    }

    @Test
    void checkConnection_connectionGone_readsNoAzureAndRecordsNothing() {
        when(connectionRepository.findById(connectionId)).thenReturn(Optional.empty());

        service.checkConnection(connectionId);

        verify(resourceManagerQueryService, never()).listSubscriptions(any());
        verify(connectionService, never()).recordCheckSucceeded(any(), any(), any(), any());
        verify(connectionService, never()).recordCheckFailed(any(), any(), any(), anyString());
    }

    @Test
    void verifyConnection_connectionWithClientId_checksThenAnswersStoredResult() {
        UUID organizationId = UUID.randomUUID();
        AzureConnection connection = AzureConnectionMocks.connection();
        AzureConnectionResponse stored = new AzureConnectionResponse(connectionId, "Production", null, null, null,
                null, null, null, List.of(), null);
        when(validator.ensureConnectionExists(organizationId, connectionId)).thenReturn(connection);
        when(resourceManagerQueryService.listSubscriptions(connectionId)).thenReturn(List.of());
        when(queryService.getConnection(organizationId, connectionId)).thenReturn(stored);

        AzureConnectionResponse result = service.verifyConnection(organizationId, connectionId);

        assertThat(result).isSameAs(stored);
        InOrder order = inOrder(validator, resourceManagerQueryService, queryService);
        order.verify(validator).ensureConnectionExists(organizationId, connectionId);
        order.verify(validator).ensureClientIdSet(connection);
        order.verify(resourceManagerQueryService).listSubscriptions(connectionId);
        order.verify(queryService).getConnection(organizationId, connectionId);
    }

    @Test
    void verifyConnection_noClientId_refusesWithoutAzureCall() {
        UUID organizationId = UUID.randomUUID();
        AzureConnection connection = AzureConnectionMocks.connection(null);
        when(validator.ensureConnectionExists(organizationId, connectionId)).thenReturn(connection);
        doThrow(new ValidationException(ExceptionDomainAzureError.VAL_AZURE_CONNECTION_CLIENT_ID_MISSING))
                .when(validator).ensureClientIdSet(connection);

        assertThatThrownBy(() -> service.verifyConnection(organizationId, connectionId))
                .isInstanceOfSatisfying(ApplicationException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ExceptionDomainAzureError.VAL_AZURE_CONNECTION_CLIENT_ID_MISSING.getCode()));

        verify(resourceManagerQueryService, never()).listSubscriptions(any());
    }
}
