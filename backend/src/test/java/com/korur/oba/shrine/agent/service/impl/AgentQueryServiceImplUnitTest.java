package com.korur.oba.shrine.agent.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.token.entity.ApiToken;
import com.korur.oba.dojo.security.token.enums.ApiTokenKind;
import com.korur.oba.dojo.security.token.service.ApiTokenQueryService;
import com.korur.oba.shrine.access.dto.response.AppGrantResponse;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.enums.AppGrantTargetType;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.agent.dto.response.AgentFlowOptionResponse;
import com.korur.oba.shrine.agent.dto.response.AgentOptionsResponse;
import com.korur.oba.shrine.agent.dto.response.AgentResponse;
import com.korur.oba.shrine.agent.helper.AgentFlowHelper;
import com.korur.oba.shrine.agent.mapper.AgentMapperImpl;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.entity.embedded.UserAgentDetail;
import com.korur.oba.shrine.identity.entity.embedded.UserProfile;
import com.korur.oba.shrine.identity.enums.AgentFlow;
import com.korur.oba.shrine.identity.enums.AgentProvider;
import com.korur.oba.shrine.identity.enums.AgentRole;
import com.korur.oba.shrine.identity.enums.UserKind;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.identity.service.UserQueryService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentQueryServiceImplUnitTest {

    @Mock
    private UserQueryService userQueryService;
    @Mock
    private ApiTokenQueryService apiTokenQueryService;
    @Mock
    private AppAccessQueryService appAccessQueryService;
    @Mock
    private LoggingService loggingService;

    private AgentQueryServiceImpl agentQueryService;
    private User actor;
    private UUID organizationId;

    @BeforeEach
    void setUp() {
        agentQueryService = new AgentQueryServiceImpl(userQueryService, apiTokenQueryService, appAccessQueryService,
                new AgentFlowHelper(), new AgentMapperImpl(), loggingService);
        actor = User.builder().id(UUID.randomUUID()).email("admin@example.com").build();
        organizationId = UUID.randomUUID();
    }

    @Test
    void getOptions_routineAndApi_listsAnthropicForRoutineAndEveryProviderForApi() {
        AgentOptionsResponse options = agentQueryService.getOptions();

        assertEquals(List.of(
                new AgentFlowOptionResponse(AgentFlow.ROUTINE, List.of(AgentProvider.ANTHROPIC)),
                new AgentFlowOptionResponse(AgentFlow.API, List.of(AgentProvider.ANTHROPIC, AgentProvider.OPENAI,
                        AgentProvider.XAI, AgentProvider.DEEPSEEK))),
                options.flows());
    }

    @Test
    void getAgentEntity_personOrOtherOrganization_throwsAgentNotFound() {
        UUID agentId = UUID.randomUUID();
        when(userQueryService.findAgent(organizationId, agentId)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> agentQueryService.getAgentEntity(organizationId, agentId));

        assertEquals("AGT_NOT_001", exception.getErrorCode());
    }

    @Test
    void listAgents_agentsWithGrantsAndToken_mapsAppsTokenAndNullRoutine() {
        User routineAgent = agent("Alpha", AgentFlow.ROUTINE, AgentProvider.ANTHROPIC);
        User apiAgent = agent("Beta", AgentFlow.API, AgentProvider.DEEPSEEK);
        ApiToken token = ApiToken.builder()
                .id(UUID.randomUUID())
                .kind(ApiTokenKind.AGENT)
                .subjectId(routineAgent.getId())
                .tokenPrefix("oat_abcdefgh")
                .lastUsedAt(Instant.parse("2026-11-24T10:00:00Z"))
                .expiresAt(Instant.parse("2027-11-24T10:00:00Z"))
                .build();
        when(userQueryService.getAgentsOfOrganization(organizationId)).thenReturn(List.of(routineAgent, apiAgent));
        when(apiTokenQueryService.findLive(ApiTokenKind.AGENT, Set.of(routineAgent.getId(), apiAgent.getId())))
                .thenReturn(List.of(token));
        when(appAccessQueryService.listGrants(actor, organizationId)).thenReturn(List.of(
                userGrant(App.KALKAN, routineAgent.getId()),
                userGrant(App.KAZAN, routineAgent.getId()),
                userGrant(App.OTAK, UUID.randomUUID()),
                new AppGrantResponse(UUID.randomUUID(), App.KAYA, AppGrantTargetType.GROUP, null, null,
                        UUID.randomUUID(), "Everyone", null, Instant.now())));

        List<AgentResponse> agents = agentQueryService.listAgents(actor, organizationId);

        assertEquals(2, agents.size());
        AgentResponse first = agents.get(0);
        assertEquals(routineAgent.getId(), first.id());
        assertEquals("Alpha", first.name());
        assertEquals(AgentFlow.ROUTINE, first.flow());
        assertEquals(AgentProvider.ANTHROPIC, first.provider());
        assertEquals(AgentRole.CYBERSECURITY_ENGINEER, first.role());
        assertEquals(List.of(App.KAZAN, App.KALKAN), first.apps());
        assertEquals(UserStatus.ACTIVE, first.status());
        assertEquals("oat_abcdefgh", first.tokenPrefix());
        assertEquals(token.getLastUsedAt(), first.tokenLastUsedAt());
        assertEquals(token.getExpiresAt(), first.tokenExpiresAt());
        assertNull(first.routineId());
        assertNull(first.routineSecretPrefix());
        assertNull(first.lastFiredAt());
        assertNull(first.lastFireStatus());
        assertNull(first.lastSessionUrl());

        AgentResponse second = agents.get(1);
        assertEquals(AgentFlow.API, second.flow());
        assertTrue(second.apps().isEmpty());
        assertNull(second.tokenPrefix());
        assertNull(second.tokenExpiresAt());
    }

    @Test
    void listAgents_noAgents_returnsEmptyWithoutLookups() {
        when(userQueryService.getAgentsOfOrganization(organizationId)).thenReturn(List.of());

        assertTrue(agentQueryService.listAgents(actor, organizationId).isEmpty());
        verifyNoInteractions(apiTokenQueryService, appAccessQueryService);
    }

    private User agent(String name, AgentFlow flow, AgentProvider provider) {
        return User.builder()
                .id(UUID.randomUUID())
                .email("agent-" + UUID.randomUUID() + "@agents.oba.invalid")
                .status(UserStatus.ACTIVE)
                .kind(UserKind.AGENT)
                .profile(UserProfile.builder().firstName(name).build())
                .agentDetail(UserAgentDetail.builder()
                        .role(AgentRole.CYBERSECURITY_ENGINEER)
                        .flow(flow)
                        .provider(provider)
                        .organizationId(organizationId)
                        .build())
                .build();
    }

    private static AppGrantResponse userGrant(App app, UUID userId) {
        return new AppGrantResponse(UUID.randomUUID(), app, AppGrantTargetType.USER, userId, "Agent", null, null, null,
                Instant.now());
    }
}
