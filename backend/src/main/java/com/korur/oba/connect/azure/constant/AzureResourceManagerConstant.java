package com.korur.oba.connect.azure.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Duration;

// 4. Internal

/**
 * Endpoint, token scope, paths, API versions and timeouts of the Azure Resource Manager reads.
 * Public Azure cloud only.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AzureResourceManagerConstant {

    private AzureResourceManagerConstant() {
        // Private constructor to prevent instantiation
    }

    // --- Endpoint and token ---
    public static final String BASE_URL = "https://management.azure.com";
    public static final String TOKEN_SCOPE = "https://management.azure.com/.default";

    // --- Paths ---
    public static final String SUBSCRIPTIONS_PATH = "/subscriptions";

    // --- Query parameters ---
    public static final String PARAM_API_VERSION = "api-version";

    // --- API versions ---
    public static final String SUBSCRIPTIONS_API_VERSION = "2022-12-01";

    // --- Timeouts ---
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
}
