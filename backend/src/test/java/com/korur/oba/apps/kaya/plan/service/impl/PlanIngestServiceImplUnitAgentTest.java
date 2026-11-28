package com.korur.oba.apps.kaya.plan.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 2. Third-party
import jakarta.persistence.EntityManager;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.identity.service.KayaUserQueryService;
import com.korur.oba.apps.kaya.ingest.dto.request.IngestPlanRequest;
import com.korur.oba.apps.kaya.pack.service.PackQueryService;
import com.korur.oba.apps.kaya.plan.entity.Plan;
import com.korur.oba.apps.kaya.plan.entity.PlanVersion;
import com.korur.oba.apps.kaya.plan.enums.PlanKind;
import com.korur.oba.apps.kaya.plan.enums.PlanReviewState;
import com.korur.oba.apps.kaya.plan.enums.PlanVersionSource;
import com.korur.oba.apps.kaya.plan.repository.PlanRepository;
import com.korur.oba.apps.kaya.plan.repository.PlanVersionRepository;
import com.korur.oba.apps.kaya.release.service.RepositoryPackQueryService;
import com.korur.oba.apps.kaya.session.entity.Session;
import com.korur.oba.apps.kaya.session.service.SessionQueryService;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserKind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A SPEC plan is shared by everyone who writes its file, and whoever versions it last owns it and
 * receives its review feedback. An agent's session leaves a spec of another user as it is, so it
 * cannot take over that user's spec and the feedback waiting on it; people keep sharing specs.
 */
@ExtendWith(MockitoExtension.class)
class PlanIngestServiceImplUnitAgentTest {

    private static final String SPEC_PATH = "docs/plans/x/01_X.md";
    private static final Instant CAPTURED_AT = Instant.parse("2026-11-27T09:00:00Z");

    @Mock
    private PlanRepository planRepository;
    @Mock
    private PlanVersionRepository planVersionRepository;
    @Mock
    private SessionQueryService sessionQueryService;
    @Mock
    private RepositoryPackQueryService repositoryPackQueryService;
    @Mock
    private PackQueryService packQueryService;
    @Mock
    private KayaUserQueryService kayaUserQueryService;
    @Mock
    private EntityManager entityManager;

    private PlanIngestServiceImpl planIngestService;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID repositoryId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        planIngestService = new PlanIngestServiceImpl(planRepository, planVersionRepository, sessionQueryService,
                repositoryPackQueryService, packQueryService, kayaUserQueryService, entityManager,
                new LoggingService());
    }

    @Test
    void storePlans_agentVersionsAPersonsSpec_addsNoVersionAndKeepsTheOwnerAndTheReview() {
        User person = user(UserKind.HUMAN);
        User agent = user(UserKind.AGENT);
        Plan plan = specOf(person);
        plan.setReviewState(PlanReviewState.CHANGES_REQUESTED);
        when(planRepository.findByRepositoryIdAndKindAndPath(repositoryId, PlanKind.SPEC, SPEC_PATH))
                .thenReturn(Optional.of(plan));
        when(kayaUserQueryService.getEntityById(agent.getId())).thenReturn(agent);

        planIngestService.storePlans(sessionOf(agent), List.of(spec("# X\n\nchanged by the agent\n")));

        assertEquals(person.getId(), plan.getUserId());
        assertEquals(PlanReviewState.CHANGES_REQUESTED, plan.getReviewState());
        verify(planVersionRepository, never()).save(any());
        verify(planRepository, never()).save(any());
    }

    @Test
    void storePlans_personVersionsAnotherPersonsSpec_addsAVersionAndTakesItOver() {
        User owner = user(UserKind.HUMAN);
        User person = user(UserKind.HUMAN);
        Plan plan = specOf(owner);
        plan.setReviewState(PlanReviewState.CHANGES_REQUESTED);
        when(planRepository.findByRepositoryIdAndKindAndPath(repositoryId, PlanKind.SPEC, SPEC_PATH))
                .thenReturn(Optional.of(plan));
        when(kayaUserQueryService.getEntityById(person.getId())).thenReturn(person);
        when(planVersionRepository.findFirstByPlanIdOrderByNumberDesc(plan.getId())).thenReturn(Optional.empty());

        planIngestService.storePlans(sessionOf(person), List.of(spec("# X\n\nchanged by a colleague\n")));

        assertEquals(person.getId(), plan.getUserId());
        assertEquals(PlanReviewState.OPEN, plan.getReviewState());
        verify(planVersionRepository).save(any(PlanVersion.class));
    }

    @Test
    void storePlans_agentVersionsItsOwnSpec_addsAVersionWithoutReadingItsUser() {
        User agent = user(UserKind.AGENT);
        Plan plan = specOf(agent);
        when(planRepository.findByRepositoryIdAndKindAndPath(repositoryId, PlanKind.SPEC, SPEC_PATH))
                .thenReturn(Optional.of(plan));
        when(planVersionRepository.findFirstByPlanIdOrderByNumberDesc(plan.getId())).thenReturn(Optional.empty());

        planIngestService.storePlans(sessionOf(agent), List.of(spec("# X\n\nthe agent's own spec\n")));

        verify(planVersionRepository).save(any(PlanVersion.class));
        verifyNoInteractions(kayaUserQueryService);
    }

    @Test
    void linkFeature_agentPlanningHandoffNamingAPersonsSpec_keepsTheFeature() {
        User person = user(UserKind.HUMAN);
        User agent = user(UserKind.AGENT);
        Plan plan = specOf(person);
        plan.setFeature("person-feature");
        when(planRepository.findByRepositoryIdAndKindAndPath(repositoryId, PlanKind.SPEC, SPEC_PATH))
                .thenReturn(Optional.of(plan));
        when(kayaUserQueryService.getEntityById(agent.getId())).thenReturn(agent);

        planIngestService.linkFeature(sessionOf(agent), "agent-feature/planning.md", "Spec: " + SPEC_PATH);

        assertEquals("person-feature", plan.getFeature());
        verify(planRepository, never()).save(any());
    }

    @Test
    void linkFeature_personPlanningHandoffNamingAnotherPersonsSpec_linksTheFeature() {
        User owner = user(UserKind.HUMAN);
        User person = user(UserKind.HUMAN);
        Plan plan = specOf(owner);
        plan.setFeature("old-feature");
        when(planRepository.findByRepositoryIdAndKindAndPath(repositoryId, PlanKind.SPEC, SPEC_PATH))
                .thenReturn(Optional.of(plan));
        when(kayaUserQueryService.getEntityById(person.getId())).thenReturn(person);

        planIngestService.linkFeature(sessionOf(person), "new-feature/planning.md", "Spec: " + SPEC_PATH);

        assertEquals("new-feature", plan.getFeature());
        verify(planRepository).save(plan);
    }

    private Session sessionOf(User user) {
        return Session.builder().id(UUID.randomUUID()).organizationId(organizationId).repositoryId(repositoryId)
                .userId(user.getId()).sessionId("session-" + user.getId()).build();
    }

    private Plan specOf(User owner) {
        Plan plan = Plan.builder().organizationId(organizationId).repositoryId(repositoryId).kind(PlanKind.SPEC)
                .path(SPEC_PATH).title("X").userId(owner.getId()).build();
        plan.setId(UUID.randomUUID());
        return plan;
    }

    private static IngestPlanRequest spec(String content) {
        return new IngestPlanRequest(PlanKind.SPEC, PlanVersionSource.FILE, SPEC_PATH, content, CAPTURED_AT);
    }

    private static User user(UserKind kind) {
        return User.builder().id(UUID.randomUUID()).kind(kind).build();
    }
}
