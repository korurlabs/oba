package com.korur.oba.connect.realtime.util;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.realtime.constant.RealtimeConstant;

/**
 * Builds and reads the destination shapes the realtime transport and its consumers share, so a
 * consumer's publisher and its authorizer spell a destination the same way.
 *
 * An organization id in a destination is accepted only in its canonical form, the one
 * {@link java.util.UUID#toString()} writes. {@link java.util.UUID#fromString} also reads shortened
 * or upper-case forms, and two
 * spellings of one organization would be two broker topics that the organization check and the
 * publisher do not both recognize.
 */
public final class RealtimeDestinationUtil {

    private RealtimeDestinationUtil() {
    }

    /**
     * Builds the topic of a consumer in one organization.
     *
     * @param organizationId the organization
     * @param suffix         the consumer's suffix, starting with "/", such as "/chat"
     * @return "/topic/organizations/{organizationId}{suffix}"
     */
    public static String organizationTopic(UUID organizationId, String suffix) {
        return RealtimeConstant.ORGANIZATION_TOPIC_PREFIX + organizationId + suffix;
    }

    /**
     * Reads the organization of a destination that is exactly the consumer topic with the
     * given suffix.
     *
     * @param destination the destination a client named
     * @param suffix      the consumer's suffix, starting with "/"
     * @return the organization id, or empty when the destination is not that topic or its id is
     *         not canonical
     */
    public static Optional<UUID> parseOrganizationTopic(String destination, String suffix) {
        if (destination == null
                || !destination.startsWith(RealtimeConstant.ORGANIZATION_TOPIC_PREFIX)
                || !destination.endsWith(suffix)
                || destination.length() < RealtimeConstant.ORGANIZATION_TOPIC_PREFIX.length() + suffix.length()) {
            return Optional.empty();
        }
        return parseCanonical(destination.substring(
                RealtimeConstant.ORGANIZATION_TOPIC_PREFIX.length(), destination.length() - suffix.length()));
    }

    /**
     * Whether a destination is scoped to an organization, so that the session's organization
     * must be the one it names.
     *
     * @param destination the destination a client named
     * @return true for every destination under "/topic/organizations/"
     */
    public static boolean isOrganizationScoped(String destination) {
        return destination != null && destination.startsWith(RealtimeConstant.ORGANIZATION_TOPIC_PREFIX);
    }

    /**
     * Reads the organization an organization-scoped destination names, whatever consumer it
     * belongs to.
     *
     * @param destination a destination under "/topic/organizations/"
     * @return the organization id of the first segment, or empty when it is missing or not
     *         canonical
     */
    public static Optional<UUID> organizationOf(String destination) {
        if (!isOrganizationScoped(destination)) {
            return Optional.empty();
        }
        String rest = destination.substring(RealtimeConstant.ORGANIZATION_TOPIC_PREFIX.length());
        int end = rest.indexOf(RealtimeConstant.DESTINATION_SEPARATOR);
        return parseCanonical(end < 0 ? rest : rest.substring(0, end));
    }

    private static Optional<UUID> parseCanonical(String raw) {
        try {
            UUID parsed = UUID.fromString(raw);
            return parsed.toString().equals(raw) ? Optional.of(parsed) : Optional.empty();
        } catch (IllegalArgumentException malformed) {
            // rule-10-no-op -- reason: a malformed id is an expected client input; the empty
            // result makes the caller refuse the frame, and the refusal is logged with its reason.
            return Optional.empty();
        }
    }
}
