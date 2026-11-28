package com.korur.oba.connect.azure.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.azure.constant.AzureConnectionConstant;
import com.korur.oba.connect.azure.dto.response.AzureConnectionResponse;
import com.korur.oba.connect.azure.entity.AzureConnection;
import com.korur.oba.connect.azure.entity.AzureConnectionSubscription;
import com.korur.oba.connect.azure.enums.AzureConnectionStatus;
import com.korur.oba.connect.azure.exception.ExceptionDomainAzureError;
import com.korur.oba.connect.azure.mapper.AzureConnectionMapperImpl;
import com.korur.oba.connect.azure.mock_data.AzureConnectionMocks;
import com.korur.oba.connect.azure.repository.AzureConnectionRepository;
import com.korur.oba.connect.azure.repository.AzureConnectionSubscriptionRepository;
import com.korur.oba.connect.azure.validator.AzureConnectionValidator;
import com.korur.oba.connect.oidc.dto.response.OidcDiscoveryResponse;
import com.korur.oba.connect.oidc.service.OidcAssertionService;
import com.korur.oba.dojo.exception.internal.business.ConflictException;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AzureConnectionQueryServiceImplUnitTest {

    private static final String ISSUER = "https://api.example.com";

    private final AzureConnectionRepository connectionRepository = mock(AzureConnectionRepository.class);
    private final AzureConnectionSubscriptionRepository subscriptionRepository =
            mock(AzureConnectionSubscriptionRepository.class);
    private final AzureConnectionValidator validator = mock(AzureConnectionValidator.class);
    private final OidcAssertionService oidcAssertionService = mock(OidcAssertionService.class);
    private final AzureConnectionQueryServiceImpl service = new AzureConnectionQueryServiceImpl(connectionRepository,
            subscriptionRepository, new AzureConnectionMapperImpl(), validator, oidcAssertionService);

    private final UUID organizationId = UUID.randomUUID();

    @Test
    void getConnection_issuerConfigured_answersSetupValuesAndSubscriptions() {
        AzureConnection connection = AzureConnectionMocks.connection();
        AzureConnectionSubscription subscription = subscription(connection, "Production");
        when(validator.ensureConnectionExists(organizationId, connection.getId())).thenReturn(connection);
        when(subscriptionRepository.findByConnectionId(connection.getId())).thenReturn(List.of(subscription));
        issuerConfigured();

        AzureConnectionResponse response = service.getConnection(organizationId, connection.getId());

        assertThat(response.id()).isEqualTo(connection.getId());
        assertThat(response.setup().issuer()).isEqualTo(ISSUER);
        assertThat(response.setup().subject()).isEqualTo(connection.getSubject());
        assertThat(response.setup().audience()).isEqualTo(AzureConnectionConstant.TOKEN_EXCHANGE_AUDIENCE);
        assertThat(response.subscriptions()).hasSize(1);
        assertThat(response.subscriptions().get(0).subscriptionId()).isEqualTo(subscription.getSubscriptionId());
    }

    @Test
    void getConnection_noIssuer_answersWithoutIssuer() {
        AzureConnection connection = AzureConnectionMocks.connection();
        when(validator.ensureConnectionExists(organizationId, connection.getId())).thenReturn(connection);
        when(oidcAssertionService.isConfigured()).thenReturn(false);

        AzureConnectionResponse response = service.getConnection(organizationId, connection.getId());

        assertThat(response.setup().issuer()).isNull();
        assertThat(response.setup().subject()).isEqualTo(connection.getSubject());
        verify(oidcAssertionService, never()).getDiscovery();
    }

    @Test
    void listConnections_severalConnections_readsSubscriptionsOnceAndGroupsThem() {
        AzureConnection first = AzureConnectionMocks.connection();
        AzureConnection second = AzureConnectionMocks.connection();
        when(connectionRepository.findAllByOrganizationIdOrderByNameAsc(organizationId))
                .thenReturn(List.of(first, second));
        when(subscriptionRepository.findByConnectionIdIn(List.of(first.getId(), second.getId())))
                .thenReturn(List.of(subscription(first, "A"), subscription(first, "B")));
        issuerConfigured();

        List<AzureConnectionResponse> responses = service.listConnections(organizationId);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).subscriptions()).hasSize(2);
        assertThat(responses.get(1).subscriptions()).isEmpty();
    }

    @Test
    void listConnections_none_answersEmptyWithoutSubscriptionQuery() {
        when(connectionRepository.findAllByOrganizationIdOrderByNameAsc(organizationId)).thenReturn(List.of());

        assertThat(service.listConnections(organizationId)).isEmpty();

        verify(subscriptionRepository, never()).findByConnectionIdIn(anyList());
    }

    @Test
    void getConnectedConnection_notConnected_isConflict() {
        AzureConnection connection = AzureConnectionMocks.connection();
        when(validator.ensureConnectionExists(organizationId, connection.getId())).thenReturn(connection);
        doThrow(new ConflictException(ExceptionDomainAzureError.STA_AZURE_CONNECTION_NOT_CONNECTED))
                .when(validator).ensureConnected(connection);

        assertThatThrownBy(() -> service.getConnectedConnection(organizationId, connection.getId()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void isConnected_connectedPendingOrMissing_answersOnlyConnected() {
        AzureConnection connected = AzureConnectionMocks.connection();
        connected.setStatus(AzureConnectionStatus.CONNECTED);
        AzureConnection pending = AzureConnectionMocks.connection();
        UUID missing = UUID.randomUUID();
        when(connectionRepository.findById(connected.getId())).thenReturn(Optional.of(connected));
        when(connectionRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
        when(connectionRepository.findById(missing)).thenReturn(Optional.empty());

        assertThat(service.isConnected(connected.getId())).isTrue();
        assertThat(service.isConnected(pending.getId())).isFalse();
        assertThat(service.isConnected(missing)).isFalse();
    }

    @Test
    void listCheckableConnectionIds_asksForConnectedAndFailing() {
        service.listCheckableConnectionIds();

        verify(connectionRepository).findIdsByStatusIn(
                List.of(AzureConnectionStatus.CONNECTED, AzureConnectionStatus.FAILING));
    }

    private void issuerConfigured() {
        when(oidcAssertionService.isConfigured()).thenReturn(true);
        when(oidcAssertionService.getDiscovery()).thenReturn(new OidcDiscoveryResponse(ISSUER,
                ISSUER + "/.well-known/jwks.json", List.of("id_token"), List.of("public"), List.of("RS256")));
    }

    private static AzureConnectionSubscription subscription(AzureConnection connection, String displayName) {
        AzureConnectionSubscription subscription = AzureConnectionSubscription.builder()
                .connection(connection)
                .subscriptionId(UUID.randomUUID())
                .displayName(displayName)
                .state("Enabled")
                .build();
        subscription.setId(UUID.randomUUID());
        return subscription;
    }
}
