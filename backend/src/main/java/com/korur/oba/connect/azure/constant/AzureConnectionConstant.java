package com.korur.oba.connect.azure.constant;

/**
 * Request paths, field names, limits, validation messages, constraint names and federation values
 * of the Azure connections. The limits repeat the column lengths of shrine.azure_connections and
 * shrine.azure_connection_subscriptions.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AzureConnectionConstant {

    private AzureConnectionConstant() {
        // Private constructor to prevent instantiation
    }

    // --- Paths ---
    // Under no app: the connection belongs to the organization, and every app reuses it.
    public static final String CONNECTIONS_PATH = "/azure/connections";
    public static final String CONNECTION_PATH = "/{connectionId}";
    public static final String VERIFY_PATH = CONNECTION_PATH + "/verify";

    // --- Field names ---
    public static final String FIELD_CONNECTION_ID = "connectionId";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_TENANT_ID = "tenantId";
    public static final String FIELD_CLIENT_ID = "clientId";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_SUBSCRIPTION_ID = "subscriptionId";

    // --- Limits ---
    public static final int NAME_MAX_LENGTH = 255;
    // Entra ID accepts a federated credential subject of at most 600 characters.
    public static final int SUBJECT_MAX_LENGTH = 600;
    public static final int LAST_ERROR_MAX_LENGTH = 1000;
    public static final int SUBSCRIPTION_DISPLAY_NAME_MAX_LENGTH = 255;
    public static final int SUBSCRIPTION_STATE_MAX_LENGTH = 32;

    // --- Validation messages ---
    public static final String MSG_NAME_REQUIRED = "Name is required";
    public static final String MSG_NAME_SIZE = "Name must not exceed 255 characters";
    public static final String MSG_TENANT_ID_REQUIRED = "Tenant id is required";

    // --- Check results ---
    // lastError of a refused token exchange: Oba's error code, then Entra's AADSTS code when Entra sent one.
    public static final String LAST_ERROR_WITH_DETAIL_FORMAT = "%s:%s";

    // --- Constraints ---
    // Back the name and identity checks against a concurrent write.
    public static final String NAME_UNIQUE_CONSTRAINT = "azure_connections_organization_id_name_uk";
    public static final String IDENTITY_UNIQUE_CONSTRAINT = "azure_connections_organization_id_tenant_id_client_id_uk";
    public static final String SUBJECT_UNIQUE_CONSTRAINT = "azure_connections_subject_uk";
    public static final String SUBSCRIPTION_UNIQUE_CONSTRAINT =
            "azure_connection_subscriptions_connection_id_subscription_id_uk";

    // --- Federation ---
    // Subject of a connection's assertions, filled with the organization id and the connection id.
    // One subject per connection, so a deleted connection's federated credential never serves its successor.
    public static final String SUBJECT_FORMAT = "oba:organization:%s:connection:%s";
    // The audience Entra ID requires on an assertion it exchanges for a token.
    public static final String TOKEN_EXCHANGE_AUDIENCE = "api://AzureADTokenExchange";
}
