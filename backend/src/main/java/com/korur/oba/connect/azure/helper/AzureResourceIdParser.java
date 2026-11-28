package com.korur.oba.connect.azure.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.azure.constant.AzureResourceManagerConstant;
import com.korur.oba.connect.azure.dto.AzureResourceId;

/**
 * Splits an Azure resource id into its parts, so a caller can check which subscription and which
 * type of resource it names before any Azure call.
 *
 * Accepted are a top-level resource and a resource inside one other resource:
 * - /subscriptions/{uuid}/resourceGroups/{group}/providers/{namespace}/{type}/{name}
 * - /subscriptions/{uuid}/resourceGroups/{group}/providers/{namespace}/{type}/{parent}/{childType}/{name}
 *
 * The keywords subscriptions, resourceGroups and providers match ignoring case, as Azure treats
 * resource ids. Every other form, an empty segment, a trailing slash and a subscription that is not
 * a UUID give an empty result rather than an error, so the caller chooses its own error code.
 */
public final class AzureResourceIdParser {

    private static final int SUBSCRIPTIONS_INDEX = 1;
    private static final int SUBSCRIPTION_ID_INDEX = 2;
    private static final int RESOURCE_GROUPS_INDEX = 3;
    private static final int RESOURCE_GROUP_INDEX = 4;
    private static final int PROVIDERS_INDEX = 5;
    private static final int NAMESPACE_INDEX = 6;
    private static final int TYPE_INDEX = 7;
    private static final int NAME_INDEX = 8;
    private static final int CHILD_TYPE_INDEX = 9;
    private static final int CHILD_NAME_INDEX = 10;

    private AzureResourceIdParser() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * Parses a resource id.
     *
     * @param resourceId the resource id, possibly null or blank
     * @return the parts, or empty when the id has none of the accepted forms
     */
    public static Optional<AzureResourceId> parse(String resourceId) {
        if (resourceId == null) {
            return Optional.empty();
        }
        String[] segments = resourceId.trim().split(AzureResourceManagerConstant.RESOURCE_ID_SEPARATOR, -1);
        if (segments.length != AzureResourceManagerConstant.TOP_LEVEL_RESOURCE_SEGMENTS
                && segments.length != AzureResourceManagerConstant.CHILD_RESOURCE_SEGMENTS) {
            return Optional.empty();
        }
        if (!segments[0].isEmpty()) {
            return Optional.empty();
        }
        for (int index = 1; index < segments.length; index++) {
            if (segments[index].isBlank()) {
                return Optional.empty();
            }
        }
        if (!segments[SUBSCRIPTIONS_INDEX].equalsIgnoreCase(AzureResourceManagerConstant.SEGMENT_SUBSCRIPTIONS)
                || !segments[RESOURCE_GROUPS_INDEX].equalsIgnoreCase(AzureResourceManagerConstant.SEGMENT_RESOURCE_GROUPS)
                || !segments[PROVIDERS_INDEX].equalsIgnoreCase(AzureResourceManagerConstant.SEGMENT_PROVIDERS)) {
            return Optional.empty();
        }
        String subscriptionId = segments[SUBSCRIPTION_ID_INDEX];
        if (!AzureResourceManagerConstant.UUID_PATTERN.matcher(subscriptionId).matches()) {
            return Optional.empty();
        }
        UUID subscription = UUID.fromString(subscriptionId);
        if (segments.length == AzureResourceManagerConstant.TOP_LEVEL_RESOURCE_SEGMENTS) {
            return Optional.of(new AzureResourceId(subscription, segments[RESOURCE_GROUP_INDEX],
                    segments[NAMESPACE_INDEX], segments[TYPE_INDEX], segments[NAME_INDEX], null));
        }
        String childType = segments[TYPE_INDEX] + AzureResourceManagerConstant.RESOURCE_ID_SEPARATOR
                + segments[CHILD_TYPE_INDEX];
        return Optional.of(new AzureResourceId(subscription, segments[RESOURCE_GROUP_INDEX],
                segments[NAMESPACE_INDEX], childType, segments[CHILD_NAME_INDEX], segments[NAME_INDEX]));
    }
}
