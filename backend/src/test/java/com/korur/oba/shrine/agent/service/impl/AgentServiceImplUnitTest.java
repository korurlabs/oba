package com.korur.oba.shrine.agent.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

// 2. Third-party
import org.hibernate.exception.ConstraintViolationException;

// 3. Java SDK
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.audit.dto.request.AuditLogRequest;
import com.korur.oba.dojo.audit.mapper.AuditLogMapper;
import com.korur.oba.dojo.audit.service.AuditService;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.token.dto.IssuedApiToken;
import com.korur.oba.dojo.security.token.entity.ApiToken;
import com.korur.oba.dojo.security.token.enums.ApiTokenKind;
import com.korur.oba.dojo.security.token.service.ApiTokenService;
import com.korur.oba.shrine.access.dto.request.AppGrantCreateRequest;
import com.korur.oba.shrine.access.dto.response.AppGrantResponse;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.enums.AppGrantTargetType;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.access.service.AppAccessService;
import com.korur.oba.shrine.agent.dto.request.AgentCreateRequest;
import com.korur.oba.shrine.agent.dto.request.AgentStatusUpdateRequest;
import com.korur.oba.shrine.agent.dto.request.AgentUpdateRequest;
import com.korur.oba.shrine.agent.dto.response.AgentResponse;
import com.korur.oba.shrine.agent.dto.response.AgentTokenCreatedResponse;
import com.korur.oba.shrine.agent.helper.AgentFlowHelper;
import com.korur.oba.shrine.agent.mapper.AgentMapper;
import com.korur.oba.shrine.agent.mapper.AgentMapperImpl;
import com.korur.oba.shrine.agent.service.AgentQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.entity.embedded.UserAgentDetail;
import com.korur.oba.shrine.identity.entity.embedded.UserProfile;
import com.korur.oba.shrine.identity.enums.AgentFlow;
import com.korur.oba.shrine.identity.enums.AgentProvider;
import com.korur.oba.shrine.identity.enums.AgentRole;
import com.korur.oba.shrine.identity.enums.UserKind;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.identity.service.UserQueryService;
import com.korur.oba.shrine.identity.service.UserService;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;
import com.korur.oba.shrine.organization.service.OrganizationMemberService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentServiceImplUnitTest {

    private static final String PLAINTEXT = "oat_secretsecretsecretsecretsecretsecretsecret00";

    @Mock
    private UserService userService;
    @Mock
    private UserQueryService userQueryService;
    @Mock
    private AgentQueryService agentQueryService;
    @Mock
    private OrganizationMemberService organizationMemberService;
    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;
    @Mock
    private AppAccessService appAccessService;
    @Mock
    private AppAccessQueryService appAccessQueryService;
    @Mock
    private ApiTokenService apiTokenService;
    @Mock
    private AuditLogMapper auditLogMapper;
    @Mock
    private AuditService auditService;
    @Mock
    private LoggingService loggingService;

    private final AgentMapper agentMapper = new AgentMapperImpl();

    private AgentServiceImpl agentService;
    private User actor;
    private UUID organizationId;
    private Organization organization;

    @BeforeEach
    void setUp() {
        agentService = new AgentServiceImpl(userService, userQueryService, agentQueryService, organizationMemberService,
                organizationMemberQueryService, appAccessService, appAccessQueryService, apiTokenService,
                new AgentFlowHelper(), agentMapper, auditLogMapper, auditService, loggingService);
        actor = User.builder().id(UUID.randomUUID()).email("admin@example.com").build();
        organizationId = UUID.randomUUID();
        organization = Organization.builder().id(organizationId).build();
    }

    // ---------- create ----------

    @Test
    void createAgent_routineWithAnthropic_createsUserMemberAndGrants() {
        AgentCreateRequest request = new AgentCreateRequest("  Pentest agent ", AgentFlow.ROUTINE, AgentProvider.ANTHROPIC,
                AgentRole.CYBERSECURITY_ENGINEER, Set.of(App.KAZAN, App.KALKAN));
        User agent = agent(AgentFlow.ROUTINE, AgentProvider.ANTHROPIC);
        when(userService.createAgentUser(anyString(), eq("Pentest agent"), any(UserAgentDetail.class))).thenReturn(agent);
        AgentResponse expected = response(agent.getId());
        when(agentQueryService.getAgent(actor, organizationId, agent.getId())).thenReturn(expected);

        AgentResponse result = agentService.createAgent(actor, organizationId, request);

        assertSame(expected, result);
        ArgumentCaptor<String> email = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<UserAgentDetail> detail = ArgumentCaptor.forClass(UserAgentDetail.class);
        verify(userService).createAgentUser(email.capture(), eq("Pentest agent"), detail.capture());
        assertTrue(email.getValue().matches("agent-[0-9a-f-]{36}@agents\\.oba\\.invalid"));
        assertEquals(AgentFlow.ROUTINE, detail.getValue().getFlow());
        assertEquals(AgentProvider.ANTHROPIC, detail.getValue().getProvider());
        assertEquals(AgentRole.CYBERSECURITY_ENGINEER, detail.getValue().getRole());
        assertEquals(organizationId, detail.getValue().getOrganizationId());
        assertEquals(actor.getId(), detail.getValue().getCreatedByUserId());

        ArgumentCaptor<OrganizationMember> member = ArgumentCaptor.forClass(OrganizationMember.class);
        verify(organizationMemberService).saveMember(member.capture());
        assertSame(agent, member.getValue().getUser());
        assertSame(organization, member.getValue().getOrganization());
        assertEquals(OrganizationMemberRole.MEMBER, member.getValue().getRole());
        assertEquals(OrganizationMemberStatus.ACTIVE, member.getValue().getStatus());

        verify(appAccessService).grant(actor, organizationId, new AppGrantCreateRequest(App.KAZAN, agent.getId(), null));
        verify(appAccessService).grant(actor, organizationId, new AppGrantCreateRequest(App.KALKAN, agent.getId(), null));
        verify(auditLogMapper).toRequest(eq(organizationId), eq(actor.getId()), eq("AGENT_CREATED"), eq("Agent"),
                eq(agent.getId()), isNull(), any());
        verify(auditService).log(any());
    }

    @ParameterizedTest
    @EnumSource(value = AgentProvider.class, names = {"OPENAI", "XAI", "DEEPSEEK"})
    void createAgent_routineWithProviderWithoutRoutine_throwsFlowProviderUnsupported(AgentProvider provider) {
        AgentCreateRequest request = new AgentCreateRequest("Agent", AgentFlow.ROUTINE, provider,
                AgentRole.SALES_ENGINEER, Set.of(App.KAZAN));

        ValidationException exception = assertThrows(ValidationException.class,
                () -> agentService.createAgent(actor, organizationId, request));

        assertEquals("AGT_VAL_003", exception.getErrorCode());
        verifyNoInteractions(userService, organizationMemberService, appAccessService, auditService);
    }

    @ParameterizedTest
    @EnumSource(AgentProvider.class)
    void createAgent_apiWithAnyProvider_createsAgent(AgentProvider provider) {
        AgentCreateRequest request = new AgentCreateRequest("Agent", AgentFlow.API, provider,
                AgentRole.SALES_ENGINEER, Set.of(App.KAZAN));
        User agent = agent(AgentFlow.API, provider);
        when(userService.createAgentUser(anyString(), eq("Agent"), any(UserAgentDetail.class))).thenReturn(agent);

        agentService.createAgent(actor, organizationId, request);

        verify(organizationMemberService).saveMember(any(OrganizationMember.class));
        verify(appAccessService).grant(actor, organizationId, new AppGrantCreateRequest(App.KAZAN, agent.getId(), null));
    }

    @Test
    void createAgent_nameTaken_throwsNameTaken() {
        AgentCreateRequest request = new AgentCreateRequest("Agent", AgentFlow.API, AgentProvider.XAI,
                AgentRole.SALES_ENGINEER, Set.of(App.KAZAN));
        when(userQueryService.existsAgentName(organizationId, "Agent", null)).thenReturn(true);

        ConflictException exception = assertThrows(ConflictException.class,
                () -> agentService.createAgent(actor, organizationId, request));

        assertEquals("AGT_STA_001", exception.getErrorCode());
        verifyNoInteractions(userService);
    }

    @Test
    void createAgent_nameIndexViolatedByParallelRequest_throwsNameTaken() {
        AgentCreateRequest request = new AgentCreateRequest("Agent", AgentFlow.API, AgentProvider.XAI,
                AgentRole.SALES_ENGINEER, Set.of(App.KAZAN));
        when(userService.createAgentUser(anyString(), eq("Agent"), any(UserAgentDetail.class)))
                .thenThrow(integrityViolation("users_agent_organization_id_first_name_uk"));

        ConflictException exception = assertThrows(ConflictException.class,
                () -> agentService.createAgent(actor, organizationId, request));

        assertEquals("AGT_STA_001", exception.getErrorCode());
        verifyNoInteractions(organizationMemberService, appAccessService);
    }

    @Test
    void createAgent_otherIntegrityViolation_rethrows() {
        AgentCreateRequest request = new AgentCreateRequest("Agent", AgentFlow.API, AgentProvider.XAI,
                AgentRole.SALES_ENGINEER, Set.of(App.KAZAN));
        DataIntegrityViolationException violation = integrityViolation("users_email_uk");
        when(userService.createAgentUser(anyString(), eq("Agent"), any(UserAgentDetail.class))).thenThrow(violation);

        DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                () -> agentService.createAgent(actor, organizationId, request));

        assertSame(violation, thrown);
    }

    // ---------- update ----------

    @Test
    void updateAgent_newApps_replacesUserGrantsAndKeepsFlowAndProvider() {
        User agent = agent(AgentFlow.ROUTINE, AgentProvider.ANTHROPIC);
        UUID kazanGrantId = UUID.randomUUID();
        UUID kalkanGrantId = UUID.randomUUID();
        when(agentQueryService.getAgentEntity(organizationId, agent.getId())).thenReturn(agent);
        when(appAccessQueryService.listGrants(actor, organizationId)).thenReturn(List.of(
                grant(kazanGrantId, App.KAZAN, agent.getId()),
                grant(kalkanGrantId, App.KALKAN, agent.getId()),
                grant(UUID.randomUUID(), App.OTAK, UUID.randomUUID())));
        AgentUpdateRequest request = new AgentUpdateRequest("Renamed", AgentRole.SALES_ENGINEER, Set.of(App.KAZAN, App.OTAK));

        agentService.updateAgent(actor, organizationId, agent.getId(), request);

        verify(userService).updateAgentUser(agent, "Renamed", AgentRole.SALES_ENGINEER, null);
        verify(appAccessService).revoke(actor, organizationId, kalkanGrantId);
        verify(appAccessService, never()).revoke(actor, organizationId, kazanGrantId);
        verify(appAccessService).grant(actor, organizationId, new AppGrantCreateRequest(App.OTAK, agent.getId(), null));
        verify(appAccessService, never()).grant(actor, organizationId, new AppGrantCreateRequest(App.KAZAN, agent.getId(), null));
        assertEquals(AgentFlow.ROUTINE, agent.getAgentDetail().getFlow());
        assertEquals(AgentProvider.ANTHROPIC, agent.getAgentDetail().getProvider());
    }

    @Test
    void updateAgent_nameOfAnotherAgent_throwsNameTaken() {
        User agent = agent(AgentFlow.API, AgentProvider.OPENAI);
        when(agentQueryService.getAgentEntity(organizationId, agent.getId())).thenReturn(agent);
        when(userQueryService.existsAgentName(organizationId, "Taken", agent.getId())).thenReturn(true);

        ConflictException exception = assertThrows(ConflictException.class, () -> agentService.updateAgent(actor,
                organizationId, agent.getId(), new AgentUpdateRequest("Taken", AgentRole.SALES_ENGINEER, Set.of(App.KAZAN))));

        assertEquals("AGT_STA_001", exception.getErrorCode());
        verify(userService, never()).updateAgentUser(any(), any(), any(), any());
    }

    @Test
    void updateAgent_unknownAgent_throwsNotFound() {
        UUID agentId = UUID.randomUUID();
        NotFoundException notFound = new NotFoundException();
        when(agentQueryService.getAgentEntity(organizationId, agentId)).thenThrow(notFound);

        assertSame(notFound, assertThrows(NotFoundException.class, () -> agentService.updateAgent(actor, organizationId,
                agentId, new AgentUpdateRequest("Name", AgentRole.SALES_ENGINEER, Set.of(App.KAZAN)))));
    }

    // ---------- status ----------

    @Test
    void updateStatus_disable_disablesUserAndMembership() {
        User agent = agent(AgentFlow.ROUTINE, AgentProvider.ANTHROPIC);
        OrganizationMember member = OrganizationMember.builder().organization(organization).user(agent)
                .status(OrganizationMemberStatus.ACTIVE).build();
        when(agentQueryService.getAgentEntity(organizationId, agent.getId())).thenReturn(agent);
        when(organizationMemberQueryService.findMember(organizationId, agent.getId())).thenReturn(Optional.of(member));

        agentService.updateStatus(actor, organizationId, agent.getId(),
                new AgentStatusUpdateRequest(OrganizationMemberStatus.DISABLED));

        verify(userService).updateAgentUser(agent, null, null, UserStatus.DISABLED);
        assertEquals(OrganizationMemberStatus.DISABLED, member.getStatus());
        verify(organizationMemberService).saveMember(member);
        verify(apiTokenService, never()).revoke(any(), any());
        verify(auditLogMapper).toRequest(eq(organizationId), eq(actor.getId()), eq("AGENT_STATUS_CHANGED"), eq("Agent"),
                eq(agent.getId()), any(), any());
    }

    @Test
    void updateStatus_sameStatus_changesNothing() {
        User agent = agent(AgentFlow.ROUTINE, AgentProvider.ANTHROPIC);
        OrganizationMember member = OrganizationMember.builder().organization(organization).user(agent)
                .status(OrganizationMemberStatus.ACTIVE).build();
        when(agentQueryService.getAgentEntity(organizationId, agent.getId())).thenReturn(agent);
        when(organizationMemberQueryService.findMember(organizationId, agent.getId())).thenReturn(Optional.of(member));

        agentService.updateStatus(actor, organizationId, agent.getId(),
                new AgentStatusUpdateRequest(OrganizationMemberStatus.ACTIVE));

        verifyNoInteractions(userService, organizationMemberService, auditService);
    }

    // ---------- delete ----------

    @Test
    void deleteAgent_withToken_revokesTokenRemovesAccessAndDeletesUser() {
        User agent = agent(AgentFlow.ROUTINE, AgentProvider.ANTHROPIC);
        ApiToken token = token(agent.getId());
        when(agentQueryService.getAgentEntity(organizationId, agent.getId())).thenReturn(agent);
        when(apiTokenService.revoke(ApiTokenKind.AGENT, agent.getId())).thenReturn(Optional.of(token));

        agentService.deleteAgent(actor, organizationId, agent.getId());

        verify(appAccessService).removeMemberAccess(actor, organizationId, agent.getId());
        verify(userService).deleteAgentUser(agent);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> newValues = ArgumentCaptor.forClass(Map.class);
        verify(auditLogMapper).toRequest(eq(organizationId), eq(actor.getId()), eq("AGENT_DELETED"), eq("Agent"),
                eq(agent.getId()), any(), newValues.capture());
        assertEquals(token.getId().toString(), newValues.getValue().get("revokedTokenId"));
    }

    // ---------- token ----------

    @Test
    void issueToken_routineAgent_issuesTokenAndAuditsWithoutPlaintext() {
        User agent = agent(AgentFlow.ROUTINE, AgentProvider.ANTHROPIC);
        ApiToken token = token(agent.getId());
        UUID revokedTokenId = UUID.randomUUID();
        when(agentQueryService.getAgentEntity(organizationId, agent.getId())).thenReturn(agent);
        when(apiTokenService.issue(ApiTokenKind.AGENT, agent.getId(), agent, organizationId))
                .thenReturn(new IssuedApiToken(token, PLAINTEXT, revokedTokenId));
        when(auditLogMapper.toRequest(any(), any(), any(), any(), any(), any(), any())).thenReturn(mock(AuditLogRequest.class));

        AgentTokenCreatedResponse response = agentService.issueToken(actor, organizationId, agent.getId());

        assertEquals(agent.getId(), response.agentId());
        assertEquals(PLAINTEXT, response.token());
        assertEquals(token.getTokenPrefix(), response.tokenPrefix());
        assertEquals(token.getExpiresAt(), response.expiresAt());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> newValues = ArgumentCaptor.forClass(Map.class);
        verify(auditLogMapper).toRequest(eq(organizationId), eq(actor.getId()), eq("AGENT_TOKEN_ISSUED"), eq("Agent"),
                eq(agent.getId()), isNull(), newValues.capture());
        assertEquals(token.getId().toString(), newValues.getValue().get("tokenId"));
        assertEquals(revokedTokenId.toString(), newValues.getValue().get("revokedTokenId"));
        assertFalse(newValues.getValue().containsValue(PLAINTEXT));
    }

    @ParameterizedTest
    @EnumSource(AgentProvider.class)
    void issueToken_apiAgent_throwsFlowProviderUnsupported(AgentProvider provider) {
        User agent = agent(AgentFlow.API, provider);
        when(agentQueryService.getAgentEntity(organizationId, agent.getId())).thenReturn(agent);

        ValidationException exception = assertThrows(ValidationException.class,
                () -> agentService.issueToken(actor, organizationId, agent.getId()));

        assertEquals("AGT_VAL_003", exception.getErrorCode());
        verifyNoInteractions(apiTokenService, auditService);
    }

    @Test
    void revokeToken_liveToken_revokesAndAudits() {
        User agent = agent(AgentFlow.ROUTINE, AgentProvider.ANTHROPIC);
        ApiToken token = token(agent.getId());
        when(agentQueryService.getAgentEntity(organizationId, agent.getId())).thenReturn(agent);
        when(apiTokenService.revoke(ApiTokenKind.AGENT, agent.getId())).thenReturn(Optional.of(token));

        agentService.revokeToken(actor, organizationId, agent.getId());

        verify(auditLogMapper).toRequest(eq(organizationId), eq(actor.getId()), eq("AGENT_TOKEN_REVOKED"), eq("Agent"),
                eq(agent.getId()), any(), isNull());
        verify(auditService).log(any());
    }

    @Test
    void revokeToken_noLiveToken_auditsNothing() {
        User agent = agent(AgentFlow.ROUTINE, AgentProvider.ANTHROPIC);
        when(agentQueryService.getAgentEntity(organizationId, agent.getId())).thenReturn(agent);
        when(apiTokenService.revoke(ApiTokenKind.AGENT, agent.getId())).thenReturn(Optional.empty());

        agentService.revokeToken(actor, organizationId, agent.getId());

        verifyNoInteractions(auditService);
    }

    // ---------- fixtures ----------

    private User agent(AgentFlow flow, AgentProvider provider) {
        return User.builder()
                .id(UUID.randomUUID())
                .email("agent-x@agents.oba.invalid")
                .status(UserStatus.ACTIVE)
                .kind(UserKind.AGENT)
                .defaultOrganization(organization)
                .profile(UserProfile.builder().firstName("Agent").build())
                .agentDetail(UserAgentDetail.builder()
                        .role(AgentRole.CYBERSECURITY_ENGINEER)
                        .flow(flow)
                        .provider(provider)
                        .organizationId(organizationId)
                        .createdByUserId(actor.getId())
                        .build())
                .build();
    }

    private ApiToken token(UUID agentId) {
        return ApiToken.builder()
                .id(UUID.randomUUID())
                .kind(ApiTokenKind.AGENT)
                .subjectId(agentId)
                .userId(agentId)
                .organizationId(organizationId)
                .tokenPrefix("oat_secretse")
                .expiresAt(Instant.parse("2027-11-24T10:15:30Z"))
                .build();
    }

    private static AgentResponse response(UUID agentId) {
        return new AgentResponse(agentId, "Pentest agent", AgentFlow.ROUTINE, AgentProvider.ANTHROPIC,
                AgentRole.CYBERSECURITY_ENGINEER, List.of(App.KAZAN), UserStatus.ACTIVE,
                null, null, null, null, null, null, null, null);
    }

    private static AppGrantResponse grant(UUID grantId, App app, UUID userId) {
        return new AppGrantResponse(grantId, app, AppGrantTargetType.USER, userId, "Agent", null, null, null, Instant.now());
    }

    private static DataIntegrityViolationException integrityViolation(String constraintName) {
        return new DataIntegrityViolationException("violation",
                new ConstraintViolationException("violation", new SQLException("duplicate"), constraintName));
    }
}
