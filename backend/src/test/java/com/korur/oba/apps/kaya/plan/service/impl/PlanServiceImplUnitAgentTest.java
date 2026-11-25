package com.korur.oba.apps.kaya.plan.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

// 3. Java SDK
import java.time.Clock;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.identity.helper.UserAccessHelper;
import com.korur.oba.apps.kaya.plan.dto.request.PlanFeedbackRequest;
import com.korur.oba.apps.kaya.plan.enums.PlanFeedbackDecision;
import com.korur.oba.apps.kaya.plan.mapper.PlanMapper;
import com.korur.oba.apps.kaya.plan.repository.PlanFeedbackRepository;
import com.korur.oba.apps.kaya.plan.repository.PlanRepository;
import com.korur.oba.apps.kaya.plan.repository.PlanVersionRepository;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.agent.validator.AgentValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserKind;
import com.korur.oba.shrine.identity.service.UserQueryService;

/**
 * Runs plan feedback through the real {@link AgentValidator}: the CLI injects feedback into the plan
 * owner's live session, so an agent gives none, neither a decision nor a comment.
 */
@ExtendWith(MockitoExtension.class)
class PlanServiceImplUnitAgentTest {

    @Mock
    private PlanRepository planRepository;
    @Mock
    private PlanVersionRepository planVersionRepository;
    @Mock
    private PlanFeedbackRepository planFeedbackRepository;
    @Mock
    private UserQueryService userQueryService;

    private PlanServiceImpl planService;

    private final UUID organizationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        LoggingService loggingService = new LoggingService();
        planService = new PlanServiceImpl(planRepository, planVersionRepository, planFeedbackRepository,
                mock(UserAccessHelper.class), new AgentValidator(userQueryService, loggingService),
                mock(PlanMapper.class), loggingService, Clock.systemUTC());
    }

    @Test
    void giveFeedback_agentComment_throwsForbiddenAndRecordsNothing() {
        assertRefused(PlanFeedbackDecision.COMMENT);
    }

    @Test
    void giveFeedback_agentRequestsChanges_throwsForbiddenAndRecordsNothing() {
        assertRefused(PlanFeedbackDecision.REQUEST_CHANGES);
    }

    private void assertRefused(PlanFeedbackDecision decision) {
        User agent = User.builder().id(UUID.randomUUID()).kind(UserKind.AGENT).build();
        PlanFeedbackRequest request = new PlanFeedbackRequest(1, decision, "Drop the review step", null, null, null);

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> planService.giveFeedback(agent, organizationId, UUID.randomUUID(), request));

        assertEquals("AGT_SEC_002", exception.getErrorCode());
        verifyNoInteractions(planRepository, planVersionRepository, planFeedbackRepository);
    }
}
