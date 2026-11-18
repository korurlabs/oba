package com.korur.oba.apps.kaya.plan.repository.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

// 3. Java SDK
import java.time.Instant;

// 4. Internal
import com.korur.oba.apps.kaya.plan.entity.Plan;
import com.korur.oba.apps.kaya.plan.entity.PlanFeedback;
import com.korur.oba.apps.kaya.plan.entity.PlanVersion;
import com.korur.oba.apps.kaya.plan.enums.PlanFeedbackDecision;
import com.korur.oba.apps.kaya.plan.enums.PlanKind;
import com.korur.oba.apps.kaya.plan.enums.PlanVersionSource;
import com.korur.oba.apps.kaya.plan.repository.PlanFeedbackRepository;
import com.korur.oba.apps.kaya.plan.repository.PlanRepository;
import com.korur.oba.apps.kaya.plan.repository.PlanVersionRepository;
import com.korur.oba.apps.kaya.session.entity.Session;
import com.korur.oba.apps.kaya.session.repository.SessionRepository;
import com.korur.oba.apps.kaya.support.AbstractSessionIT;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The plan schema refuses rows that break its invariants, whatever the application does: a SPEC
 * plan without a path would be unreachable by (repository, path), and a feedback range that ends
 * before it starts cannot be anchored in the plan text.
 */
class PlanMigrationIT extends AbstractSessionIT {

    private static final Instant CAPTURED_AT = Instant.parse("2026-10-20T09:30:00Z");

    @Autowired
    private PlanRepository planRepository;
    @Autowired
    private PlanVersionRepository planVersionRepository;
    @Autowired
    private PlanFeedbackRepository planFeedbackRepository;
    @Autowired
    private SessionRepository sessionRepository;

    private Tenant tenant;
    private Person reviewer;
    private Session session;

    @BeforeEach
    void setUp() throws Exception {
        tenant = newTenant();
        reviewer = signIn(tenant, "ada");
        Machine machine = registerMachine(tenant, reviewer, "laptop-" + uniqueSuffix());
        String sessionId = ingestSession(machine, remote(), 2);
        session = sessionRepository.findByOrganizationIdAndSessionId(tenant.organization().getId(), sessionId)
                .orElseThrow();
    }

    private Plan.PlanBuilder<?, ?> plan(PlanKind kind) {
        return Plan.builder()
                .organizationId(session.getOrganizationId())
                .repositoryId(session.getRepositoryId())
                .kind(kind)
                .title("Kaya plans")
                .userId(session.getUserId())
                .lastChangedAt(CAPTURED_AT);
    }

    private PlanFeedback.PlanFeedbackBuilder<?, ?> feedback(Plan plan, PlanVersion version) {
        return PlanFeedback.builder()
                .planId(plan.getId())
                .versionId(version.getId())
                .reviewerId(reviewer.user().getId())
                .decision(PlanFeedbackDecision.REQUEST_CHANGES)
                .comment("Split step 2.");
    }

    private PlanVersion savedVersion() {
        Plan saved = planRepository.saveAndFlush(plan(PlanKind.SPEC).path("docs/plans/a.md").build());
        return planVersionRepository.saveAndFlush(PlanVersion.builder()
                .planId(saved.getId())
                .number(1)
                .source(PlanVersionSource.FILE)
                .content("# A\n")
                .contentSha256("a".repeat(64))
                .lineCount(1)
                .capturedAt(CAPTURED_AT)
                .build());
    }

    @Test
    void savePlan_specWithoutPath_isRejected() {
        Plan noPath = plan(PlanKind.SPEC).build();

        assertThrows(DataIntegrityViolationException.class, () -> planRepository.saveAndFlush(noPath));
    }

    @Test
    void savePlan_sessionPlanWithoutSession_isRejected() {
        Plan noSession = plan(PlanKind.SESSION).build();

        assertThrows(DataIntegrityViolationException.class, () -> planRepository.saveAndFlush(noSession));
    }

    @Test
    void savePlan_specWithSessionId_isRejected() {
        Plan both = plan(PlanKind.SPEC).path("docs/plans/b.md").sessionId(session.getId()).build();

        assertThrows(DataIntegrityViolationException.class, () -> planRepository.saveAndFlush(both));
    }

    @Test
    void saveFeedback_lineEndBeforeLineStart_isRejected() {
        PlanVersion version = savedVersion();
        Plan owner = planRepository.findById(version.getPlanId()).orElseThrow();
        PlanFeedback backwards = feedback(owner, version).lineStart(5).lineEnd(3).build();

        assertThrows(DataIntegrityViolationException.class, () -> planFeedbackRepository.saveAndFlush(backwards));
    }

    @Test
    void saveFeedback_onlyLineStart_isRejected() {
        PlanVersion version = savedVersion();
        Plan owner = planRepository.findById(version.getPlanId()).orElseThrow();
        PlanFeedback half = feedback(owner, version).lineStart(2).build();

        assertThrows(DataIntegrityViolationException.class, () -> planFeedbackRepository.saveAndFlush(half));
    }
}
