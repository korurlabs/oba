package com.korur.oba.connect.azure.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 2. Third-party
import org.hibernate.exception.ConstraintViolationException;

// 3. Java SDK
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.azure.client.AzureConnectionTokenProvider;
import com.korur.oba.connect.azure.constant.AzureConnectionConstant;
import com.korur.oba.connect.azure.dto.AzureSubscriptionPayload;
import com.korur.oba.connect.azure.dto.request.AzureConnectionRequest;
import com.korur.oba.connect.azure.dto.response.AzureConnectionResponse;
import com.korur.oba.connect.azure.entity.AzureConnection;
import com.korur.oba.connect.azure.entity.AzureConnectionSubscription;
import com.korur.oba.connect.azure.enums.AzureConnectionStatus;
import com.korur.oba.connect.azure.exception.ExceptionDomainAzureError;
import com.korur.oba.connect.azure.mapper.AzureConnectionMapperImpl;
import com.korur.oba.connect.azure.mock_data.AzureConnectionMocks;
import com.korur.oba.connect.azure.repository.AzureConnectionRepository;
import com.korur.oba.connect.azure.repository.AzureConnectionSubscriptionRepository;
import com.korur.oba.connect.azure.service.AzureConnectionQueryService;
import com.korur.oba.connect.azure.validator.AzureConnectionValidator;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AzureConnectionServiceImplUnitTest {

    private static final Instant NOW = Instant.parse("2026-11-28T04:30:00Z");

    private final AzureConnectionRepository connectionRepository = mock(AzureConnectionRepository.class);
    private final AzureConnectionSubscriptionRepository subscriptionRepository =
            mock(AzureConnectionSubscriptionRepository.class);
    private final AzureConnectionValidator validator = mock(AzureConnectionValidator.class);
    private final AzureConnectionQueryService queryService = mock(AzureConnectionQueryService.class);
    private final AzureConnectionTokenProvider tokenProvider = mock(AzureConnectionTokenProvider.class);
    private final AzureConnectionServiceImpl service = new AzureConnectionServiceImpl(connectionRepository,
            subscriptionRepository, new AzureConnectionMapperImpl(), validator, queryService, tokenProvider,
            Clock.fixed(NOW, ZoneOffset.UTC), mock(LoggingService.class));

    private final UUID organizationId = UUID.randomUUID();

    @Test
    void createConnection_validRequest_savesPendingRowOfOrganizationAndCreatorWithoutSubject() {
        User user = new User();
        user.setId(UUID.randomUUID());
        AzureConnectionRequest request = new AzureConnectionRequest("Production", UUID.randomUUID(), null);
        UUID newId = UUID.randomUUID();
        AzureConnectionResponse expected = new AzureConnectionResponse(newId, "Production", request.tenantId(), null,
                AzureConnectionStatus.PENDING, null, null, NOW, List.of(), null);
        when(connectionRepository.saveAndFlush(any(AzureConnection.class))).thenAnswer(invocation -> {
            AzureConnection saved = invocation.getArgument(0);
            saved.setId(newId);
            return saved;
        });
        when(queryService.getConnection(organizationId, newId)).thenReturn(expected);

        AzureConnectionResponse result = service.createConnection(user, organizationId, request);

        assertThat(result).isSameAs(expected);
        InOrder order = inOrder(validator, connectionRepository);
        order.verify(validator).ensureIssuerConfigured(organizationId);
        order.verify(validator).ensureNameIsUnique(organizationId, "Production");
        order.verify(validator).ensureIdentityIsNew(organizationId, request.tenantId(), null);
        ArgumentCaptor<AzureConnection> saved = ArgumentCaptor.forClass(AzureConnection.class);
        order.verify(connectionRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getOrganizationId()).isEqualTo(organizationId);
        assertThat(saved.getValue().getCreatedById()).isEqualTo(user.getId());
        assertThat(saved.getValue().getTenantId()).isEqualTo(request.tenantId());
        assertThat(saved.getValue().getStatus()).isEqualTo(AzureConnectionStatus.PENDING);
        // The entity fixes the subject from the organization and the new id when it is persisted.
        assertThat(saved.getValue().getSubject()).isNull();
    }

    @Test
    void createConnection_noIssuer_savesNothing() {
        User user = new User();
        user.setId(UUID.randomUUID());
        doThrow(new NotFoundException(ExceptionDomainAzureError.NOT_AZURE_CONNECTIONS_UNAVAILABLE))
                .when(validator).ensureIssuerConfigured(organizationId);

        assertThatThrownBy(() -> service.createConnection(user, organizationId,
                new AzureConnectionRequest("Production", UUID.randomUUID(), null)))
                .isInstanceOf(NotFoundException.class);

        verify(connectionRepository, never()).saveAndFlush(any());
    }

    @Test
    void createConnection_nameTakenConcurrently_isNameConflict() {
        User user = new User();
        user.setId(UUID.randomUUID());
        when(connectionRepository.saveAndFlush(any(AzureConnection.class)))
                .thenThrow(integrityViolation(AzureConnectionConstant.NAME_UNIQUE_CONSTRAINT));

        assertThatThrownBy(() -> service.createConnection(user, organizationId,
                new AzureConnectionRequest("Production", UUID.randomUUID(), null)))
                .isInstanceOfSatisfying(ConflictException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ExceptionDomainAzureError.STA_AZURE_CONNECTION_NAME_DUPLICATE.getCode()));
    }

    @Test
    void createConnection_identityTakenConcurrently_isIdentityConflict() {
        User user = new User();
        user.setId(UUID.randomUUID());
        when(connectionRepository.saveAndFlush(any(AzureConnection.class)))
                .thenThrow(integrityViolation(AzureConnectionConstant.IDENTITY_UNIQUE_CONSTRAINT));

        assertThatThrownBy(() -> service.createConnection(user, organizationId,
                new AzureConnectionRequest("Production", UUID.randomUUID(), UUID.randomUUID())))
                .isInstanceOfSatisfying(ConflictException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ExceptionDomainAzureError.STA_AZURE_CONNECTION_IDENTITY_DUPLICATE.getCode()));
    }

    @Test
    void updateConnection_newClientId_resetsStatusResultsAndSubscriptions() {
        AzureConnection connection = connectedConnection();
        String subject = connection.getSubject();
        when(validator.ensureConnectionExists(organizationId, connection.getId())).thenReturn(connection);
        when(connectionRepository.saveAndFlush(connection)).thenReturn(connection);
        AzureConnectionRequest request =
                new AzureConnectionRequest("Production", connection.getTenantId(), UUID.randomUUID());

        service.updateConnection(organizationId, connection.getId(), request);

        assertThat(connection.getClientId()).isEqualTo(request.clientId());
        assertThat(connection.getStatus()).isEqualTo(AzureConnectionStatus.PENDING);
        assertThat(connection.getLastVerifiedAt()).isNull();
        assertThat(connection.getLastError()).isNull();
        assertThat(connection.getSubject()).isEqualTo(subject);
        verify(subscriptionRepository).deleteByConnectionId(connection.getId());
        verify(validator).ensureNameIsUnique(organizationId, "Production", connection.getId());
        verify(validator).ensureIdentityIsNew(organizationId, request.tenantId(), request.clientId(),
                connection.getId());
    }

    @Test
    void updateConnection_renameOnly_keepsStatusAndSubscriptions() {
        AzureConnection connection = connectedConnection();
        when(validator.ensureConnectionExists(organizationId, connection.getId())).thenReturn(connection);
        when(connectionRepository.saveAndFlush(connection)).thenReturn(connection);
        AzureConnectionRequest request =
                new AzureConnectionRequest("Renamed", connection.getTenantId(), connection.getClientId());

        service.updateConnection(organizationId, connection.getId(), request);

        assertThat(connection.getName()).isEqualTo("Renamed");
        assertThat(connection.getStatus()).isEqualTo(AzureConnectionStatus.CONNECTED);
        assertThat(connection.getLastVerifiedAt()).isEqualTo(NOW);
        verify(subscriptionRepository, never()).deleteByConnectionId(any());
    }

    @Test
    void deleteConnection_notReferenced_deletesAndEvictsCredential() {
        AzureConnection connection = AzureConnectionMocks.connection();
        when(validator.ensureConnectionExists(organizationId, connection.getId())).thenReturn(connection);

        service.deleteConnection(organizationId, connection.getId());

        InOrder order = inOrder(connectionRepository, tokenProvider);
        order.verify(connectionRepository).delete(connection);
        order.verify(connectionRepository).flush();
        order.verify(tokenProvider).evict(connection.getId());
    }

    @Test
    void deleteConnection_inUse_isConflictAndKeepsCredential() {
        AzureConnection connection = AzureConnectionMocks.connection();
        when(validator.ensureConnectionExists(organizationId, connection.getId())).thenReturn(connection);
        doThrow(integrityViolation("backup_sources_connection_id_fk")).when(connectionRepository).flush();

        assertThatThrownBy(() -> service.deleteConnection(organizationId, connection.getId()))
                .isInstanceOfSatisfying(ConflictException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ExceptionDomainAzureError.STA_AZURE_CONNECTION_IN_USE.getCode()));

        verify(tokenProvider, never()).evict(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordCheckSucceeded_replacesSubscriptionsSetsConnectedAndBoundsAzureText() {
        AzureConnection connection = AzureConnectionMocks.connection();
        connection.setStatus(AzureConnectionStatus.FAILING);
        connection.setLastError("AZR_UPS_006");
        when(connectionRepository.findByIdForUpdate(connection.getId())).thenReturn(Optional.of(connection));
        UUID first = UUID.randomUUID();
        List<AzureSubscriptionPayload> subscriptions = List.of(
                new AzureSubscriptionPayload(first, "x".repeat(300), "Enabled"),
                new AzureSubscriptionPayload(first, "Duplicate", "Enabled"),
                new AzureSubscriptionPayload(UUID.randomUUID(), "Staging", "S".repeat(40)));

        service.recordCheckSucceeded(connection.getId(), subscriptions);

        InOrder order = inOrder(subscriptionRepository);
        order.verify(subscriptionRepository).deleteByConnectionId(connection.getId());
        ArgumentCaptor<List<AzureConnectionSubscription>> rows = ArgumentCaptor.forClass(List.class);
        order.verify(subscriptionRepository).saveAll(rows.capture());
        assertThat(rows.getValue()).hasSize(2);
        assertThat(rows.getValue().get(0).getDisplayName()).hasSize(255);
        assertThat(rows.getValue().get(1).getState()).hasSize(32);
        assertThat(rows.getValue().get(0).getConnection()).isSameAs(connection);
        assertThat(connection.getStatus()).isEqualTo(AzureConnectionStatus.CONNECTED);
        assertThat(connection.getLastVerifiedAt()).isEqualTo(NOW);
        assertThat(connection.getLastError()).isNull();
    }

    @Test
    void recordCheckSucceeded_connectionDeleted_writesNothing() {
        UUID connectionId = UUID.randomUUID();
        when(connectionRepository.findByIdForUpdate(connectionId)).thenReturn(Optional.empty());

        service.recordCheckSucceeded(connectionId,
                List.of(new AzureSubscriptionPayload(UUID.randomUUID(), "Production", "Enabled")));

        verify(subscriptionRepository, never()).deleteByConnectionId(any());
        verify(subscriptionRepository, never()).saveAll(anyList());
    }

    @Test
    void recordCheckFailed_connected_becomesFailingAndKeepsSubscriptions() {
        AzureConnection connection = connectedConnection();
        when(connectionRepository.findByIdForUpdate(connection.getId())).thenReturn(Optional.of(connection));

        service.recordCheckFailed(connection.getId(), "AZR_UPS_009:AADSTS700213");

        assertThat(connection.getStatus()).isEqualTo(AzureConnectionStatus.FAILING);
        assertThat(connection.getLastError()).isEqualTo("AZR_UPS_009:AADSTS700213");
        assertThat(connection.getLastVerifiedAt()).isEqualTo(NOW);
        verify(subscriptionRepository, never()).deleteByConnectionId(any());
    }

    @Test
    void recordCheckFailed_pending_staysPending() {
        AzureConnection connection = AzureConnectionMocks.connection();
        when(connectionRepository.findByIdForUpdate(connection.getId())).thenReturn(Optional.of(connection));

        service.recordCheckFailed(connection.getId(), "AZR_UPS_006");

        assertThat(connection.getStatus()).isEqualTo(AzureConnectionStatus.PENDING);
        assertThat(connection.getLastError()).isEqualTo("AZR_UPS_006");
    }

    @Test
    void recordCheckFailed_failing_staysFailing() {
        AzureConnection connection = AzureConnectionMocks.connection();
        connection.setStatus(AzureConnectionStatus.FAILING);
        when(connectionRepository.findByIdForUpdate(connection.getId())).thenReturn(Optional.of(connection));

        service.recordCheckFailed(connection.getId(), "AZR_STA_005");

        assertThat(connection.getStatus()).isEqualTo(AzureConnectionStatus.FAILING);
    }

    @Test
    void recordCheckFailed_connectionDeleted_writesNothing() {
        UUID connectionId = UUID.randomUUID();
        when(connectionRepository.findByIdForUpdate(connectionId)).thenReturn(Optional.empty());

        service.recordCheckFailed(connectionId, "AZR_UPS_006");

        verify(connectionRepository, never()).save(any());
    }

    private static AzureConnection connectedConnection() {
        AzureConnection connection = AzureConnectionMocks.connection();
        connection.setStatus(AzureConnectionStatus.CONNECTED);
        connection.setLastVerifiedAt(NOW);
        return connection;
    }

    private static DataIntegrityViolationException integrityViolation(String constraintName) {
        return new DataIntegrityViolationException("constraint",
                new ConstraintViolationException("duplicate", new SQLException("duplicate"), constraintName));
    }
}
