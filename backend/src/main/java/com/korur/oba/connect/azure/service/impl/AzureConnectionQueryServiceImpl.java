package com.korur.oba.connect.azure.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.azure.dto.response.AzureConnectionResponse;
import com.korur.oba.connect.azure.entity.AzureConnection;
import com.korur.oba.connect.azure.entity.AzureConnectionSubscription;
import com.korur.oba.connect.azure.enums.AzureConnectionStatus;
import com.korur.oba.connect.azure.mapper.AzureConnectionMapper;
import com.korur.oba.connect.azure.repository.AzureConnectionRepository;
import com.korur.oba.connect.azure.repository.AzureConnectionSubscriptionRepository;
import com.korur.oba.connect.azure.service.AzureConnectionQueryService;
import com.korur.oba.connect.azure.validator.AzureConnectionValidator;
import com.korur.oba.connect.oidc.service.OidcAssertionService;

/**
 * Reads an organization's Azure connections. Every read for a user loads the connection by id and
 * organization through {@link AzureConnectionValidator#ensureConnectionExists}, so another
 * organization's connection answers 404; the reads by id alone serve background jobs that already
 * hold a connection id from a row of the same organization.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AzureConnectionQueryServiceImpl implements AzureConnectionQueryService {

    private static final List<AzureConnectionStatus> CHECKABLE_STATUSES =
            List.of(AzureConnectionStatus.CONNECTED, AzureConnectionStatus.FAILING);

    private final AzureConnectionRepository connectionRepository;
    private final AzureConnectionSubscriptionRepository subscriptionRepository;
    private final AzureConnectionMapper connectionMapper;
    private final AzureConnectionValidator connectionValidator;
    private final OidcAssertionService oidcAssertionService;

    @Override
    public List<AzureConnectionResponse> listConnections(UUID organizationId) {
        List<AzureConnection> connections = connectionRepository.findAllByOrganizationIdOrderByNameAsc(organizationId);
        List<AzureConnectionResponse> responses = new ArrayList<>();
        if (connections.isEmpty()) {
            return responses;
        }

        List<UUID> connectionIds = new ArrayList<>();
        for (AzureConnection connection : connections) {
            connectionIds.add(connection.getId());
        }
        Map<UUID, List<AzureConnectionSubscription>> subscriptionsByConnection =
                groupByConnection(subscriptionRepository.findByConnectionIdIn(connectionIds));

        String issuerUrl = issuerUrl();
        for (AzureConnection connection : connections) {
            List<AzureConnectionSubscription> subscriptions = subscriptionsByConnection.get(connection.getId());
            if (subscriptions == null) {
                subscriptions = new ArrayList<>();
            }
            responses.add(connectionMapper.toResponse(connection, subscriptions, issuerUrl));
        }
        return responses;
    }

    @Override
    public AzureConnectionResponse getConnection(UUID organizationId, UUID connectionId) {
        AzureConnection connection = connectionValidator.ensureConnectionExists(organizationId, connectionId);
        return toResponse(connection);
    }

    @Override
    public AzureConnectionResponse getConnectedConnection(UUID organizationId, UUID connectionId) {
        AzureConnection connection = connectionValidator.ensureConnectionExists(organizationId, connectionId);
        connectionValidator.ensureConnected(connection);
        return toResponse(connection);
    }

    @Override
    public boolean isConnected(UUID connectionId) {
        Optional<AzureConnection> connection = connectionRepository.findById(connectionId);
        return connection.isPresent() && connection.get().getStatus() == AzureConnectionStatus.CONNECTED;
    }

    @Override
    public boolean isSubscriptionListed(UUID connectionId, UUID subscriptionId) {
        return subscriptionRepository.existsByConnectionIdAndSubscriptionId(connectionId, subscriptionId);
    }

    @Override
    public List<UUID> listCheckableConnectionIds() {
        return connectionRepository.findIdsByStatusIn(CHECKABLE_STATUSES);
    }

    private AzureConnectionResponse toResponse(AzureConnection connection) {
        List<AzureConnectionSubscription> subscriptions = subscriptionRepository.findByConnectionId(connection.getId());
        return connectionMapper.toResponse(connection, subscriptions, issuerUrl());
    }

    /**
     * The issuer URL the admin enters in the federated credential, or null when the deployment has
     * no issuer: existing connections stay readable then, but cannot be checked.
     */
    private String issuerUrl() {
        if (!oidcAssertionService.isConfigured()) {
            return null;
        }
        return oidcAssertionService.getDiscovery().issuer();
    }

    /**
     * Groups subscriptions by their connection, keeping the order the repository returned.
     */
    private static Map<UUID, List<AzureConnectionSubscription>> groupByConnection(
            List<AzureConnectionSubscription> subscriptions) {
        Map<UUID, List<AzureConnectionSubscription>> grouped = new HashMap<>();
        for (AzureConnectionSubscription subscription : subscriptions) {
            UUID connectionId = subscription.getConnection().getId();
            List<AzureConnectionSubscription> group = grouped.get(connectionId);
            if (group == null) {
                group = new ArrayList<>();
                grouped.put(connectionId, group);
            }
            group.add(subscription);
        }
        return grouped;
    }
}
