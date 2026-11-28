package com.korur.oba.connect.azure.enums;

/**
 * State of an organization's Azure connection, as the last check left it. Only the check changes
 * it, so the status always reflects what Azure answered, never what an admin typed.
 *
 * Backed by the PostgreSQL enum shrine_azure_connection_status_enum; a new constant needs a
 * migration that adds the same value.
 */
public enum AzureConnectionStatus {

    // Created or changed, and no check has succeeded since; the identity may not exist yet.
    PENDING,

    // The last check exchanged a token and listed at least one subscription.
    CONNECTED,

    // A check succeeded once, and the latest one failed; the subscriptions of the last success are kept.
    FAILING
}
