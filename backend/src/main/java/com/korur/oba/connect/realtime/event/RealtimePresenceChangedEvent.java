package com.korur.oba.connect.realtime.event;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * Published by {@link com.korur.oba.connect.realtime.service.RealtimeSessionRegistry} when a
 * member comes online with their first session in an organization, or goes offline with their
 * last one. A second tab publishes nothing.
 *
 * The transport only tracks presence; each consumer that shows it turns the event into its own
 * signal on its own topic. Published synchronously on the thread of the transport callback, in
 * the order the transitions happen, so a listener must be quick and must not block.
 *
 * @param organizationId the organization
 * @param userId         the member whose presence changed
 * @param online         true on the first session, false when the last one closed
 */
public record RealtimePresenceChangedEvent(UUID organizationId, UUID userId, boolean online) {
}
