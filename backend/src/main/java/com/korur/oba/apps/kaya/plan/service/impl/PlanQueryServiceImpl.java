package com.korur.oba.apps.kaya.plan.service.impl;

// 1. Framework-level
import org.springframework.data.domain.Limit;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kaya.identity.helper.UserDisplayNameHelper;
import com.korur.oba.dojo.constant.CommonConstant;
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.business.BadRequestException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.delivery.DeliveryProposalProperties;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.apps.kaya.identity.service.KayaUserQueryService;
import com.korur.oba.apps.kaya.identity.service.OrganizationAccessQueryService;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;
import com.korur.oba.apps.kaya.plan.constant.PlanConstant;
import com.korur.oba.apps.kaya.plan.constant.service.PlanServiceLogConstant;
import com.korur.oba.apps.kaya.plan.dto.request.PlanFeedbackDeliveryQueryRequest;
import com.korur.oba.apps.kaya.plan.dto.request.PlanQueryRequest;
import com.korur.oba.apps.kaya.plan.dto.response.PlanDetailResponse;
import com.korur.oba.apps.kaya.plan.dto.response.PlanFeedbackDeliveryResponse;
import com.korur.oba.apps.kaya.plan.dto.response.PlanFeedbackResponse;
import com.korur.oba.apps.kaya.plan.dto.response.PlanFiltersResponse;
import com.korur.oba.apps.kaya.plan.dto.response.PlanListResponse;
import com.korur.oba.apps.kaya.plan.dto.response.PlanVersionContentResponse;
import com.korur.oba.apps.kaya.plan.dto.response.PlanVersionDiffResponse;
import com.korur.oba.apps.kaya.plan.dto.response.embedded.PlanHandoffResponse;
import com.korur.oba.apps.kaya.plan.dto.response.embedded.PlanReferenceResponse;
import com.korur.oba.apps.kaya.plan.dto.response.embedded.PlanSessionResponse;
import com.korur.oba.apps.kaya.plan.dto.response.embedded.PlanVersionSummaryResponse;
import com.korur.oba.apps.kaya.plan.entity.Plan;
import com.korur.oba.apps.kaya.plan.entity.PlanFeedback;
import com.korur.oba.apps.kaya.plan.entity.PlanVersion;
import com.korur.oba.apps.kaya.plan.enums.PlanKind;
import com.korur.oba.apps.kaya.plan.mapper.PlanMapper;
import com.korur.oba.apps.kaya.plan.repository.PlanFeedbackRepository;
import com.korur.oba.apps.kaya.plan.repository.PlanRepository;
import com.korur.oba.apps.kaya.plan.repository.PlanVersionRepository;
import com.korur.oba.apps.kaya.plan.service.PlanQueryService;
import com.korur.oba.apps.kaya.plan.specification.PlanSpecification;
import com.korur.oba.apps.kaya.pack.entity.Pack;
import com.korur.oba.apps.kaya.pack.service.PackQueryService;
import com.korur.oba.apps.kaya.repository.dto.response.RepositoryResponse;
import com.korur.oba.apps.kaya.repository.helper.RemoteUrlHelper;
import com.korur.oba.apps.kaya.repository.service.RepositoryQueryService;
import com.korur.oba.apps.kaya.session.dto.response.SessionHandoffResponse;
import com.korur.oba.apps.kaya.session.entity.Session;
import com.korur.oba.apps.kaya.session.service.SessionQueryService;
import com.korur.oba.apps.kaya.version.helper.UnifiedDiff;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanQueryServiceImpl implements PlanQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(PlanQueryServiceImpl.class);
    private static final Pattern UNIT_NUMBER = Pattern.compile(PlanConstant.UNIT_NUMBER_PATTERN);

    private final PlanRepository planRepository;
    private final PlanVersionRepository planVersionRepository;
    private final PlanFeedbackRepository planFeedbackRepository;
    private final SessionQueryService sessionQueryService;
    private final KayaUserQueryService kayaUserQueryService;
    private final RepositoryQueryService repositoryQueryService;
    private final PackQueryService packQueryService;
    private final OrganizationAccessQueryService organizationAccessQueryService;
    private final DeliveryProposalProperties deliveryProposalProperties;
    private final PlanMapper planMapper;
    private final LoggingService loggingService;

    @Override
    public Page<PlanListResponse> listPlans(UUID organizationId, PlanQueryRequest request) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.LISTING_PLANS, organizationId, request);
        UUID sessionRowId = request.getSessionId() == null ? null
                : sessionQueryService.getEntityBySessionId(organizationId, request.getSessionId()).getId();

        // A fixed order, with the id as tie-breaker, keeps pages stable and keeps arbitrary sortBy
        // values from reaching the query.
        Pageable pageable = PageRequest.of(request.getPage() - 1, request.getAmount(),
                Sort.by(Sort.Direction.DESC, PlanConstant.FIELD_LAST_CHANGED_AT).and(Sort.by(Sort.Direction.DESC, CommonConstant.ID)));
        Page<Plan> plans = planRepository.findAll(PlanSpecification.filterBy(organizationId, sessionRowId, request), pageable);

        // The names of the whole page come in a few queries instead of a few per plan.
        List<Plan> content = plans.getContent();
        Map<UUID, String> repositoryNames = repositoryNames(organizationId);
        Map<UUID, String> packNames = packNames(organizationId,
                content.stream().map(Plan::getPackId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<UUID, String> userNames = kayaUserQueryService.getDisplayNames(organizationId,
                content.stream().map(Plan::getUserId).collect(Collectors.toSet()));

        return plans.map(plan -> planMapper.toListResponse(plan,
                reference(plan.getRepositoryId(), repositoryNames),
                reference(plan.getPackId(), packNames),
                reference(plan.getUserId(), userNames)));
    }

    @Override
    public PlanFiltersResponse getFilters(UUID organizationId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.READING_FILTERS, organizationId);
        List<UUID> packIds = planRepository.findDistinctPackIds(organizationId);
        List<UUID> userIds = planRepository.findDistinctUserIds(organizationId);
        return planMapper.toFiltersResponse(
                references(planRepository.findDistinctRepositoryIds(organizationId), repositoryNames(organizationId)),
                references(packIds, packNames(organizationId, packIds)),
                references(userIds, kayaUserQueryService.getDisplayNames(organizationId, userIds)));
    }

    @Override
    public PlanDetailResponse getPlan(User user, UUID organizationId, UUID planId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.READING_PLAN, planId, organizationId, user.getId());
        Plan plan = getEntity(organizationId, planId);
        List<PlanVersion> versions = planVersionRepository.findByPlanIdOrderByNumberAsc(plan.getId());
        List<PlanFeedback> feedback = planFeedbackRepository.findByPlanIdOrderByCreatedAtAscIdAsc(plan.getId());

        Set<UUID> sessionRowIds = new HashSet<>();
        versions.stream().map(PlanVersion::getSessionId).filter(Objects::nonNull).forEach(sessionRowIds::add);
        if (plan.getLatestSessionId() != null) {
            sessionRowIds.add(plan.getLatestSessionId());
        }
        Map<UUID, Session> sessions = sessionQueryService.findEntitiesByIds(organizationId, sessionRowIds);
        Session latestSession = plan.getLatestSessionId() == null ? null : sessions.get(plan.getLatestSessionId());

        List<PlanHandoffResponse> handoffs = featureHandoffs(organizationId, plan);
        Map<UUID, String> userNames = kayaUserQueryService.getDisplayNames(organizationId, List.of(plan.getUserId()));
        return planMapper.toDetailResponse(plan,
                planMapper.toReferenceResponse(plan.getRepositoryId(),
                        repositoryQueryService.getEntityById(organizationId, plan.getRepositoryId()).getName()),
                plan.getPackId() == null ? null : planMapper.toReferenceResponse(plan.getPackId(),
                        packQueryService.getEntityById(organizationId, plan.getPackId()).getName()),
                reference(plan.getUserId(), userNames),
                latestSession == null ? null : planMapper.toSessionResponse(latestSession),
                versions.stream().map(version -> planMapper.toVersionSummaryResponse(version,
                        version.getSessionId() == null || !sessions.containsKey(version.getSessionId()) ? null
                                : sessions.get(version.getSessionId()).getSessionId())).toList(),
                feedbackResponses(versions, feedback),
                handoffs,
                planningHandoffPath(plan, handoffs),
                canApprove(user, organizationId, plan));
    }

    @Override
    public PlanVersionContentResponse getVersion(UUID organizationId, UUID planId, int number) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.READING_VERSION, number, planId, organizationId);
        Plan plan = getEntity(organizationId, planId);
        return planMapper.toVersionContentResponse(getVersionEntity(plan, number));
    }

    @Override
    public PlanVersionDiffResponse getDiff(UUID organizationId, UUID planId, int number) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.READING_DIFF, number, planId, organizationId);
        Plan plan = getEntity(organizationId, planId);
        PlanVersion version = getVersionEntity(plan, number);
        if (version.getNumber() == 1) {
            return planMapper.toVersionDiffResponse(version.getNumber(), "");
        }
        PlanVersion previous = getVersionEntity(plan, version.getNumber() - 1);
        String path = plan.getKind() == PlanKind.SPEC ? plan.getPath() : PlanConstant.SESSION_PLAN_DIFF_PATH;
        return planMapper.toVersionDiffResponse(version.getNumber(),
                UnifiedDiff.diff(path, previous.getContent(), version.getContent()));
    }

    @Override
    public Plan getEntity(UUID organizationId, UUID planId) {
        // Looked up within the organization, so a plan of another organization answers the same 404
        // as an unknown id.
        return planRepository.findByIdAndOrganizationId(planId, organizationId).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.PLAN_NOT_FOUND, planId, organizationId);
            return new NotFoundException(ExceptionDomainKayaError.NOT_PLAN_FOUND,
                    Map.of(PlanConstant.FIELD_PLAN_ID, planId.toString()));
        });
    }

    @Override
    public boolean existsForPack(UUID organizationId, UUID packId) {
        return planRepository.existsByOrganizationIdAndPackId(organizationId, packId);
    }

    @Override
    public List<PlanFeedbackDeliveryResponse> getPendingFeedback(MachinePrincipal principal,
            PlanFeedbackDeliveryQueryRequest request) {
        boolean sessionsGiven = request.getSessionId() != null && !request.getSessionId().isEmpty();
        boolean remoteGiven = request.getRemoteUrl() != null && !request.getRemoteUrl().isBlank();
        boolean branchGiven = request.getBranch() != null && !request.getBranch().isBlank();
        if (sessionsGiven && !remoteGiven && !branchGiven) {
            return pendingForSessions(principal, request.getSessionId());
        }
        if (!sessionsGiven && remoteGiven && branchGiven) {
            return pendingForBranch(principal, request.getRemoteUrl(), request.getBranch());
        }
        loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.DELIVERY_QUERY_INVALID, principal.machineId(),
                sessionsGiven ? request.getSessionId().size() : 0, remoteGiven, branchGiven);
        throw new BadRequestException(ExceptionDomainKayaError.PLN_DELIVERY_QUERY_INVALID,
                Map.of(PlanConstant.FIELD_QUERY, PlanConstant.DELIVERY_QUERY_INVALID_MESSAGE));
    }

    @Override
    public List<PlanFeedbackDeliveryResponse> pendingForSessions(MachinePrincipal principal, List<String> sessionIds) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.READING_PENDING_FOR_SESSIONS,
                sessionIds.size(), principal.userId(), principal.organizationId());
        Set<String> wanted = new HashSet<>(sessionIds);
        List<Plan> plans = planRepository.findWithPendingFeedback(principal.organizationId(), principal.userId());
        Map<UUID, Session> sessions = latestSessionsOf(principal.organizationId(), plans);
        List<Plan> matching = plans.stream()
                .filter(plan -> {
                    Session session = sessions.get(plan.getLatestSessionId());
                    return session != null && session.getUserId().equals(principal.userId())
                            && wanted.contains(session.getSessionId());
                })
                .toList();
        return pendingOf(principal, matching, sessions);
    }

    @Override
    public List<PlanFeedbackDeliveryResponse> pendingForBranch(MachinePrincipal principal, String remoteUrl, String branch) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.READING_PENDING_FOR_BRANCH,
                principal.userId(), principal.organizationId());
        // Resolved without the throwing lookup of delivery: a remote the organization does not know,
        // or one Kaya cannot read, has no plans, so the answer is the empty list rather than an error.
        String normalizedRemoteUrl = RemoteUrlHelper.normalize(remoteUrl);
        Optional<RepositoryResponse> repository = normalizedRemoteUrl == null ? Optional.empty()
                : repositoryQueryService.getRepositories(principal.organizationId()).stream()
                        .filter(candidate -> candidate.remoteUrl().equals(normalizedRemoteUrl))
                        .findFirst();
        if (repository.isEmpty()) {
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.PENDING_REPOSITORY_UNKNOWN,
                    principal.organizationId());
            return List.of();
        }
        UUID repositoryId = repository.get().id();
        List<Plan> plans = planRepository.findWithPendingFeedback(principal.organizationId(), principal.userId()).stream()
                .filter(plan -> plan.getRepositoryId().equals(repositoryId))
                .toList();
        Map<UUID, Session> sessions = latestSessionsOf(principal.organizationId(), plans);
        // Only an ended latest session hands its feedback to the next session of the branch; a live
        // one receives it itself through its own hooks.
        List<Plan> matching = plans.stream()
                .filter(plan -> {
                    Session session = sessions.get(plan.getLatestSessionId());
                    return session != null && session.isEnded() && session.getUserId().equals(principal.userId())
                            && branch.equals(session.getBranch());
                })
                .toList();
        return pendingOf(principal, matching, sessions);
    }

    private PlanVersion getVersionEntity(Plan plan, int number) {
        return planVersionRepository.findByPlanIdAndNumber(plan.getId(), number).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.VERSION_NOT_FOUND, number, plan.getId());
            return new NotFoundException(ExceptionDomainKayaError.NOT_PLAN_FOUND,
                    Map.of(PlanConstant.FIELD_PLAN_ID, plan.getId().toString(), PlanConstant.FIELD_VERSION_NUMBER, number));
        });
    }

    /**
     * The approval rule of a plan without the refusal: the user linked to the plan's user, or an
     * ADMIN or OWNER of the organization. It is the rule PlanService applies to an APPROVE, so the
     * page offers Approve exactly when the API accepts it.
     */
    private boolean canApprove(User user, UUID organizationId, Plan plan) {
        if (user.getId().equals(plan.getUserId())) {
            return true;
        }
        return organizationAccessQueryService.verifyTenantAccess(user, organizationId).getRole().isAtLeast(OrganizationMemberRole.ADMIN);
    }

    /** The feedback of a plan with its version numbers and reviewer names. */
    private List<PlanFeedbackResponse> feedbackResponses(List<PlanVersion> versions, List<PlanFeedback> feedback) {
        Map<UUID, Integer> numberByVersion = versions.stream()
                .collect(Collectors.toMap(PlanVersion::getId, PlanVersion::getNumber));
        // A plan has a handful of reviewers, so each is read once.
        Map<UUID, String> reviewerNames = new HashMap<>();
        for (PlanFeedback item : feedback) {
            reviewerNames.computeIfAbsent(item.getReviewerId(), id -> UserDisplayNameHelper.displayNameOf(kayaUserQueryService.getEntityById(id)));
        }
        return feedback.stream()
                .map(item -> planMapper.toFeedbackResponse(item, numberByVersion.get(item.getVersionId()),
                        reference(item.getReviewerId(), reviewerNames)))
                .toList();
    }

    /**
     * The newest snapshot of each handoff of the plan's feature, with the unit of the spec each one
     * implements. A plan without a feature has none.
     */
    private List<PlanHandoffResponse> featureHandoffs(UUID organizationId, Plan plan) {
        if (plan.getFeature() == null) {
            return List.of();
        }
        return sessionQueryService.findFeatureHandoffs(organizationId, plan.getRepositoryId(), plan.getFeature()).stream()
                .map(handoff -> planMapper.toHandoffResponse(handoff, unitNumberOf(handoff)))
                .toList();
    }

    /**
     * The planning handoff of the feature, when it still names the spec's path: the feature was
     * linked from it, but a newer capture may have dropped the spec.
     */
    private static String planningHandoffPath(Plan plan, List<PlanHandoffResponse> handoffs) {
        if (plan.getKind() != PlanKind.SPEC || plan.getFeature() == null) {
            return null;
        }
        String planningPath = plan.getFeature() + PlanConstant.HANDOFF_PATH_SEPARATOR + PlanConstant.PLANNING_HANDOFF_FILE;
        return handoffs.stream()
                .filter(handoff -> handoff.path().equals(planningPath) && handoff.content().contains(plan.getPath()))
                .map(PlanHandoffResponse::path)
                .findFirst()
                .orElse(null);
    }

    /**
     * The first "unit N" of a handoff's "Scope given" section, which names the implementation unit
     * of the spec the handoff belongs to.
     */
    private static Integer unitNumberOf(SessionHandoffResponse handoff) {
        boolean inScope = false;
        for (String line : handoff.content().replace("\r\n", "\n").split("\n", -1)) {
            if (line.startsWith(PlanConstant.SECTION_PREFIX)) {
                if (inScope) {
                    return null;
                }
                inScope = line.strip().equals(PlanConstant.SCOPE_GIVEN_HEADING);
                continue;
            }
            if (inScope) {
                Matcher unit = UNIT_NUMBER.matcher(line);
                if (unit.find()) {
                    return Integer.valueOf(unit.group(1));
                }
            }
        }
        return null;
    }

    private Map<UUID, Session> latestSessionsOf(UUID organizationId, List<Plan> plans) {
        return sessionQueryService.findEntitiesByIds(organizationId,
                plans.stream().map(Plan::getLatestSessionId).collect(Collectors.toSet()));
    }

    /**
     * The undelivered feedback of the plans, oldest first and at most 50, with what the CLI needs to
     * phrase it: the plan's kind, path and page, the version numbers and the reviewer's name.
     */
    private List<PlanFeedbackDeliveryResponse> pendingOf(MachinePrincipal principal, List<Plan> plans,
            Map<UUID, Session> sessions) {
        if (plans.isEmpty()) {
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.PENDING_RESOLVED, 0,
                    principal.userId(), principal.organizationId());
            return List.of();
        }
        Map<UUID, Plan> plansById = plans.stream().collect(Collectors.toMap(Plan::getId, Function.identity()));
        List<PlanFeedback> feedback = planFeedbackRepository.findByPlanIdInAndDeliveredAtIsNullOrderByCreatedAtAscIdAsc(
                plansById.keySet(), Limit.of(PlanConstant.DELIVERY_ITEMS_MAX));
        Map<UUID, Integer> numberByVersion = planVersionRepository.findAllById(
                        feedback.stream().map(PlanFeedback::getVersionId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(PlanVersion::getId, PlanVersion::getNumber));
        Map<UUID, String> reviewerNames = new HashMap<>();
        for (PlanFeedback item : feedback) {
            reviewerNames.computeIfAbsent(item.getReviewerId(), id -> UserDisplayNameHelper.displayNameOf(kayaUserQueryService.getEntityById(id)));
        }
        String origin = webOrigin();
        List<PlanFeedbackDeliveryResponse> responses = feedback.stream()
                .map(item -> {
                    Plan plan = plansById.get(item.getPlanId());
                    return planMapper.toDeliveryResponse(item, plan, origin + PlanConstant.PLAN_PAGE_PATH + plan.getId(),
                            numberByVersion.get(item.getVersionId()), sessions.get(plan.getLatestSessionId()).getSessionId(),
                            reviewerNames.get(item.getReviewerId()));
                })
                .toList();
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.PENDING_RESOLVED, responses.size(),
                principal.userId(), principal.organizationId());
        return responses;
    }

    /** The web app's origin without a trailing slash, so the plan page path joins it once. */
    private String webOrigin() {
        String webUrl = deliveryProposalProperties.webUrl();
        return webUrl.endsWith(PlanConstant.URL_PATH_SEPARATOR)
                ? webUrl.substring(0, webUrl.length() - PlanConstant.URL_PATH_SEPARATOR.length())
                : webUrl;
    }

    private Map<UUID, String> repositoryNames(UUID organizationId) {
        return repositoryQueryService.getRepositories(organizationId).stream()
                .collect(Collectors.toMap(RepositoryResponse::id, RepositoryResponse::name, (first, second) -> first));
    }

    /** The names of the given packs of the organization by id, read in one query. */
    private Map<UUID, String> packNames(UUID organizationId, Collection<UUID> packIds) {
        return packQueryService.getEntitiesByIds(organizationId, packIds).stream()
                .collect(Collectors.toMap(Pack::getId, Pack::getName, (first, second) -> first));
    }

    private PlanReferenceResponse reference(UUID id, Map<UUID, String> names) {
        return id == null ? null : planMapper.toReferenceResponse(id, names.get(id));
    }

    private List<PlanReferenceResponse> references(Collection<UUID> ids, Map<UUID, String> names) {
        return ids.stream()
                .map(id -> reference(id, names))
                .sorted(Comparator.comparing(PlanReferenceResponse::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(PlanReferenceResponse::id))
                .toList();
    }
}
