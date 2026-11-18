package com.korur.oba.apps.kaya.session.service.impl;

// 1. Framework-level
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.release.entity.Release;
import com.korur.oba.apps.kaya.release.service.ReleaseQueryService;
import com.korur.oba.apps.kaya.ingest.dto.request.IngestAccountRequest;
import com.korur.oba.apps.kaya.ingest.dto.request.IngestBatchRequest;
import com.korur.oba.apps.kaya.ingest.dto.request.IngestConfigReleaseRequest;
import com.korur.oba.apps.kaya.ingest.dto.request.IngestHandoffRequest;
import com.korur.oba.apps.kaya.ingest.dto.request.IngestSummaryRequest;
import com.korur.oba.apps.kaya.plan.service.PlanIngestService;
import com.korur.oba.apps.kaya.session.constant.SessionConstant;
import com.korur.oba.apps.kaya.session.constant.service.SessionServiceLogConstant;
import com.korur.oba.apps.kaya.session.dto.derived.DerivedSkillUse;
import com.korur.oba.apps.kaya.session.dto.derived.DerivedSubagent;
import com.korur.oba.apps.kaya.session.dto.derived.SessionActivity;
import com.korur.oba.apps.kaya.session.dto.projection.SessionEventAggregateProjection;
import com.korur.oba.apps.kaya.session.entity.Session;
import com.korur.oba.apps.kaya.session.entity.SessionEvent;
import com.korur.oba.apps.kaya.session.enums.SessionLinkKind;
import com.korur.oba.apps.kaya.session.enums.SessionReleaseState;
import com.korur.oba.apps.kaya.session.event.SessionUpdatedEvent;
import com.korur.oba.apps.kaya.session.helper.SessionActivityDeriver;
import com.korur.oba.apps.kaya.session.repository.SessionEventRepository;
import com.korur.oba.apps.kaya.session.repository.SessionHandoffSnapshotRepository;
import com.korur.oba.apps.kaya.session.repository.SessionLinkRepository;
import com.korur.oba.apps.kaya.session.repository.SessionRepository;
import com.korur.oba.apps.kaya.session.repository.SessionSkillUseRepository;
import com.korur.oba.apps.kaya.session.repository.SessionSubagentRepository;
import com.korur.oba.apps.kaya.session.service.SessionIngestService;

@Service
@RequiredArgsConstructor
public class SessionIngestServiceImpl implements SessionIngestService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(SessionIngestServiceImpl.class);
    private static final Pattern PULL_REQUEST_URL = Pattern.compile(SessionConstant.PULL_REQUEST_URL_PATTERN);
    private static final Pattern KAZAN_TICKET = Pattern.compile(SessionConstant.KAZAN_TICKET_PATTERN);

    private final SessionRepository sessionRepository;
    private final SessionEventRepository sessionEventRepository;
    private final SessionSubagentRepository sessionSubagentRepository;
    private final SessionSkillUseRepository sessionSkillUseRepository;
    private final SessionHandoffSnapshotRepository sessionHandoffSnapshotRepository;
    private final SessionLinkRepository sessionLinkRepository;
    private final SessionActivityDeriver sessionActivityDeriver;
    private final ReleaseQueryService releaseQueryService;
    private final PlanIngestService planIngestService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final LoggingService loggingService;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Session openSession(UUID organizationId, UUID repositoryId, UUID userId, UUID machineId, String sessionId,
            Instant startedAt) {
        int created = sessionRepository.insertIfAbsent(organizationId, repositoryId, userId, machineId, sessionId, startedAt);
        Session session = findSessionForUpdate(organizationId, userId, sessionId)
                .orElseThrow(() -> new IllegalStateException(SessionServiceLogConstant.SESSION_MISSING_AFTER_INSERT));
        if (created > 0) {
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, SessionServiceLogConstant.SESSION_CREATED,
                    sessionId, organizationId, userId);
        }
        return session;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Session> findSessionForUpdate(UUID organizationId, UUID userId, String sessionId) {
        Optional<Session> session = sessionRepository.findByOrganizationIdAndSessionIdForUpdate(organizationId, sessionId);
        if (session.isPresent() && !session.get().getUserId().equals(userId)) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, SessionServiceLogConstant.SESSION_OWNED_BY_OTHER_USER,
                    sessionId, organizationId, session.get().getUserId(), userId);
            throw new ConflictException(ExceptionDomainKayaError.VAL_SESSION_OWNED_BY_OTHER_USER,
                    Map.of(SessionConstant.FIELD_SESSION_ID, sessionId));
        }
        return session;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public long applyBatch(Session session, IngestBatchRequest request, String ticketUrlTemplate,
            UUID identityConflictUserId) {
        List<Map<String, Object>> events = request.events() == null ? List.of() : request.events();
        int stored = storeEvents(session, events);
        storeHandoffs(session, request.handoffs());

        SessionEventAggregateProjection aggregate = sessionEventRepository.aggregateBySessionRowId(session.getId());
        applySummary(session, request, aggregate);
        if (identityConflictUserId != null) {
            session.setIdentityConflictUserId(identityConflictUserId);
        }

        List<Map<String, Object>> allEvents = toDeriverEvents(sessionEventRepository.findBySession_IdOrderBySeqAsc(session.getId()));
        addRecorderLinks(session, allEvents, request.summary().ticket(), ticketUrlTemplate);

        SessionActivity activity = sessionActivityDeriver.derive(allEvents);
        storeSubagents(session, activity.subagents());
        storeSkillUses(session, activity.skillUses());
        sessionRepository.saveAndFlush(session);

        // Only the contiguous prefix is acknowledged: the sender drops its queue up to the
        // acknowledgement, so acknowledging past a gap would lose the events still missing in it.
        long acknowledgedSeq = sessionEventRepository.findContiguousSeq(session.getId());
        // Handled after the ingest transaction commits, so the wall reads the batch it is told about.
        applicationEventPublisher.publishEvent(
                new SessionUpdatedEvent(session.getOrganizationId(), session.getSessionId(), session.getLastEventAt()));
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, SessionServiceLogConstant.BATCH_APPLIED,
                session.getId(), stored, session.getEventCount(), acknowledgedSeq);
        return acknowledgedSeq;
    }

    /**
     * Stores the events of the batch. The recorder's keys other than seq, at, event and agent_id
     * are kept as the payload, so an event read back is the event the recorder wrote.
     */
    private int storeEvents(Session session, List<Map<String, Object>> events) {
        if (events.isEmpty()) {
            return 0;
        }
        List<Map<String, Object>> rows = new ArrayList<>(events.size());
        for (Map<String, Object> event : events) {
            Map<String, Object> payload = new LinkedHashMap<>(event);
            payload.remove(SessionConstant.EVENT_SEQ);
            payload.remove(SessionConstant.EVENT_AT);
            payload.remove(SessionConstant.EVENT_NAME);
            payload.remove(SessionConstant.EVENT_AGENT_ID);
            Map<String, Object> row = new HashMap<>();
            row.put(SessionConstant.EVENT_SEQ, ((Number) event.get(SessionConstant.EVENT_SEQ)).longValue());
            row.put(SessionConstant.EVENT_AT, Instant.parse((String) event.get(SessionConstant.EVENT_AT)).toString());
            row.put(SessionConstant.EVENT_NAME, event.get(SessionConstant.EVENT_NAME));
            row.put(SessionConstant.EVENT_AGENT_ID, event.get(SessionConstant.EVENT_AGENT_ID));
            row.put(SessionConstant.EVENT_PAYLOAD, payload);
            rows.add(row);
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(rows);
        } catch (JsonProcessingException e) {
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE, SessionServiceLogConstant.EVENTS_SERIALIZATION_FAILED, e,
                    session.getId());
            throw new IllegalStateException(SessionServiceLogConstant.EVENTS_SERIALIZATION_FAILED, e);
        }
        int stored = sessionEventRepository.insertAll(session.getId(), json);
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, SessionServiceLogConstant.EVENTS_STORED,
                stored, events.size(), session.getId());
        return stored;
    }

    /**
     * Stores the handoff snapshots. A snapshot that replaced the stored one may be a planning
     * handoff, which links the specs it names to its feature.
     */
    private void storeHandoffs(Session session, List<IngestHandoffRequest> handoffs) {
        if (handoffs == null) {
            return;
        }
        for (IngestHandoffRequest handoff : handoffs) {
            int stored = sessionHandoffSnapshotRepository.upsertIfNewer(session.getId(), handoff.path(), handoff.content(),
                    handoff.capturedAt());
            if (stored > 0) {
                planIngestService.linkFeature(session, handoff.path(), handoff.content());
            }
        }
    }

    private void applySummary(Session session, IngestBatchRequest request, SessionEventAggregateProjection aggregate) {
        IngestSummaryRequest summary = request.summary();
        UUID sessionRowId = session.getId();

        String sessionTitle = cut(sessionRowId, SessionConstant.FIELD_SESSION_TITLE, summary.sessionTitle(),
                SessionConstant.SESSION_TITLE_MAX_LENGTH);
        String firstPrompt = cut(sessionRowId, SessionConstant.FIELD_FIRST_PROMPT, summary.firstPrompt(),
                SessionConstant.FIRST_PROMPT_MAX_LENGTH);
        if (sessionTitle != null) {
            session.setSessionTitle(sessionTitle);
        }
        if (firstPrompt != null) {
            session.setFirstPrompt(firstPrompt);
        }
        String title = firstPresent(summary.title(), session.getSessionTitle(), session.getFirstPrompt());
        if (title != null) {
            session.setTitle(cut(sessionRowId, SessionConstant.FIELD_TITLE, title, SessionConstant.TITLE_MAX_LENGTH));
        }
        setIfPresent(cut(sessionRowId, SessionConstant.FIELD_BRANCH, summary.branch(), SessionConstant.BRANCH_MAX_LENGTH),
                session::setBranch);
        setIfPresent(cut(sessionRowId, SessionConstant.FIELD_CWD, summary.cwd(), SessionConstant.CWD_MAX_LENGTH), session::setCwd);
        setIfPresent(cut(sessionRowId, SessionConstant.FIELD_MODEL, summary.model(), SessionConstant.MODEL_MAX_LENGTH),
                session::setModel);
        setIfPresent(cut(sessionRowId, SessionConstant.FIELD_CLAUDE_CODE_VERSION, request.machine().claudeCodeVersion(),
                SessionConstant.CLAUDE_CODE_VERSION_MAX_LENGTH), session::setClaudeCodeVersion);
        if (session.getGitAuthorEmail() == null) {
            session.setGitAuthorEmail(cut(sessionRowId, SessionConstant.FIELD_GIT_AUTHOR_EMAIL, summary.gitAuthorEmail(),
                    SessionConstant.EMAIL_MAX_LENGTH));
        }
        copyAccount(session, request.machine().account());
        applyRelease(session, summary.configRelease());

        if (summary.ended() != null) {
            session.setEnded(summary.ended());
        }
        if (summary.features() != null) {
            Set<String> features = new LinkedHashSet<>(session.getFeatures());
            for (String feature : summary.features()) {
                String value = cut(sessionRowId, SessionConstant.FIELD_FEATURE, feature, SessionConstant.FEATURE_MAX_LENGTH);
                if (value != null) {
                    features.add(value);
                }
            }
            session.setFeatures(new ArrayList<>(features));
        }

        session.setEventCount(aggregate.getEventCount() == null ? 0 : aggregate.getEventCount().intValue());
        Instant startedAt = earliest(session.getStartedAt(), summary.startedAt(), aggregate.getFirstAt());
        session.setStartedAt(startedAt);
        session.setLastEventAt(latest(session.getLastEventAt(), summary.lastEventAt(), aggregate.getLastAt()));
    }

    /**
     * Copies the account onto the session while it holds none, so the session keeps the
     * subscription it ran on.
     */
    private void copyAccount(Session session, IngestAccountRequest account) {
        if (account == null || session.getAccountEmail() != null || blank(account.emailAddress())) {
            return;
        }
        UUID sessionRowId = session.getId();
        session.setAccountEmail(cut(sessionRowId, SessionConstant.FIELD_ACCOUNT, account.emailAddress().strip().toLowerCase(),
                SessionConstant.EMAIL_MAX_LENGTH));
        session.setAccountOrganizationUuid(cut(sessionRowId, SessionConstant.FIELD_ACCOUNT, account.organizationUuid(),
                SessionConstant.ACCOUNT_ORGANIZATION_UUID_MAX_LENGTH));
        session.setAccountOrganizationName(cut(sessionRowId, SessionConstant.FIELD_ACCOUNT, account.organizationName(),
                SessionConstant.ACCOUNT_ORGANIZATION_NAME_MAX_LENGTH));
        session.setBillingType(cut(sessionRowId, SessionConstant.FIELD_ACCOUNT, account.billingType(),
                SessionConstant.ACCOUNT_FIELD_MAX_LENGTH));
        session.setSeatTier(cut(sessionRowId, SessionConstant.FIELD_ACCOUNT, account.seatTier(),
                SessionConstant.ACCOUNT_FIELD_MAX_LENGTH));
    }

    /**
     * Records the release the session reported. The release is pinned for the whole
     * session by the CLI, so every report of a session names the same one and the latest report
     * is kept. A release counts only when the session's repository has a release with the reported
     * number and the same manifest hash; otherwise the session shows UNVERIFIED, so a tampered or
     * foreign manifest is never shown as a known release. A batch that reports nothing leaves the
     * recorded release as it is.
     */
    private void applyRelease(Session session, IngestConfigReleaseRequest reported) {
        if (reported == null) {
            return;
        }
        session.setDeliveredBy(reported.deliveredBy());
        if (reported.state() == SessionReleaseState.NONE) {
            session.setReleaseId(null);
            session.setReleaseState(SessionReleaseState.NONE);
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, SessionServiceLogConstant.RELEASE_NONE,
                    session.getId(), reported.deliveredBy());
            return;
        }
        Optional<Release> release = matchingRelease(session, reported);
        if (release.isEmpty() || reported.state() == null || reported.state() == SessionReleaseState.UNVERIFIED) {
            session.setReleaseId(null);
            session.setReleaseState(SessionReleaseState.UNVERIFIED);
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, SessionServiceLogConstant.RELEASE_UNVERIFIED,
                    session.getId(), reported.number(), reported.manifestSha256(), reported.state(), session.getRepositoryId());
            return;
        }
        session.setReleaseId(release.get().getId());
        session.setReleaseState(reported.state());
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, SessionServiceLogConstant.RELEASE_RECORDED,
                session.getId(), release.get().getNumber(), release.get().getId(), reported.state(), reported.deliveredBy());
    }

    private Optional<Release> matchingRelease(Session session, IngestConfigReleaseRequest reported) {
        if (reported.number() == null || reported.number() < 1 || blank(reported.manifestSha256())) {
            return Optional.empty();
        }
        String manifestSha256 = reported.manifestSha256().strip();
        return releaseQueryService.findByNumber(session.getOrganizationId(), session.getRepositoryId(), reported.number())
                .filter(release -> release.getManifestSha256().equalsIgnoreCase(manifestSha256));
    }

    /**
     * Adds the pull requests the events link to and the ticket the summary names. A link the
     * session already has, from the recorder or by hand, is kept as it is.
     */
    private void addRecorderLinks(Session session, List<Map<String, Object>> events, String ticket, String ticketUrlTemplate) {
        int added = 0;
        Set<String> pullRequests = new LinkedHashSet<>();
        for (Map<String, Object> event : events) {
            if (event.get(SessionConstant.EVENT_URL) instanceof String url
                    && url.length() <= SessionConstant.LINK_URL_MAX_LENGTH
                    && PULL_REQUEST_URL.matcher(url).matches()) {
                pullRequests.add(url);
            }
        }
        for (String url : pullRequests) {
            added += sessionLinkRepository.insertRecorderLinkIfAbsent(session.getId(), SessionLinkKind.PULL_REQUEST.name(), url, url);
        }
        if (ticket != null && KAZAN_TICKET.matcher(ticket).matches()) {
            String url = ticketUrlTemplate == null ? null
                    : ticketUrlTemplate.replace(SessionConstant.TICKET_URL_KEY_PLACEHOLDER, ticket);
            if (url != null && url.length() > SessionConstant.LINK_URL_MAX_LENGTH) {
                url = null;
            }
            added += sessionLinkRepository.insertRecorderLinkIfAbsent(session.getId(), SessionLinkKind.KAZAN_TICKET.name(),
                    ticket, url);
        }
        if (added > 0) {
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, SessionServiceLogConstant.LINKS_ADDED, added, session.getId());
        }
    }

    private void storeSubagents(Session session, List<DerivedSubagent> subagents) {
        UUID sessionRowId = session.getId();
        for (DerivedSubagent subagent : subagents) {
            if (subagent.agentId() == null || subagent.agentId().length() > SessionConstant.AGENT_ID_MAX_LENGTH
                    || subagent.subagentType() == null || subagent.startedAt() == null) {
                continue;
            }
            sessionSubagentRepository.upsert(sessionRowId, subagent.agentId(),
                    cut(sessionRowId, SessionConstant.FIELD_SUBAGENT, subagent.subagentType(), SessionConstant.SUBAGENT_TYPE_MAX_LENGTH),
                    cut(sessionRowId, SessionConstant.FIELD_MODEL, subagent.model(), SessionConstant.MODEL_MAX_LENGTH),
                    cut(sessionRowId, SessionConstant.FIELD_SUBAGENT, subagent.description(), SessionConstant.DESCRIPTION_MAX_LENGTH),
                    subagent.startedAt(), subagent.endedAt());
        }
    }

    private void storeSkillUses(Session session, List<DerivedSkillUse> skillUses) {
        UUID sessionRowId = session.getId();
        for (DerivedSkillUse use : skillUses) {
            if (use.skill() == null || use.firstUsedAt() == null) {
                continue;
            }
            sessionSkillUseRepository.upsert(sessionRowId,
                    cut(sessionRowId, SessionConstant.FIELD_SKILL, use.skill(), SessionConstant.SKILL_MAX_LENGTH),
                    use.source().name(), use.useCount(), use.firstUsedAt());
        }
    }

    /**
     * The stored events as the deriver reads them: the payload with seq, at, event and agent_id
     * put back.
     */
    private static List<Map<String, Object>> toDeriverEvents(List<SessionEvent> events) {
        List<Map<String, Object>> result = new ArrayList<>(events.size());
        for (SessionEvent event : events) {
            Map<String, Object> map = new HashMap<>(event.getPayload());
            map.put(SessionConstant.EVENT_SEQ, event.getSeq());
            map.put(SessionConstant.EVENT_AT, event.getAt().toString());
            map.put(SessionConstant.EVENT_NAME, event.getEvent());
            if (event.getAgentId() != null) {
                map.put(SessionConstant.EVENT_AGENT_ID, event.getAgentId());
            }
            result.add(map);
        }
        return result;
    }

    /**
     * A text value cut to its column; an empty value reads as absent.
     */
    private String cut(UUID sessionRowId, String field, String value, int maxLength) {
        if (blank(value)) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, SessionServiceLogConstant.FIELD_TRUNCATED, field, sessionRowId, maxLength);
        return value.substring(0, maxLength);
    }

    private static void setIfPresent(String value, Consumer<String> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    private static String firstPresent(String... values) {
        for (String value : values) {
            if (!blank(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static Instant earliest(Instant... values) {
        Instant result = null;
        for (Instant value : values) {
            if (value != null && (result == null || value.isBefore(result))) {
                result = value;
            }
        }
        return result;
    }

    private static Instant latest(Instant... values) {
        Instant result = null;
        for (Instant value : values) {
            if (value != null && (result == null || value.isAfter(result))) {
                result = value;
            }
        }
        return result;
    }
}
