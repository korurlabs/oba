package com.korur.oba.connect.azure.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Duration;
import java.util.regex.Pattern;

// 4. Internal

/**
 * Endpoint, token scope, paths, API versions, timeouts, resource id segments and error field names
 * of the Azure Resource Manager reads. Public Azure cloud only.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AzureResourceManagerConstant {

    private AzureResourceManagerConstant() {
        // Private constructor to prevent instantiation
    }

    // --- Endpoint and token ---
    public static final String BASE_URL = "https://management.azure.com";
    // A next page link is followed only to this scheme and host, so the bearer token of a connection
    // never leaves Azure Resource Manager whatever link an answer carries.
    public static final String BASE_SCHEME = "https";
    public static final String BASE_HOST = "management.azure.com";
    public static final String TOKEN_SCOPE = "https://management.azure.com/.default";

    // --- Paths ---
    public static final String SUBSCRIPTIONS_PATH = "/subscriptions";

    // --- Query parameters ---
    public static final String PARAM_API_VERSION = "api-version";

    // --- API versions ---
    // The GA version of Subscriptions - List, the only one Microsoft documents for this operation.
    public static final String SUBSCRIPTIONS_API_VERSION = "2022-12-01";

    // --- Paging ---
    // Bounds the pages read for one list, so a link that keeps pointing on cannot loop forever.
    public static final int MAX_PAGES = 50;

    // --- Resource ids ---
    public static final String RESOURCE_ID_SEPARATOR = "/";
    public static final String SEGMENT_SUBSCRIPTIONS = "subscriptions";
    public static final String SEGMENT_RESOURCE_GROUPS = "resourceGroups";
    public static final String SEGMENT_PROVIDERS = "providers";
    // Segments of a top-level resource id: an empty first one, then subscriptions, the id,
    // resourceGroups, the group, providers, the namespace, the type and the name.
    public static final int TOP_LEVEL_RESOURCE_SEGMENTS = 9;
    // A child resource adds its type and name, as a backup instance inside its backup vault.
    public static final int CHILD_RESOURCE_SEGMENTS = 11;
    public static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    // --- Token exchange errors ---
    // Entra ID names every refusal with an AADSTS code; only the code is kept, never the message,
    // which can carry the subject and other identifiers of the organization's tenant.
    public static final Pattern AADSTS_CODE_PATTERN = Pattern.compile("AADSTS\\d+");
    // Bounds the walk through the causes of a token exchange failure.
    public static final int MAX_CAUSE_DEPTH = 10;

    // --- Error fields ---
    public static final String FIELD_AADSTS_CODE = "aadstsCode";

    // --- Timeouts ---
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
}
