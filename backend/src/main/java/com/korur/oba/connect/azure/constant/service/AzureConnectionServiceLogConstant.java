package com.korur.oba.connect.azure.constant.service;

/**
 * Log message constants for the writes to Azure connections.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AzureConnectionServiceLogConstant {

    private AzureConnectionServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String CONNECTION_CREATED =
            "Azure connection created: connectionId={}, organizationId={}, clientIdSet={}";
    public static final String CONNECTION_UPDATED =
            "Azure connection updated: connectionId={}, organizationId={}, identityChanged={}";
    public static final String CONNECTION_DELETED = "Azure connection deleted: connectionId={}, organizationId={}";
    public static final String CHECK_SUCCEEDED_RECORDED =
            "Azure connection check recorded as succeeded: connectionId={}, subscriptions={}";
    public static final String CHECK_FAILED_RECORDED =
            "Azure connection check recorded as failed: connectionId={}, errorCode={}, status={}";
    public static final String CHECK_RESULT_CONNECTION_GONE =
            "Azure connection check result dropped, the connection was deleted during the check: connectionId={}";
    public static final String SUBSCRIPTION_DUPLICATE_SKIPPED =
            "Azure subscription listed twice, kept once: connectionId={}, subscriptionId={}";
}
