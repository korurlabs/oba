package com.korur.oba.shrine.agent.integration;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// 3. Java SDK
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.access.dto.projection.OrganizationAppGrantProjection;
import com.korur.oba.shrine.access.entity.OrganizationGroup;
import com.korur.oba.shrine.access.enums.OrganizationGroupType;
import com.korur.oba.shrine.access.repository.AppGrantRepository;
import com.korur.oba.shrine.access.repository.OrganizationGroupRepository;
import com.korur.oba.shrine.access.service.OrganizationGroupQueryService;
import com.korur.oba.shrine.agent.support.AbstractAgentIT;
import com.korur.oba.shrine.identity.enums.AgentFlow;
import com.korur.oba.shrine.identity.enums.AgentProvider;
import com.korur.oba.shrine.organization.dto.projection.OrganizationMemberKindSummaryProjection;
import com.korur.oba.shrine.organization.repository.OrganizationMemberRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Agents hold only what an agent is granted: the Everyone group never reaches them, in the grant SQL, in the
 * group's user ids and over HTTP, and the people-management views leave them out while the agent listing and
 * the member summaries still name them with their kind.
 */
class AgentAccessIT extends AbstractAgentIT {

    private static final List<String> ALL_ROLES = List.of("OWNER", "ADMIN", "MEMBER", "GUEST");

    @Autowired
    private AppGrantRepository appGrantRepository;

    @Autowired
    private OrganizationGroupRepository organizationGroupRepository;

    @Autowired
    private OrganizationGroupQueryService organizationGroupQueryService;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    private String routineAgent(Tenant tenant) {
        return createAgent(tenant, uniqueAgentName(), AgentFlow.ROUTINE, AgentProvider.ANTHROPIC, "KAZAN");
    }

    @Test
    void existsGrantForUser_everyoneGrant_reachesPeopleButNotAgents() {
        Tenant tenant = newTenant();
        UUID agentId = UUID.fromString(routineAgent(tenant));
        UUID memberId = tenant.member().getUser().getId();
        UUID organizationId = tenant.organization().getId();

        assertTrue(appGrantRepository.existsGrantForUserByAppName(organizationId, memberId, "KALKAN"));
        assertFalse(appGrantRepository.existsGrantForUserByAppName(organizationId, agentId, "KALKAN"));
        assertTrue(appGrantRepository.existsGrantForUserByAppName(organizationId, agentId, "KAZAN"),
                "A direct grant still reaches the agent");
    }

    @Test
    void findUserIdsWithApp_everyoneGrant_leavesTheAgentOut() {
        Tenant tenant = newTenant();
        UUID agentId = UUID.fromString(routineAgent(tenant));
        UUID memberId = tenant.member().getUser().getId();
        UUID organizationId = tenant.organization().getId();

        List<UUID> kalkan = appGrantRepository.findUserIdsWithAppByAppName(
                organizationId, "KALKAN", ALL_ROLES, List.of(agentId, memberId));
        List<UUID> kazan = appGrantRepository.findUserIdsWithAppByAppName(
                organizationId, "KAZAN", ALL_ROLES, List.of(agentId, memberId));

        assertEquals(List.of(memberId), kalkan);
        assertEquals(Set.of(agentId, memberId), new HashSet<>(kazan));
    }

    @Test
    void findGrantedAppsOfUser_everyoneGrant_leavesTheAgentWithItsDirectApps() {
        Tenant tenant = newTenant();
        UUID agentId = UUID.fromString(routineAgent(tenant));
        UUID memberId = tenant.member().getUser().getId();
        List<UUID> organizations = List.of(tenant.organization().getId());

        Set<String> agentApps = new HashSet<>();
        for (OrganizationAppGrantProjection grant : appGrantRepository.findGrantedAppsOfUser(agentId, organizations)) {
            agentApps.add(grant.getApp());
        }
        Set<String> memberApps = new HashSet<>();
        for (OrganizationAppGrantProjection grant : appGrantRepository.findGrantedAppsOfUser(memberId, organizations)) {
            memberApps.add(grant.getApp());
        }

        assertEquals(Set.of("KAZAN"), agentApps);
        assertTrue(memberApps.contains("KALKAN"));
        assertTrue(memberApps.size() > 1);
    }

    @Test
    void findGroupUserIds_everyoneGroup_listsPeopleOnly() {
        Tenant tenant = newTenant();
        UUID agentId = UUID.fromString(routineAgent(tenant));
        OrganizationGroup everyone = organizationGroupRepository
                .findFirstByOrganization_IdAndType(tenant.organization().getId(), OrganizationGroupType.EVERYONE)
                .orElseThrow();

        Set<UUID> userIds = organizationGroupQueryService.findGroupUserIds(everyone);

        assertFalse(userIds.contains(agentId));
        assertTrue(userIds.contains(tenant.member().getUser().getId()));
    }

    @Test
    void findActiveHumanUserIdsByRoles_leavesAgentsOutWhileTheSummariesNameThemWithTheirKind() {
        Tenant tenant = newTenant();
        UUID agentId = UUID.fromString(routineAgent(tenant));
        UUID organizationId = tenant.organization().getId();

        List<UUID> humans = organizationMemberRepository.findActiveHumanUserIdsByRoles(organizationId, ALL_ROLES);
        List<OrganizationMemberKindSummaryProjection> summaries = organizationMemberRepository
                .findActiveMemberSummaries(organizationId, Instant.now().plusSeconds(60));

        assertFalse(humans.contains(agentId));
        assertEquals(3, humans.size());
        List<String> kinds = new ArrayList<>();
        OrganizationMemberKindSummaryProjection agentRow = null;
        for (OrganizationMemberKindSummaryProjection summary : summaries) {
            kinds.add(summary.getKind());
            if (summary.getUserId().equals(agentId)) {
                agentRow = summary;
            }
        }
        assertEquals(4, summaries.size());
        assertEquals(3, kinds.stream().filter("HUMAN"::equals).count());
        assertNotNull(agentRow);
        assertEquals("AGENT", agentRow.getKind());
        assertEquals("CYBERSECURITY_ENGINEER", agentRow.getAgentRole());
        assertEquals("ROUTINE", agentRow.getAgentFlow());
        assertEquals("ANTHROPIC", agentRow.getAgentProvider());
    }

    @Test
    void memberList_hidesAgentsAndAgentListShowsThem() throws Exception {
        Tenant tenant = newTenant();
        String agentId = routineAgent(tenant);

        mockMvc.perform(in(tenant, get("/organizations/" + tenant.id() + "/members"), ownerJwt(tenant), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[?(@.userId == '" + agentId + "')]").isEmpty())
                .andExpect(jsonPath("$.data[*].kind").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("HUMAN"))));
        mockMvc.perform(in(tenant, get("/organizations/" + tenant.id() + "/agents"), ownerJwt(tenant), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(agentId));
    }

    @Test
    void boardMembers_listAgentWithKind() throws Exception {
        Tenant tenant = newTenant();
        String agentId = routineAgent(tenant);
        String prefix = "A" + uniqueSuffix().substring(0, 8).toUpperCase();
        String boardId = read(mockMvc.perform(in(tenant, post("/kazan/boards"), ownerJwt(tenant),
                        "{\"name\":\"Board " + prefix + "\",\"prefix\":\"" + prefix + "\"}"))
                .andExpect(status().is2xxSuccessful()).andReturn(), "$.data.id");

        mockMvc.perform(in(tenant, post("/kazan/boards/" + boardId + "/members"), ownerJwt(tenant),
                        "{\"userId\":\"" + agentId + "\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.kind").value("AGENT"))
                .andExpect(jsonPath("$.data.agentFlow").value("ROUTINE"))
                .andExpect(jsonPath("$.data.agentProvider").value("ANTHROPIC"));
    }

    @Test
    void addToCustomGroup_agent_answers422AgentTargetRefused() throws Exception {
        Tenant tenant = newTenant();
        String agentId = routineAgent(tenant);
        String groupId = read(mockMvc.perform(in(tenant, post("/organizations/" + tenant.id() + "/groups"),
                        ownerJwt(tenant), "{\"name\":\"Team " + uniqueSuffix() + "\"}"))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");

        mockMvc.perform(in(tenant, post("/organizations/" + tenant.id() + "/groups/" + groupId + "/members"),
                        ownerJwt(tenant), "{\"userIds\":[\"" + agentId + "\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("AGT_VAL_001"));

        assertEquals(0L, count("SELECT count(*) FROM shrine.organization_group_members WHERE group_id = ?::uuid",
                groupId));
    }

    @Test
    void agentToken_onAppOnlyTheEveryoneGroupGrants_answersAppAccessDenied() throws Exception {
        Tenant tenant = newTenant();
        String token = issueToken(tenant, routineAgent(tenant));

        mockMvc.perform(get("/kalkan/pages").header("Authorization", "Bearer " + token)
                        .header("X-Organization-ID", tenant.id()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("APP_SEC_001"));
        mockMvc.perform(get("/kazan/boards").header("Authorization", "Bearer " + token)
                        .header("X-Organization-ID", tenant.id()))
                .andExpect(status().isOk());
    }
}
