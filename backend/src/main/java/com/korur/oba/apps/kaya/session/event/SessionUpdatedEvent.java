package com.korur.oba.apps.kaya.session.event;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * Published inside the transaction that changed a session: an ingest batch, a link added or
 * removed by hand, or a delete. SessionLiveService handles it only after that transaction commits,
 * so a stream never announces a change a reader cannot see yet, and a rolled-back batch is never
 * announced at all.
 *
 * @param organizationId the organization of the session; only its streams receive the event
 * @param sessionId      the Claude Code session id
 * @param lastEventAt    the session's last event after the change; null when the session was deleted
 */
public record SessionUpdatedEvent(UUID organizationId, String sessionId, Instant lastEventAt) {
}
