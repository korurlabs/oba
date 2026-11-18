package com.korur.oba.apps.kaya.session.service.impl;

// 1. Framework-level
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.dojo.constant.CommonConstant;
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.identity.service.KayaUserQueryService;
import com.korur.oba.apps.kaya.machine.service.MachineQueryService;
import com.korur.oba.apps.kaya.release.service.ReleaseQueryService;
import com.korur.oba.apps.kaya.session.constant.SessionConstant;
import com.korur.oba.apps.kaya.session.constant.service.SessionServiceLogConstant;
import com.korur.oba.apps.kaya.session.dto.projection.SessionMachineUserProjection;
import com.korur.oba.apps.kaya.session.dto.request.SessionQueryRequest;
import com.korur.oba.apps.kaya.session.dto.response.SessionDetailResponse;
import com.korur.oba.apps.kaya.session.dto.response.SessionFiltersResponse;
import com.korur.oba.apps.kaya.session.dto.response.SessionHandoffResponse;
import com.korur.oba.apps.kaya.session.dto.response.SessionListResponse;
import com.korur.oba.apps.kaya.session.dto.response.embedded.SessionFilterMachineResponse;
import com.korur.oba.apps.kaya.session.dto.response.embedded.SessionFilterUserResponse;
import com.korur.oba.apps.kaya.session.entity.Session;
import com.korur.oba.apps.kaya.session.entity.SessionHandoffSnapshot;
import com.korur.oba.apps.kaya.session.entity.SessionLink;
import com.korur.oba.apps.kaya.session.mapper.SessionMapper;
import com.korur.oba.apps.kaya.session.repository.SessionEventRepository;
import com.korur.oba.apps.kaya.session.repository.SessionHandoffSnapshotRepository;
import com.korur.oba.apps.kaya.session.repository.SessionLinkRepository;
import com.korur.oba.apps.kaya.session.repository.SessionRepository;
import com.korur.oba.apps.kaya.session.repository.SessionSkillUseRepository;
import com.korur.oba.apps.kaya.session.repository.SessionSubagentRepository;
import com.korur.oba.apps.kaya.session.service.SessionQueryService;
import com.korur.oba.apps.kaya.session.specification.SessionSpecification;
import com.korur.oba.apps.kaya.repository.dto.response.RepositoryResponse;
import com.korur.oba.apps.kaya.repository.service.RepositoryQueryService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionQueryServiceImpl implements SessionQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(SessionQueryServiceImpl.class);

    private final SessionRepository sessionRepository;
    private final SessionEventRepository sessionEventRepository;
    private final SessionLinkRepository sessionLinkRepository;
    private final SessionSubagentRepository sessionSubagentRepository;
    private final SessionSkillUseRepository sessionSkillUseRepository;
    private final SessionHandoffSnapshotRepository sessionHandoffSnapshotRepository;
    private final RepositoryQueryService repositoryQueryService;
    private final KayaUserQueryService kayaUserQueryService;
    private final MachineQueryService machineQueryService;
    private final ReleaseQueryService releaseQueryService;
    private final SessionMapper sessionMapper;
    private final LoggingService loggingService;

    @Override
    public Page<SessionListResponse> listSessions(UUID organizationId, SessionQueryRequest request) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, SessionServiceLogConstant.LISTING_SESSIONS, organizationId, request);

        // A fixed order, with the id as tie-breaker, keeps pages stable and keeps arbitrary sortBy
        // values from reaching the query.
        Pageable pageable = PageRequest.of(request.getPage() - 1, request.getAmount(),
                Sort.by(Sort.Direction.DESC, SessionConstant.FIELD_LAST_EVENT_AT).and(Sort.by(Sort.Direction.DESC, CommonConstant.ID)));
        Page<Session> sessions = sessionRepository.findAll(SessionSpecification.filterBy(organizationId, request), pageable);

        // The names and links of the whole page come in a few queries instead of a few per session.
        List<Session> content = sessions.getContent();
        Map<UUID, String> repositoryNames = repositoryNames(organizationId);
        Map<UUID, String> userNames = kayaUserQueryService.getDisplayNames(organizationId, userIdsOf(content));
        Map<UUID, String> machineNames = machineQueryService.getMachineNames(organizationId,
                content.stream().map(Session::getMachineId).collect(Collectors.toSet()));
        Map<UUID, List<SessionLink>> linksBySession = content.isEmpty()
                ? Map.of()
                : sessionLinkRepository.findBySession_IdInOrderByKindAscReferenceAsc(content.stream().map(Session::getId).toList())
                        .stream().collect(Collectors.groupingBy(link -> link.getSession().getId()));

        return sessions.map(session -> sessionMapper.toListResponse(session,
                repositoryNames.get(session.getRepositoryId()),
                userNames.get(session.getUserId()),
                machineNames.get(session.getMachineId()),
                session.getIdentityConflictUserId() == null ? null : userNames.get(session.getIdentityConflictUserId()),
                linksBySession.getOrDefault(session.getId(), List.of())));
    }

    @Override
    public SessionFiltersResponse getFilters(UUID organizationId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, SessionServiceLogConstant.READING_FILTERS, organizationId);

        List<UUID> userIds = sessionRepository.findDistinctUserIds(organizationId);
        Map<UUID, String> userNames = kayaUserQueryService.getDisplayNames(organizationId, userIds);
        List<SessionFilterUserResponse> users = userIds.stream()
                .map(id -> sessionMapper.toFilterUserResponse(id, userNames.get(id)))
                .sorted(Comparator.comparing(SessionFilterUserResponse::displayName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(SessionFilterUserResponse::id))
                .toList();

        // A machine belongs to one user, so each machine appears with one user; the first row is kept
        // should a session ever disagree.
        Map<UUID, UUID> userByMachine = sessionRepository.findDistinctMachineUsers(organizationId).stream()
                .collect(Collectors.toMap(SessionMachineUserProjection::getMachineId, SessionMachineUserProjection::getUserId,
                        (first, second) -> first));
        Map<UUID, String> machineNames = machineQueryService.getMachineNames(organizationId, userByMachine.keySet());
        List<SessionFilterMachineResponse> machines = userByMachine.entrySet().stream()
                .map(entry -> sessionMapper.toFilterMachineResponse(entry.getKey(), machineNames.get(entry.getKey()), entry.getValue()))
                .sorted(Comparator.comparing(SessionFilterMachineResponse::machineName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(SessionFilterMachineResponse::id))
                .toList();

        return sessionMapper.toFiltersResponse(users, machines, sessionRepository.findDistinctModels(organizationId),
                sessionMapper.toFilterSubscriptionResponses(sessionRepository.findSubscriptions(organizationId)));
    }

    @Override
    public SessionDetailResponse getSession(UUID organizationId, String sessionId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, SessionServiceLogConstant.READING_SESSION, sessionId, organizationId);
        Session session = getEntityBySessionId(organizationId, sessionId);

        Set<UUID> userIds = userIdsOf(List.of(session));
        Map<UUID, String> userNames = kayaUserQueryService.getDisplayNames(organizationId, userIds);
        String machineName = machineQueryService.getMachineNames(organizationId, List.of(session.getMachineId())).get(session.getMachineId());
        UUID sessionRowId = session.getId();
        return sessionMapper.toDetailResponse(session,
                repositoryNames(organizationId).get(session.getRepositoryId()),
                userNames.get(session.getUserId()),
                machineName,
                session.getIdentityConflictUserId() == null ? null : userNames.get(session.getIdentityConflictUserId()),
                sessionLinkRepository.findBySession_IdOrderByKindAscReferenceAsc(sessionRowId),
                sessionSubagentRepository.findBySession_IdOrderByStartedAtAscAgentIdAsc(sessionRowId),
                sessionSkillUseRepository.findBySession_IdOrderByFirstUsedAtAscSkillAsc(sessionRowId),
                sessionHandoffSnapshotRepository.findBySession_IdOrderByPathAsc(sessionRowId),
                session.getReleaseId() == null ? null
                        : releaseQueryService.getEntityById(organizationId, session.getReleaseId()).getNumber());
    }

    @Override
    public List<Map<String, Object>> getEvents(UUID organizationId, String sessionId, long afterSeq) {
        Session session = getEntityBySessionId(organizationId, sessionId);
        return sessionMapper.toEventResponses(sessionEventRepository.findBySession_IdAndSeqGreaterThanOrderBySeqAsc(session.getId(),
                Math.max(afterSeq, 0L)));
    }

    @Override
    public List<SessionHandoffResponse> getHandoffs(UUID organizationId, String sessionId) {
        Session session = getEntityBySessionId(organizationId, sessionId);
        return sessionMapper.toHandoffResponses(sessionHandoffSnapshotRepository.findBySession_IdOrderByPathAsc(session.getId()));
    }

    @Override
    public Session getEntityBySessionId(UUID organizationId, String sessionId) {
        // Looked up within the organization, so a session of another organization answers the same
        // 404 as an unknown session id.
        return sessionRepository.findByOrganizationIdAndSessionId(organizationId, sessionId).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, SessionServiceLogConstant.SESSION_NOT_FOUND, sessionId, organizationId);
            return new NotFoundException(ExceptionDomainKayaError.NOT_RESOURCE_FOUND,
                    Map.of(SessionConstant.FIELD_SESSION_ID, sessionId));
        });
    }

    @Override
    public Optional<Session> findEntityBySessionId(UUID organizationId, String sessionId) {
        return sessionRepository.findByOrganizationIdAndSessionId(organizationId, sessionId);
    }

    @Override
    public Map<String, Session> findEntitiesBySessionIds(UUID organizationId, Collection<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Map.of();
        }
        return sessionRepository.findByOrganizationIdAndSessionIdIn(organizationId, sessionIds).stream()
                .collect(Collectors.toMap(Session::getSessionId, Function.identity()));
    }

    @Override
    public List<SessionHandoffResponse> findFeatureHandoffs(UUID organizationId, UUID repositoryId, String feature) {
        if (feature == null || feature.isBlank()) {
            return List.of();
        }
        List<SessionHandoffSnapshot> snapshots = sessionHandoffSnapshotRepository.findAll(
                SessionSpecification.featureHandoffSnapshots(organizationId, repositoryId, feature));
        // Several sessions may have written the same handoff; the newest capture is its current truth.
        Map<String, SessionHandoffSnapshot> newestByPath = new LinkedHashMap<>();
        for (SessionHandoffSnapshot snapshot : snapshots) {
            newestByPath.merge(snapshot.getPath(), snapshot,
                    (kept, other) -> other.getCapturedAt().isAfter(kept.getCapturedAt()) ? other : kept);
        }
        return sessionMapper.toHandoffResponses(newestByPath.values().stream()
                .sorted(Comparator.comparing(SessionHandoffSnapshot::getPath))
                .toList());
    }

    @Override
    public Map<UUID, Session> findEntitiesByIds(UUID organizationId, Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        // Filtered by organization after the read, so an id of another organization stays unknown.
        return sessionRepository.findAllById(Set.copyOf(ids)).stream()
                .filter(session -> session.getOrganizationId().equals(organizationId))
                .collect(Collectors.toMap(Session::getId, Function.identity()));
    }

    /**
     * The organization's repositories by id; an organization has a handful, so one list read is
     * cheaper than resolving each session's repository.
     */
    private Map<UUID, String> repositoryNames(UUID organizationId) {
        return repositoryQueryService.getRepositories(organizationId).stream()
                .collect(Collectors.toMap(RepositoryResponse::id, RepositoryResponse::name, (first, second) -> first));
    }

    /**
     * The users the sessions name: their own and the holders of a conflicting identity.
     */
    private static Set<UUID> userIdsOf(List<Session> sessions) {
        Set<UUID> ids = new HashSet<>();
        for (Session session : sessions) {
            ids.add(session.getUserId());
            if (session.getIdentityConflictUserId() != null) {
                ids.add(session.getIdentityConflictUserId());
            }
        }
        return ids;
    }
}
