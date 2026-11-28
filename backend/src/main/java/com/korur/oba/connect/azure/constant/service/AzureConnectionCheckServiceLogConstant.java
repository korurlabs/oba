package com.korur.oba.connect.azure.constant.service;

/**
 * Log message constants for the checks of Azure connections.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AzureConnectionCheckServiceLogConstant {

    private AzureConnectionCheckServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String CHECK_STARTED = "Checking Azure connection: connectionId={}";
    public static final String CHECK_SUCCEEDED = "Azure connection check succeeded: connectionId={}, subscriptions={}";
    // Only Oba's error code and Entra's AADSTS code, never the Entra or Resource Manager message.
    public static final String CHECK_FAILED = "Azure connection check failed: connectionId={}, errorCode={}";
    public static final String CHECK_NO_SUBSCRIPTION =
            "Azure connection check found no readable subscription: connectionId={}";
}
