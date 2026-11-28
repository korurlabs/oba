package com.korur.oba.connect.azure.dto;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * A subscription the identity of a connection can read.
 *
 * @param subscriptionId the subscription id
 * @param displayName    the name the subscription's owner gave it
 * @param state          Azure's state as text, because Azure owns the list of states
 */
public record AzureSubscriptionPayload(
        UUID subscriptionId,
        String displayName,
        String state
) {
}
