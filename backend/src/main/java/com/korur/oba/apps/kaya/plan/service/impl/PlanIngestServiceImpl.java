package com.korur.oba.apps.kaya.plan.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 4. Internal
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.identity.service.KayaUserQueryService;
import com.korur.oba.apps.kaya.ingest.constant.IngestConstant;
import com.korur.oba.apps.kaya.ingest.dto.request.IngestPlanRequest;
import com.korur.oba.apps.kaya.pack.entity.Pack;
import com.korur.oba.apps.kaya.pack.service.PackQueryService;
import com.korur.oba.apps.kaya.plan.constant.PlanConstant;
import com.korur.oba.apps.kaya.plan.constant.PlanIngestConstant;
import com.korur.oba.apps.kaya.plan.constant.service.PlanServiceLogConstant;
import com.korur.oba.apps.kaya.plan.entity.Plan;
import com.korur.oba.apps.kaya.plan.entity.PlanVersion;
import com.korur.oba.apps.kaya.plan.enums.PlanKind;
import com.korur.oba.apps.kaya.plan.enums.PlanReviewState;
import com.korur.oba.apps.kaya.plan.repository.PlanRepository;
import com.korur.oba.apps.kaya.plan.repository.PlanVersionRepository;
import com.korur.oba.apps.kaya.plan.service.PlanIngestService;
import com.korur.oba.apps.kaya.release.helper.PackMount;
import com.korur.oba.apps.kaya.release.service.RepositoryPackQueryService;
import com.korur.oba.apps.kaya.session.dto.response.SessionHandoffResponse;
import com.korur.oba.apps.kaya.session.entity.Session;
import com.korur.oba.apps.kaya.session.service.SessionQueryService;
import com.korur.oba.shrine.agent.validator.AgentValidator;

@Service
@RequiredArgsConstructor
public class PlanIngestServiceImpl implements PlanIngestService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(PlanIngestServiceImpl.class);
    private static final Pattern SPEC_PATH = Pattern.compile(IngestConstant.PLAN_PATH_PATTERN);
    private static final Pattern SPEC_PATH_IN_TEXT = Pattern.compile(PlanIngestConstant.SPEC_PATH_IN_TEXT_PATTERN);
    private static final Pattern PLANNING_HANDOFF = Pattern.compile(PlanIngestConstant.PLANNING_HANDOFF_PATTERN);
    private static final Pattern HEADING_ONE = Pattern.compile(PlanIngestConstant.HEADING_ONE_PATTERN);
    private static final Pattern GOAL_LINE = Pattern.compile(PlanIngestConstant.GOAL_LINE_PATTERN);
    private static final Pattern PLANS_DIRECTORY = Pattern.compile(PlanIngestConstant.PLANS_DIRECTORY_PATTERN);

    private final PlanRepository planRepository;
    private final PlanVersionRepository planVersionRepository;
    private final SessionQueryService sessionQueryService;
    private final RepositoryPackQueryService repositoryPackQueryService;
    private final PackQueryService packQueryService;
    private final KayaUserQueryService kayaUserQueryService;
    private final EntityManager entityManager;
    private final LoggingService loggingService;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void storePlans(Session session, List<IngestPlanRequest> plans) {
        if (plans == null || plans.isEmpty()) {
            return;
        }
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.STORING_PLANS, plans.size(), session.getId());
        Set<String> specPaths = new LinkedHashSet<>();
        for (IngestPlanRequest request : plans) {
            if (request.kind() == PlanKind.SPEC) {
                storeSpec(session, request);
                specPaths.add(request.path());
            } else {
                storeSessionPlan(session, request);
            }
        }
        if (!specPaths.isEmpty()) {
            linkFromSessionHandoffs(session, specPaths);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void linkFeature(Session session, String handoffPath, String content) {
        String feature = planningFeatureOf(session, handoffPath);
        if (feature == null) {
            return;
        }
        link(session, feature, specPathsIn(content));
    }

    /**
     * Stores a spec and gives it the pack its plans directory names. A new version matches the
     * pack again, so a spec follows the packs the repository uses when it changes.
     */
    private void storeSpec(Session session, IngestPlanRequest request) {
        String title = specTitle(session, request.path(), request.content());
        UUID packId = packIdOf(session, request.path());
        Optional<Plan> existing = lockedSpec(session, request.path());
        if (existing.isEmpty()) {
            Plan plan = createPlan(session, PlanKind.SPEC, request, title);
            plan.setPackId(packId);
            addVersion(session, plan, request, null);
            return;
        }
        Plan plan = existing.get();
        if (isAgentOnAnotherUsersSpec(session, plan)) {
            return;
        }
        if (addVersion(session, plan, request, title)) {
            plan.setPackId(packId);
            planRepository.save(plan);
        }
    }

    /**
     * The pack a spec belongs to: the directory below plans/ names a pack among the packs the
     * session's repository uses and their parents. A directory that names none, or a spec outside
     * a pack directory, gives no pack.
     */
    private UUID packIdOf(Session session, String path) {
        Matcher matcher = PLANS_DIRECTORY.matcher(path);
        if (!matcher.find()) {
            return null;
        }
        String name = matcher.group(1);
        for (PackMount mount : repositoryPackQueryService.getMounts(session.getOrganizationId(), session.getRepositoryId())) {
            for (Pack pack : packQueryService.getChain(session.getOrganizationId(), mount.packId())) {
                if (pack.getName().equals(name)) {
                    return pack.getId();
                }
            }
        }
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.PLAN_PACK_UNMATCHED,
                name, session.getRepositoryId(), session.getId());
        return null;
    }

    private void storeSessionPlan(Session session, IngestPlanRequest request) {
        String title = sessionPlanTitle(session, request.content());
        Optional<Plan> existing = planRepository.findBySessionIdAndKind(session.getId(), PlanKind.SESSION).map(this::lock);
        if (existing.isEmpty()) {
            addVersion(session, createPlan(session, PlanKind.SESSION, request, title), request, null);
            return;
        }
        Plan plan = existing.get();
        if (addVersion(session, plan, request, title)) {
            planRepository.save(plan);
        }
    }

    private Plan createPlan(Session session, PlanKind kind, IngestPlanRequest request, String title) {
        Plan plan = planRepository.saveAndFlush(Plan.builder()
                .organizationId(session.getOrganizationId())
                .repositoryId(session.getRepositoryId())
                .kind(kind)
                .path(kind == PlanKind.SPEC ? request.path() : null)
                .sessionId(kind == PlanKind.SESSION ? session.getId() : null)
                .title(title)
                .userId(session.getUserId())
                .latestSessionId(session.getId())
                .lastChangedAt(request.capturedAt())
                .build());
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.PLAN_CREATED,
                kind, plan.getId(), session.getId(), session.getRepositoryId());
        return plan;
    }

    /**
     * Adds the capture as the plan's next version unless it repeats the latest content or is older
     * than the latest version, which happens when the recorder resends a capture.
     *
     * @param title the title the new version gives the plan; null keeps the plan's title
     * @return true when a version was added and the plan changed
     */
    private boolean addVersion(Session session, Plan plan, IngestPlanRequest request, String title) {
        String contentSha256 = sha256Of(request.content());
        Optional<PlanVersion> latest = planVersionRepository.findFirstByPlanIdOrderByNumberDesc(plan.getId());
        if (latest.isPresent() && latest.get().getContentSha256().equals(contentSha256)) {
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.PLAN_UNCHANGED,
                    plan.getId(), latest.get().getNumber(), session.getId());
            return false;
        }
        if (latest.isPresent() && request.capturedAt().isBefore(latest.get().getCapturedAt())) {
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.PLAN_CAPTURE_STALE, plan.getId(),
                    session.getId(), request.capturedAt(), latest.get().getNumber(), latest.get().getCapturedAt());
            return false;
        }
        int number = latest.map(version -> version.getNumber() + 1).orElse(1);
        planVersionRepository.save(PlanVersion.builder()
                .planId(plan.getId())
                .number(number)
                .source(request.source())
                .sessionId(session.getId())
                .content(request.content())
                .contentSha256(contentSha256)
                .lineCount((int) Math.max(1, request.content().lines().count()))
                .capturedAt(request.capturedAt())
                .build());
        if (title != null) {
            plan.setTitle(title);
        }
        plan.setLatestVersionNumber(number);
        plan.setLatestSessionId(session.getId());
        plan.setUserId(session.getUserId());
        plan.setLastChangedAt(request.capturedAt());
        // A decision applies to the version it was given on, so a new version is open for review again.
        plan.setReviewState(PlanReviewState.OPEN);
        planRepository.save(plan);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.PLAN_VERSION_ADDED,
                number, request.source(), plan.getId(), session.getId());
        return true;
    }

    /**
     * The SPEC plan of a path in the session's repository, locked and read again, so a new version
     * or feature never writes over a feedback count another transaction changed meanwhile.
     */
    private Optional<Plan> lockedSpec(Session session, String path) {
        return planRepository.findByRepositoryIdAndKindAndPath(session.getRepositoryId(), PlanKind.SPEC, path).map(this::lock);
    }

    private Plan lock(Plan plan) {
        planRepository.findByIdAndOrganizationIdForUpdate(plan.getId(), plan.getOrganizationId());
        entityManager.refresh(plan);
        return plan;
    }

    /**
     * Links the specs of this batch to the planning handoffs the session holds, oldest capture
     * first so the newest handoff wins. This covers a planning handoff that arrived in an earlier
     * batch or in this one, before the spec it names was stored.
     */
    private void linkFromSessionHandoffs(Session session, Set<String> specPaths) {
        List<SessionHandoffResponse> handoffs = new ArrayList<>(
                sessionQueryService.getHandoffs(session.getOrganizationId(), session.getSessionId()));
        handoffs.sort(Comparator.comparing(SessionHandoffResponse::capturedAt));
        for (SessionHandoffResponse handoff : handoffs) {
            String feature = planningFeatureOf(session, handoff.path());
            if (feature == null) {
                continue;
            }
            Set<String> named = specPathsIn(handoff.content());
            named.retainAll(specPaths);
            link(session, feature, named);
        }
    }

    private void link(Session session, String feature, Set<String> specPaths) {
        for (String path : specPaths) {
            Optional<Plan> found = lockedSpec(session, path);
            if (found.isEmpty() || feature.equals(found.get().getFeature())) {
                continue;
            }
            Plan plan = found.get();
            if (isAgentOnAnotherUsersSpec(session, plan)) {
                continue;
            }
            plan.setFeature(feature);
            planRepository.save(plan);
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.PLAN_FEATURE_LINKED,
                    plan.getId(), feature, session.getId());
        }
    }

    /**
     * Tells whether an agent's session reached a spec that belongs to someone else, which it then
     * leaves unchanged. A new version makes its session's user the owner and opens the review again,
     * so the owner's pending review feedback would go to the agent's sessions instead, and an agent's
     * run acts on text that other people write. People keep sharing a spec: whoever versions it last
     * owns it. The user is read only when the spec has another owner.
     */
    private boolean isAgentOnAnotherUsersSpec(Session session, Plan plan) {
        if (session.getUserId().equals(plan.getUserId())
                || !AgentValidator.isAgent(kayaUserQueryService.getEntityById(session.getUserId()))) {
            return false;
        }
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.PLAN_OF_ANOTHER_USER_SKIPPED,
                plan.getId(), plan.getUserId(), session.getId());
        return true;
    }

    /**
     * The feature directory of a planning handoff, or null for any other handoff.
     */
    private String planningFeatureOf(Session session, String handoffPath) {
        if (handoffPath == null) {
            return null;
        }
        Matcher matcher = PLANNING_HANDOFF.matcher(handoffPath);
        if (!matcher.matches()) {
            return null;
        }
        String feature = matcher.group(1);
        if (feature.length() > PlanConstant.FEATURE_MAX_LENGTH) {
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.PLAN_FEATURE_TOO_LONG,
                    handoffPath, session.getId(), PlanConstant.FEATURE_MAX_LENGTH);
            return null;
        }
        return feature;
    }

    /**
     * The distinct spec paths a text names, an absolute or worktree path read from its docs/ part.
     */
    private static Set<String> specPathsIn(String content) {
        Set<String> paths = new LinkedHashSet<>();
        if (content == null) {
            return paths;
        }
        Matcher matcher = SPEC_PATH_IN_TEXT.matcher(content);
        while (matcher.find() && paths.size() < PlanIngestConstant.SPEC_PATHS_IN_TEXT_MAX) {
            String path = matcher.group(1);
            if (path.length() <= PlanConstant.PATH_MAX_LENGTH && SPEC_PATH.matcher(path).matches()) {
                paths.add(path);
            }
        }
        return paths;
    }

    /**
     * The first level-one heading of a spec, else its file name.
     */
    private String specTitle(Session session, String path, String content) {
        for (String line : (Iterable<String>) content.lines()::iterator) {
            Matcher heading = HEADING_ONE.matcher(line);
            if (heading.matches()) {
                return cutTitle(session, heading.group(1));
            }
        }
        return cutTitle(session, path.substring(path.lastIndexOf(PlanIngestConstant.PATH_SEPARATOR) + 1));
    }

    /**
     * The Goal line of a session plan, or the first line after a Goal heading; else its first
     * level-one heading, else a fixed title.
     */
    private String sessionPlanTitle(Session session, String content) {
        boolean afterGoalHeading = false;
        String heading = null;
        for (String line : (Iterable<String>) content.lines()::iterator) {
            if (afterGoalHeading) {
                if (!line.isBlank()) {
                    return cutTitle(session, line.strip());
                }
                continue;
            }
            Matcher goal = GOAL_LINE.matcher(line);
            if (goal.matches()) {
                if (!goal.group(1).isBlank()) {
                    return cutTitle(session, goal.group(1));
                }
                afterGoalHeading = true;
                continue;
            }
            Matcher headingOne = HEADING_ONE.matcher(line);
            if (heading == null && headingOne.matches()) {
                heading = headingOne.group(1);
            }
        }
        return cutTitle(session, heading == null ? PlanIngestConstant.SESSION_PLAN_TITLE_FALLBACK : heading);
    }

    private String cutTitle(Session session, String title) {
        if (title.length() <= PlanConstant.TITLE_MAX_LENGTH) {
            return title;
        }
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.PLAN_TITLE_TRUNCATED,
                session.getId(), PlanConstant.TITLE_MAX_LENGTH);
        return title.substring(0, PlanConstant.TITLE_MAX_LENGTH);
    }

    private String sha256Of(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(PlanIngestConstant.SHA256_ALGORITHM)
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            // Every Java platform must provide SHA-256, so this means a broken runtime.
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE, PlanServiceLogConstant.SHA256_UNAVAILABLE, exception);
            throw new IllegalStateException(PlanServiceLogConstant.SHA256_UNAVAILABLE, exception);
        }
    }
}
