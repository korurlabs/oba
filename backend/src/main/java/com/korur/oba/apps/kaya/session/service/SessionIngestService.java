package com.korur.oba.apps.kaya.session.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.ingest.dto.request.IngestBatchRequest;
import com.korur.oba.apps.kaya.session.entity.Session;

/**
 * The session write path of a recorder batch. Every method runs in the ingest transaction, so a
 * refused batch leaves no trace.
 */
public interface SessionIngestService {

    /**
     * Returns the session row of a Claude Code session, created when the organization has none, locked until the
     * transaction ends.
     *
     * @param organizationId the organization of the token
     * @param repositoryId   the repository of the batch, used only when the session is created
     * @param userId         the user of the token
     * @param machineId        the machine of the token, used only when the session is created
     * @param sessionId      the Claude Code session id
     * @param startedAt      the start of the session, used only when the session is created
     * @return the locked session
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException KAY_VAL_001 when
     *         the session belongs to another user
     */
    Session openSession(UUID organizationId, UUID repositoryId, UUID userId, UUID machineId, String sessionId,
            Instant startedAt);

    /**
     * Returns the session row of a Claude Code session, locked, when the organization has one. Used by a batch that
     * carries usage only, which creates no session.
     *
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException KAY_VAL_001 when
     *         the session belongs to another user
     */
    Optional<Session> findSessionForUpdate(UUID organizationId, UUID userId, String sessionId);

    /**
     * Applies a batch to its session: stores the new events (a known seq is skipped), the handoff
     * snapshots, the summary fields, the recorder's links, and the subagents and skill uses that
     * {@link com.korur.oba.apps.kaya.session.helper.SessionActivityDeriver} derives from every event of
     * the session.
     *
     * The account fields are copied only while the session holds none, so a later account or seat
     * change does not rewrite the session's history.
     *
     * @param session                     the session, locked by openSession or findSessionForUpdate
     * @param request                  the batch, already validated by the ingest service
     * @param ticketUrlTemplate        the repository's ticket URL template with {key}, or null
     * @param identityConflictUserId the user that holds an identity the batch reported, or null
     * @return the highest seq the session holds, 0 when it holds none
     */
    long applyBatch(Session session, IngestBatchRequest request, String ticketUrlTemplate,
            UUID identityConflictUserId);
}
