package com.korur.oba.connect.azure.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.azure.dto.response.AzureConnectionResponse;

/**
 * Reads of an organization's Azure connections, for the admins' pages and for the apps that read
 * Azure through a connection.
 */
public interface AzureConnectionQueryService {

    /**
     * Lists the organization's connections in name order, each with its subscriptions.
     *
     * @param organizationId organization of the request
     * @return the connections
     */
    List<AzureConnectionResponse> listConnections(UUID organizationId);

    /**
     * Returns one connection of the organization.
     *
     * @param organizationId organization of the request
     * @param connectionId   ID of the connection
     * @return the connection
     */
    AzureConnectionResponse getConnection(UUID organizationId, UUID connectionId);

    /**
     * Returns one connection of the organization that is CONNECTED, for a caller about to bind
     * something to it.
     *
     * @param organizationId organization of the request
     * @param connectionId   ID of the connection
     * @return the connection
     */
    AzureConnectionResponse getConnectedConnection(UUID organizationId, UUID connectionId);

    /**
     * Tells whether the connection exists and is CONNECTED, for a background job that holds only
     * its id.
     *
     * @param connectionId ID of the connection
     * @return true when the connection is CONNECTED
     */
    boolean isConnected(UUID connectionId);

    /**
     * Tells whether the identity of the connection could read the subscription at the last
     * successful check.
     *
     * @param connectionId   ID of the connection
     * @param subscriptionId the Azure subscription id
     * @return true when the subscription is listed
     */
    boolean isSubscriptionListed(UUID connectionId, UUID subscriptionId);

    /**
     * Lists the IDs of the connections the daily check re-verifies: every CONNECTED and FAILING
     * one. A PENDING connection has never been verified and waits for an admin.
     *
     * @return the IDs, in ID order
     */
    List<UUID> listCheckableConnectionIds();
}
