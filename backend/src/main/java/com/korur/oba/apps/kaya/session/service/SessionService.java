package com.korur.oba.apps.kaya.session.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.session.dto.request.SessionLinkCreateRequest;
import com.korur.oba.apps.kaya.session.dto.response.SessionLinkResponse;

/**
 * Write operations on sessions: the links and deletes members make, and the retention cleanup. Every change a member makes is announced to the live stream of
 * the organization once its transaction commits.
 */
public interface SessionService {


    /**
     * Links a Kazan ticket or a pull request to a session by hand. Adding a link the session already
     * has returns that link unchanged.
     *
     * @param user           the member adding the link
     * @param organizationId the organization of the request
     * @param sessionId      the Claude Code session id
     * @param request        the kind and the reference: a ticket key, or a pull request URL
     * @return the link
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no session of that session
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException KAY_VAL_015 when
     *         the reference does not fit its kind
     */
    SessionLinkResponse addLink(User user, UUID organizationId, String sessionId, SessionLinkCreateRequest request);

    /**
     * Removes a link of a session, whether the recorder found it or a member added it.
     *
     * @param user           the member removing the link
     * @param organizationId the organization of the request
     * @param sessionId      the Claude Code session id
     * @param linkId         the link to remove
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         session or the link is not found
     */
    void removeLink(User user, UUID organizationId, String sessionId, UUID linkId);

    /**
     * Deletes a session with its events, subagents, skills, handoffs and links. Allowed to the
     * session's own user and to admins.
     *
     * @param user           the member deleting the session
     * @param organizationId the organization of the request
     * @param sessionId      the Claude Code session id
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no session of that session
     */
    void deleteSession(User user, UUID organizationId, String sessionId);

    /**
     * Deletes every session whose last event is older than its organization's retention.
     *
     * @param now the moment the retention is measured from
     * @return the number of sessions deleted
     */
    int deleteExpiredSessions(Instant now);
}
