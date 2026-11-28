package com.korur.oba.connect.azure.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.azure.dto.AzureSubscriptionPayload;
import com.korur.oba.connect.azure.dto.request.AzureConnectionRequest;
import com.korur.oba.connect.azure.dto.response.AzureConnectionResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Writes to an organization's Azure connections: the admin's create, edit and delete, and the
 * result of a check, each in its own transaction.
 */
public interface AzureConnectionService {

    /**
     * Creates a PENDING connection whose subject is fixed from the organization and the new id.
     *
     * @param user           the admin creating it, stored as the creator
     * @param organizationId organization of the request
     * @param request        name, tenant id and optional client id
     * @return the new connection with its setup values
     */
    AzureConnectionResponse createConnection(User user, UUID organizationId, AzureConnectionRequest request);

    /**
     * Replaces the name, tenant id and client id. A change of the tenant or client id resets the
     * connection to PENDING and clears its check results, because they belong to the old identity.
     *
     * @param organizationId organization of the request
     * @param connectionId   ID of the connection
     * @param request        the new values
     * @return the updated connection
     */
    AzureConnectionResponse updateConnection(UUID organizationId, UUID connectionId, AzureConnectionRequest request);

    /**
     * Deletes a connection with its subscriptions and drops its cached credential.
     *
     * @param organizationId organization of the request
     * @param connectionId   ID of the connection
     */
    void deleteConnection(UUID organizationId, UUID connectionId);

    /**
     * Records a successful check: replaces the subscriptions, sets CONNECTED, the verification time
     * and no error. Does nothing but log when the connection was deleted during the check.
     *
     * @param connectionId  ID of the connection
     * @param subscriptions the subscriptions the identity can read, not empty
     */
    void recordCheckSucceeded(UUID connectionId, List<AzureSubscriptionPayload> subscriptions);

    /**
     * Records a failed check: keeps the subscriptions, stores the error code, and moves a CONNECTED
     * or FAILING connection to FAILING while a PENDING one stays PENDING. Does nothing but log when
     * the connection was deleted during the check.
     *
     * @param connectionId ID of the connection
     * @param errorCode    the error code to show, never a raw Entra or Azure message
     */
    void recordCheckFailed(UUID connectionId, String errorCode);
}
