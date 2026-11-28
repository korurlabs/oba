package com.korur.oba.connect.azure.constant.service;

/**
 * Log messages of the Azure Resource Manager query service.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AzureResourceManagerQueryServiceLogConstant {

    private AzureResourceManagerQueryServiceLogConstant() {
        throw new UnsupportedOperationException("Constant class should not be instantiated");
    }

    public static final String LISTING_SUBSCRIPTIONS = "Listing the Azure subscriptions of connection {}";
    public static final String CONNECTION_NOT_FOUND = "Azure connection {} not found for a Resource Manager read";
}
