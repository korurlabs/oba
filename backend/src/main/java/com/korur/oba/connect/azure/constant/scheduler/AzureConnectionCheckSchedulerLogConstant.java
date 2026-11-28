package com.korur.oba.connect.azure.constant.scheduler;

/**
 * Log message constants for the daily check of Azure connections.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AzureConnectionCheckSchedulerLogConstant {

    private AzureConnectionCheckSchedulerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String CHECK_RUN_STARTED = "Daily Azure connection check started: connections={}";
    public static final String CHECK_RUN_COMPLETED = "Daily Azure connection check completed: connections={}, failed={}";
    public static final String CHECK_CONNECTION_FAILED = "Daily Azure connection check failed: connectionId={}";
}
