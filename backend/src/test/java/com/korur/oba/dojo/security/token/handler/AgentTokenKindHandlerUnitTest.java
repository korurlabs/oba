package com.korur.oba.dojo.security.token.handler;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.auth.UnauthorizedException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.token.dto.ApiTokenEndpoint;
import com.korur.oba.dojo.security.token.dto.ApiTokenPrincipal;
import com.korur.oba.dojo.security.token.dto.UserApiTokenPrincipal;
import com.korur.oba.dojo.security.token.entity.ApiToken;
import com.korur.oba.dojo.security.token.enums.ApiTokenKind;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.entity.embedded.UserAgentDetail;
import com.korur.oba.shrine.identity.enums.AgentFlow;
import com.korur.oba.shrine.identity.enums.AgentProvider;
import com.korur.oba.shrine.identity.enums.AgentRole;
import com.korur.oba.shrine.identity.enums.UserKind;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.identity.service.UserQueryService;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentTokenKindHandlerUnitTest {

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;

    @Mock
    private LoggingService loggingService;

    private AgentTokenKindHandler handler;
    private UUID organizationId;
    private UUID agentId;
    private ApiToken token;
    private User agent;
    private Organization organization;
    private OrganizationMember member;

    @BeforeEach
    void setUp() {
        handler = new AgentTokenKindHandler(userQueryService, organizationMemberQueryService, loggingService);
        organizationId = UUID.randomUUID();
        agentId = UUID.randomUUID();
        token = ApiToken.builder()
                .id(UUID.randomUUID())
                .kind(ApiTokenKind.AGENT)
                .subjectId(agentId)
                .userId(agentId)
                .organizationId(organizationId)
                .build();
        agent = User.builder()
                .id(agentId)
                .email("agent-x@agents.oba.invalid")
                .status(UserStatus.ACTIVE)
                .kind(UserKind.AGENT)
                .agentDetail(UserAgentDetail.builder()
                        .role(AgentRole.CYBERSECURITY_ENGINEER)
                        .flow(AgentFlow.ROUTINE)
                        .provider(AgentProvider.ANTHROPIC)
                        .organizationId(organizationId)
                        .build())
                .build();
        organization = Organization.builder().id(organizationId).status(OrganizationStatus.ACTIVE).build();
        member = OrganizationMember.builder()
                .organization(organization)
                .user(agent)
                .status(OrganizationMemberStatus.ACTIVE)
                .build();
    }

    @Test
    void kindAndRules_agentToken_reservesNothingAndExemptsOnlyGetUsersMe() {
        assertEquals(ApiTokenKind.AGENT, handler.kind());
        assertTrue(handler.reservedPaths().isEmpty());
        assertEquals(List.of(new ApiTokenEndpoint(HttpMethod.GET, "/users/me")), handler.exemptEndpoints());
        assertTrue(handler.bodyLimit().isEmpty());
        assertEquals("AGT_SEC_001", handler.invalidTokenError().getCode());
    }

    @Test
    void resolvePrincipal_activeAgent_returnsUserPrincipal() {
        when(userQueryService.getUserEntitiesByIds(List.of(agentId))).thenReturn(List.of(agent));
        when(organizationMemberQueryService.findMember(organizationId, agentId)).thenReturn(Optional.of(member));

        ApiTokenPrincipal principal = handler.resolvePrincipal(token);

        assertEquals(new UserApiTokenPrincipal(token.getId(), agentId, organizationId), principal);
    }

    @Test
    void resolvePrincipal_userMissing_throwsAgentTokenInvalid() {
        when(userQueryService.getUserEntitiesByIds(List.of(agentId))).thenReturn(List.of());

        assertRefused();
    }

    @Test
    void resolvePrincipal_ownerDiffersFromSubject_throwsAgentTokenInvalid() {
        token.setUserId(UUID.randomUUID());

        assertRefused();
    }

    @Test
    void resolvePrincipal_person_throwsAgentTokenInvalid() {
        agent.setKind(UserKind.HUMAN);
        agent.setAgentDetail(null);
        when(userQueryService.getUserEntitiesByIds(List.of(agentId))).thenReturn(List.of(agent));

        assertRefused();
    }

    @Test
    void resolvePrincipal_agentOfAnotherOrganization_throwsAgentTokenInvalid() {
        agent.getAgentDetail().setOrganizationId(UUID.randomUUID());
        when(userQueryService.getUserEntitiesByIds(List.of(agentId))).thenReturn(List.of(agent));

        assertRefused();
    }

    @Test
    void resolvePrincipal_disabledAgent_throwsAgentTokenInvalid() {
        agent.setStatus(UserStatus.DISABLED);
        when(userQueryService.getUserEntitiesByIds(List.of(agentId))).thenReturn(List.of(agent));

        assertRefused();
    }

    @Test
    void resolvePrincipal_membershipDisabled_throwsAgentTokenInvalid() {
        member.setStatus(OrganizationMemberStatus.DISABLED);
        when(userQueryService.getUserEntitiesByIds(List.of(agentId))).thenReturn(List.of(agent));
        when(organizationMemberQueryService.findMember(organizationId, agentId)).thenReturn(Optional.of(member));

        assertRefused();
    }

    @Test
    void resolvePrincipal_membershipMissing_throwsAgentTokenInvalid() {
        when(userQueryService.getUserEntitiesByIds(List.of(agentId))).thenReturn(List.of(agent));
        when(organizationMemberQueryService.findMember(organizationId, agentId)).thenReturn(Optional.empty());

        assertRefused();
    }

    @Test
    void resolvePrincipal_organizationDisabled_throwsAgentTokenInvalid() {
        organization.setStatus(OrganizationStatus.DISABLED);
        when(userQueryService.getUserEntitiesByIds(List.of(agentId))).thenReturn(List.of(agent));
        when(organizationMemberQueryService.findMember(organizationId, agentId)).thenReturn(Optional.of(member));

        assertRefused();
    }

    private void assertRefused() {
        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () -> handler.resolvePrincipal(token));
        assertEquals("AGT_SEC_001", exception.getErrorCode());
    }
}
