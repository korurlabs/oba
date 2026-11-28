package com.korur.oba.connect.azure.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.azure.dto.AzureSubscriptionPayload;

/**
 * The only entry point through which the application reads Azure Resource Manager. Every method
 * takes the connection id first and reads as that connection's identity, so a caller never handles
 * a credential or a token.
 */
public interface AzureResourceManagerQueryService {

    /**
     * Lists the subscriptions the identity of the connection can read, as a check of the
     * connection does.
     *
     * @param connectionId the connection; it must have a client id
     * @return the subscriptions, empty when the identity can read none
     */
    List<AzureSubscriptionPayload> listSubscriptions(UUID connectionId);
}
