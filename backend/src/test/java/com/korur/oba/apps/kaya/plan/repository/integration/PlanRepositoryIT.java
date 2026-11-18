package com.korur.oba.apps.kaya.plan.repository.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.plan.entity.Plan;
import com.korur.oba.apps.kaya.plan.entity.PlanFeedback;
import com.korur.oba.apps.kaya.plan.entity.PlanVersion;
import com.korur.oba.apps.kaya.plan.enums.PlanFeedbackDecision;
import com.korur.oba.apps.kaya.plan.enums.PlanKind;
import com.korur.oba.apps.kaya.plan.enums.PlanReviewState;
import com.korur.oba.apps.kaya.plan.enums.PlanVersionSource;
import com.korur.oba.apps.kaya.plan.repository.PlanFeedbackRepository;
import com.korur.oba.apps.kaya.plan.repository.PlanRepository;
import com.korur.oba.apps.kaya.plan.repository.PlanVersionRepository;
import com.korur.oba.apps.kaya.session.entity.Session;
import com.korur.oba.apps.kaya.session.repository.SessionRepository;
import com.korur.oba.apps.kaya.support.AbstractSessionIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The plan entities round-trip through the plan schema: every column, the native enum types and
 * the CHAR(64) hash are written and read back as stored, and the repository lookups the plan
 * services rely on find the rows. The context itself starting proves the mapping passes schema
 * validation.
 */
class PlanRepositoryIT extends AbstractSessionIT {

    private static final String SPEC_PATH = "docs/private/plans/kaya/04_KAYA_PLANS.md";
    private static final String CONTENT = "# Kaya plans\n\n1. Goal\n2. Steps\n";
    private static final String SHA256 = "a".repeat(64);
    private static final Instant CAPTURED_AT = Instant.parse("2026-10-20T09:30:00Z");

    @Autowired
    private PlanRepository planRepository;
    @Autowired
    private PlanVersionRepository planVersionRepository;
    @Autowired
    private PlanFeedbackRepository planFeedbackRepository;
    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private Tenant tenant;
    private Person reviewer;
    private Session session;

    @BeforeEach
    void setUp() throws Exception {
        tenant = newTenant();
        reviewer = signIn(tenant, "ada");
        session = newSession();
    }

    private Session newSession() throws Exception {
        Machine machine = registerMachine(tenant, reviewer, "laptop-" + uniqueSuffix());
        String sessionId = ingestSession(machine, remote(), 2);
        return sessionRepository.findByOrganizationIdAndSessionId(tenant.organization().getId(), sessionId).orElseThrow();
    }

    private Plan saveSpecPlan() {
        return planRepository.saveAndFlush(Plan.builder()
                .organizationId(session.getOrganizationId())
                .repositoryId(session.getRepositoryId())
                .kind(PlanKind.SPEC)
                .path(SPEC_PATH)
                .title("Kaya plans")
                .feature("kaya-plans")
                .latestVersionNumber(1)
                .latestSessionId(session.getId())
                .userId(session.getUserId())
                .lastChangedAt(CAPTURED_AT)
                .build());
    }

    private PlanVersion saveVersion(Plan plan) {
        return planVersionRepository.saveAndFlush(PlanVersion.builder()
                .planId(plan.getId())
                .number(1)
                .source(PlanVersionSource.FILE)
                .sessionId(session.getId())
                .content(CONTENT)
                .contentSha256(SHA256)
                .lineCount(4)
                .capturedAt(CAPTURED_AT)
                .build());
    }

    @Test
    void savePlan_specWithDefaults_readsBackEveryColumn() {
        Plan saved = saveSpecPlan();

        Plan read = planRepository.findByIdAndOrganizationId(saved.getId(), tenant.organization().getId()).orElseThrow();

        assertEquals(PlanKind.SPEC, read.getKind());
        assertEquals(SPEC_PATH, read.getPath());
        assertNull(read.getSessionId());
        assertEquals("Kaya plans", read.getTitle());
        assertEquals("kaya-plans", read.getFeature());
        assertNull(read.getPackId());
        assertEquals(PlanReviewState.OPEN, read.getReviewState());
        assertEquals(1, read.getLatestVersionNumber());
        assertEquals(session.getId(), read.getLatestSessionId());
        assertEquals(session.getUserId(), read.getUserId());
        assertEquals(0, read.getOpenFeedbackCount());
        assertEquals(CAPTURED_AT, read.getLastChangedAt());
        assertEquals("SPEC", jdbcTemplate.queryForObject(
                "SELECT kind::text FROM kaya.plans WHERE id = ?", String.class, saved.getId()));
    }

    @Test
    void findByRepositoryIdAndKindAndPath_specExists_returnsIt() {
        Plan saved = saveSpecPlan();

        Optional<Plan> found = planRepository.findByRepositoryIdAndKindAndPath(
                session.getRepositoryId(), PlanKind.SPEC, SPEC_PATH);

        assertEquals(saved.getId(), found.orElseThrow().getId());
    }

    @Test
    void findBySessionIdAndKind_sessionPlanExists_returnsIt() {
        Plan saved = planRepository.saveAndFlush(Plan.builder()
                .organizationId(session.getOrganizationId())
                .repositoryId(session.getRepositoryId())
                .kind(PlanKind.SESSION)
                .sessionId(session.getId())
                .title("Goal: ship plans")
                .userId(session.getUserId())
                .lastChangedAt(CAPTURED_AT)
                .build());

        Plan found = planRepository.findBySessionIdAndKind(session.getId(), PlanKind.SESSION).orElseThrow();

        assertEquals(saved.getId(), found.getId());
        assertNull(found.getPath());
    }

    @Test
    void savePlan_reviewStateChanged_storesTheNativeEnumValue() {
        Plan saved = saveSpecPlan();
        saved.setReviewState(PlanReviewState.CHANGES_REQUESTED);
        saved.setOpenFeedbackCount(2);

        planRepository.saveAndFlush(saved);

        Plan read = planRepository.findById(saved.getId()).orElseThrow();
        assertEquals(PlanReviewState.CHANGES_REQUESTED, read.getReviewState());
        assertEquals(2, read.getOpenFeedbackCount());
    }

    @Test
    void findByIdAndOrganizationIdForUpdate_otherOrganization_returnsEmpty() {
        Plan saved = saveSpecPlan();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        Optional<Plan> own = transaction.execute(status ->
                planRepository.findByIdAndOrganizationIdForUpdate(saved.getId(), tenant.organization().getId()));
        Optional<Plan> other = transaction.execute(status ->
                planRepository.findByIdAndOrganizationIdForUpdate(saved.getId(), UUID.randomUUID()));

        assertEquals(saved.getId(), own.orElseThrow().getId());
        assertTrue(other.isEmpty());
    }

    @Test
    void saveVersion_validContent_readsBackEveryColumn() {
        Plan plan = saveSpecPlan();
        PlanVersion saved = saveVersion(plan);

        PlanVersion read = planVersionRepository.findByPlanIdAndNumber(plan.getId(), 1).orElseThrow();

        assertEquals(saved.getId(), read.getId());
        assertEquals(PlanVersionSource.FILE, read.getSource());
        assertEquals(session.getId(), read.getSessionId());
        assertEquals(CONTENT, read.getContent());
        assertEquals(SHA256, read.getContentSha256());
        assertEquals(4, read.getLineCount());
        assertEquals(CAPTURED_AT, read.getCapturedAt());
    }

    @Test
    void findFirstByPlanIdOrderByNumberDesc_twoVersions_returnsTheLatest() {
        Plan plan = saveSpecPlan();
        saveVersion(plan);
        PlanVersion second = planVersionRepository.saveAndFlush(PlanVersion.builder()
                .planId(plan.getId())
                .number(2)
                .source(PlanVersionSource.PLAN_MODE)
                .content(CONTENT + "3. Verification\n")
                .contentSha256("b".repeat(64))
                .lineCount(5)
                .capturedAt(CAPTURED_AT.plusSeconds(60))
                .build());

        PlanVersion latest = planVersionRepository.findFirstByPlanIdOrderByNumberDesc(plan.getId()).orElseThrow();
        List<PlanVersion> all = planVersionRepository.findByPlanIdOrderByNumberAsc(plan.getId());

        assertEquals(second.getId(), latest.getId());
        assertNull(latest.getSessionId());
        assertEquals(List.of(1, 2), all.stream().map(PlanVersion::getNumber).toList());
    }

    @Test
    void saveFeedback_anchoredComment_readsBackEveryColumn() {
        Plan plan = saveSpecPlan();
        PlanVersion version = saveVersion(plan);
        PlanFeedback saved = planFeedbackRepository.saveAndFlush(PlanFeedback.builder()
                .planId(plan.getId())
                .versionId(version.getId())
                .reviewerId(reviewer.user().getId())
                .decision(PlanFeedbackDecision.REQUEST_CHANGES)
                .comment("Split step 2.")
                .lineStart(3)
                .lineEnd(4)
                .quote("2. Steps")
                .build());

        PlanFeedback read = planFeedbackRepository.findByIdAndPlanId(saved.getId(), plan.getId()).orElseThrow();

        assertEquals(version.getId(), read.getVersionId());
        assertEquals(reviewer.user().getId(), read.getReviewerId());
        assertEquals(PlanFeedbackDecision.REQUEST_CHANGES, read.getDecision());
        assertEquals("Split step 2.", read.getComment());
        assertEquals(3, read.getLineStart());
        assertEquals(4, read.getLineEnd());
        assertEquals("2. Steps", read.getQuote());
        assertNull(read.getDeliveredAt());
        assertNull(read.getDeliveredSessionId());
    }

    @Test
    void saveFeedback_approvalDelivered_readsBackTheDeliveryInOrder() {
        Plan plan = saveSpecPlan();
        PlanVersion version = saveVersion(plan);
        PlanFeedback comment = planFeedbackRepository.saveAndFlush(PlanFeedback.builder()
                .planId(plan.getId())
                .versionId(version.getId())
                .reviewerId(reviewer.user().getId())
                .decision(PlanFeedbackDecision.COMMENT)
                .comment("Looks right.")
                .build());
        PlanFeedback approval = planFeedbackRepository.saveAndFlush(PlanFeedback.builder()
                .planId(plan.getId())
                .versionId(version.getId())
                .reviewerId(reviewer.user().getId())
                .decision(PlanFeedbackDecision.APPROVE)
                .build());
        Instant deliveredAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        approval.setDeliveredAt(deliveredAt);
        approval.setDeliveredSessionId(session.getSessionId());
        planFeedbackRepository.saveAndFlush(approval);

        List<PlanFeedback> feedback = planFeedbackRepository.findByPlanIdOrderByCreatedAtAscIdAsc(plan.getId());

        assertEquals(List.of(comment.getId(), approval.getId()), feedback.stream().map(PlanFeedback::getId).toList());
        PlanFeedback delivered = feedback.get(1);
        assertNull(delivered.getComment());
        assertEquals(deliveredAt, delivered.getDeliveredAt());
        assertEquals(session.getSessionId(), delivered.getDeliveredSessionId());
    }
}
