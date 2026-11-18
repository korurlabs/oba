package com.korur.oba.apps.kaya.session.service;

// 1. Framework-level
import org.springframework.data.domain.Page;

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.session.dto.request.SessionQueryRequest;
import com.korur.oba.apps.kaya.session.dto.response.SessionDetailResponse;
import com.korur.oba.apps.kaya.session.dto.response.SessionFiltersResponse;
import com.korur.oba.apps.kaya.session.dto.response.SessionHandoffResponse;
import com.korur.oba.apps.kaya.session.dto.response.SessionListResponse;
import com.korur.oba.apps.kaya.session.entity.Session;

/**
 * Reads the sessions of an organization for the wall and the session view. A session of another
 * organization answers the same 404 as an unknown session id.
 */
public interface SessionQueryService {

    /**
     * Lists the sessions of the organization, newest last event first.
     *
     * @param organizationId the organization of the request
     * @param request        paging (60 by default) and filters
     * @return one page of sessions with their names and links
     */
    Page<SessionListResponse> listSessions(UUID organizationId, SessionQueryRequest request);

    /**
     * The options of the wall's user, machine, model and subscription filters: the values the
     * organization's sessions hold.
     *
     * @param organizationId the organization of the request
     * @return the options, each list ordered for display
     */
    SessionFiltersResponse getFilters(UUID organizationId);

    /**
     * Reads one session with its subagents, skills used, handoff list and links.
     *
     * @param organizationId the organization of the request
     * @param sessionId      the Claude Code session id
     * @return the session
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no session of that session
     */
    SessionDetailResponse getSession(UUID organizationId, String sessionId);

    /**
     * The events of a session in seq order, each as the recorder wrote it.
     *
     * @param organizationId the organization of the request
     * @param sessionId      the Claude Code session id
     * @param afterSeq       only events with a higher seq; 0 for all
     * @return the events
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no session of that session
     */
    List<Map<String, Object>> getEvents(UUID organizationId, String sessionId, long afterSeq);

    /**
     * The handoff snapshots of a session with their content, by path.
     *
     * @param organizationId the organization of the request
     * @param sessionId      the Claude Code session id
     * @return the snapshots
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no session of that session
     */
    List<SessionHandoffResponse> getHandoffs(UUID organizationId, String sessionId);

    /**
     * Resolves a session within the organization.
     *
     * @param organizationId the organization the session must belong to
     * @param sessionId      the Claude Code session id
     * @return the session
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 otherwise
     */
    Session getEntityBySessionId(UUID organizationId, String sessionId);

    /**
     * Finds a session within the organization, for a caller that keeps a Claude Code session id as
     * plain text and for which an unknown session is an ordinary answer. Throwing out of this
     * transactional service would mark the caller's transaction for rollback.
     *
     * @param organizationId the organization the session must belong to
     * @param sessionId      the Claude Code session id
     * @return the session, or empty when the organization has none of that id
     */
    Optional<Session> findEntityBySessionId(UUID organizationId, String sessionId);

    /**
     * Finds sessions of the organization by their Claude Code session ids, for another domain that
     * keeps those ids as plain text and shows a whole page of rows at once.
     *
     * @param organizationId the organization the sessions must belong to
     * @param sessionIds     the Claude Code session ids, none of them null; may be empty
     * @return the session per Claude Code session id; an id of another organization or an unknown
     *         id has no entry
     */
    Map<String, Session> findEntitiesBySessionIds(UUID organizationId, Collection<String> sessionIds);

    /**
     * The handoffs of a feature across the sessions of a repository: the newest snapshot of each
     * path below handoffs/{feature}/, whichever session captured it, so the plan page shows the
     * progress of every implementation unit of the feature.
     *
     * @param organizationId the organization of the repository
     * @param repositoryId   the repository whose sessions are read
     * @param feature        the feature directory below handoffs/
     * @return the newest snapshot per path, ordered by path; empty when no session wrote one
     */
    List<SessionHandoffResponse> findFeatureHandoffs(UUID organizationId, UUID repositoryId, String feature);

    /**
     * Resolves sessions of the organization by their row ids, for another domain that keeps
     * plain session row ids and shows the sessions' Claude Code ids and titles.
     *
     * @param organizationId the organization the sessions must belong to
     * @param ids            the session row ids; may be empty
     * @return the session per row id; an id of another organization or an unknown id has no entry
     */
    Map<UUID, Session> findEntitiesByIds(UUID organizationId, Collection<UUID> ids);
}
