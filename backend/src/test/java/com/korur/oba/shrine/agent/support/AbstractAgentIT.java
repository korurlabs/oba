package com.korur.oba.shrine.agent.support;

// 1. Framework-level
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractTenantIT;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.entity.embedded.UserAgentDetail;
import com.korur.oba.shrine.identity.entity.embedded.UserProfile;
import com.korur.oba.shrine.identity.enums.AgentFlow;
import com.korur.oba.shrine.identity.enums.AgentProvider;
import com.korur.oba.shrine.identity.enums.AgentRole;
import com.korur.oba.shrine.identity.enums.UserKind;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base of the agent integration tests: an organization per test, agents created through the real
 * endpoints as the OWNER, and their tokens issued through the token endpoint.
 */
public abstract class AbstractAgentIT extends AbstractTenantIT {

    protected static final String ROUTINE_ID = "trig_01AbCdEfGh";

    protected RequestPostProcessor ownerJwt(Tenant tenant) {
        return jwtOf(tenant.owner().getUser());
    }

    protected static String createBody(String name, String flow, String provider, String apps) {
        return "{\"name\":\"" + name + "\",\"flow\":\"" + flow + "\",\"provider\":\"" + provider
                + "\",\"role\":\"CYBERSECURITY_ENGINEER\",\"apps\":[" + apps + "]}";
    }

    /**
     * Creates an agent the way the create endpoint does (agent user, ACTIVE MEMBER membership, one USER grant
     * per app) but through the repository, so the tests of the other endpoints do not depend on the name
     * check of the create endpoint. Returns the agent id.
     */
    protected String createAgent(Tenant tenant, String name, AgentFlow flow, AgentProvider provider, String... apps) {
        User agent = insertAgent(tenant, name, flow, provider);
        createMember(tenant.organization(), agent, OrganizationMemberRole.MEMBER, OrganizationMemberStatus.ACTIVE);
        for (String app : apps) {
            jdbcTemplate.update("INSERT INTO shrine.organization_app_grants (organization_id, app, user_id) "
                    + "VALUES (?, ?::shrine_app_grant_app_enum, ?)", tenant.organization().getId(), app, agent.getId());
        }
        return agent.getId().toString();
    }

    /** Issues the token of an agent as the OWNER and returns the plaintext, shown once. */
    protected String issueToken(Tenant tenant, String agentId) throws Exception {
        return read(mockMvc.perform(in(tenant, post("/organizations/" + tenant.id() + "/agents/" + agentId + "/token"),
                        ownerJwt(tenant), null))
                .andExpect(status().isCreated()).andReturn(), "$.data.token");
    }

    protected static String uniqueAgentName() {
        return "agent " + uniqueSuffix();
    }

    /** An agent row inserted through the repository, without membership or grants. */
    protected User insertAgent(Tenant tenant, String name) {
        return insertAgent(tenant, name, AgentFlow.API, AgentProvider.OPENAI);
    }

    protected User insertAgent(Tenant tenant, String name, AgentFlow flow, AgentProvider provider) {
        String suffix = uniqueSuffix();
        return userRepository.saveAndFlush(User.builder()
                .email("agent-" + suffix + "@agents.oba.invalid")
                .status(UserStatus.ACTIVE)
                .kind(UserKind.AGENT)
                .profile(UserProfile.builder().firstName(name).build())
                .agentDetail(UserAgentDetail.builder()
                        .role(AgentRole.CYBERSECURITY_ENGINEER)
                        .flow(flow)
                        .provider(provider)
                        .organizationId(tenant.organization().getId())
                        .build())
                .build());
    }
}
