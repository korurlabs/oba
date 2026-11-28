package com.korur.oba.connect.azure.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.azure.dto.response.AzureConnectionResponse;

/**
 * Checks exactly one Azure connection: it asks Entra ID for a token and Azure Resource Manager for
 * the subscriptions outside any transaction, so no row lock is held during a network call, and
 * commits the result in that connection's own transaction
 * ({@link AzureConnectionService#recordCheckSucceeded} or
 * {@link AzureConnectionService#recordCheckFailed}). A failure therefore touches only that
 * connection, which is why the daily check calls it per connection and not once for all.
 */
public interface AzureConnectionCheckService {

    /**
     * Checks a connection of the organization on an admin's request and returns the stored result.
     * An Azure refusal is a stored result, not an error of the request.
     *
     * @param organizationId organization of the request
     * @param connectionId   ID of the connection
     * @return the connection after the check
     */
    AzureConnectionResponse verifyConnection(UUID organizationId, UUID connectionId);

    /**
     * Checks one connection and stores the result.
     *
     * @param connectionId ID of the connection
     */
    void checkConnection(UUID connectionId);
}
