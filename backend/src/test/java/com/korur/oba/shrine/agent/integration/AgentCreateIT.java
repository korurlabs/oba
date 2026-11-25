package com.korur.oba.shrine.agent.integration;

// 1. Framework-level
import org.junit.jupiter.api.Test;

// 4. Internal
import com.korur.oba.shrine.agent.support.AbstractAgentIT;
import com.korur.oba.shrine.identity.enums.AgentFlow;
import com.korur.oba.shrine.identity.enums.AgentProvider;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The create, rename and delete endpoints against the real schema: the name check, the unique index behind
 * it, the options listing and the refusal of every flow and provider pair the connectors do not serve.
 */
class AgentCreateIT extends AbstractAgentIT {

    private String agentsPath(Tenant tenant) {
        return "/organizations/" + tenant.id() + "/agents";
    }

    @Test
    void createAgent_routineWithAnthropic_createsUserMembershipAndGrants() throws Exception {
        Tenant tenant = newTenant();
        String name = uniqueAgentName();

        String agentId = read(mockMvc.perform(in(tenant, post(agentsPath(tenant)), ownerJwt(tenant),
                        createBody(name, "ROUTINE", "ANTHROPIC", "\"KAZAN\",\"KAYA\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.flow").value("ROUTINE"))
                .andExpect(jsonPath("$.data.provider").value("ANTHROPIC"))
                .andReturn(), "$.data.id");

        assertEquals(1L, count("SELECT count(*) FROM shrine.users WHERE id = ?::uuid AND kind = 'AGENT' "
                + "AND agent_organization_id = ?::uuid", agentId, tenant.id()));
        assertEquals(1L, count("SELECT count(*) FROM shrine.organization_members WHERE user_id = ?::uuid "
                + "AND organization_id = ?::uuid AND role = 'MEMBER' AND status = 'ACTIVE'", agentId, tenant.id()));
        assertEquals(2L, count("SELECT count(*) FROM shrine.organization_app_grants WHERE user_id = ?::uuid "
                + "AND deleted = FALSE", agentId));
    }

    @Test
    void createAgent_secondAgentWithSameNameIgnoringCase_answers409NameTaken() throws Exception {
        Tenant tenant = newTenant();
        String name = uniqueAgentName();
        mockMvc.perform(in(tenant, post(agentsPath(tenant)), ownerJwt(tenant),
                createBody(name, "API", "OPENAI", "\"KAZAN\""))).andExpect(status().isCreated());

        mockMvc.perform(in(tenant, post(agentsPath(tenant)), ownerJwt(tenant),
                        createBody(name.toUpperCase(), "API", "XAI", "\"KAZAN\"")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AGT_STA_001"));
    }

    @Test
    void createAgent_routineWithOtherProviders_answers422FlowProviderUnsupported() throws Exception {
        Tenant tenant = newTenant();

        for (String provider : new String[] {"OPENAI", "XAI", "DEEPSEEK"}) {
            mockMvc.perform(in(tenant, post(agentsPath(tenant)), ownerJwt(tenant),
                            createBody(uniqueAgentName(), "ROUTINE", provider, "\"KAZAN\"")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("AGT_VAL_003"));
        }
    }

    @Test
    void createAgent_apiWithEachProvider_succeeds() throws Exception {
        Tenant tenant = newTenant();

        for (String provider : new String[] {"ANTHROPIC", "OPENAI", "XAI", "DEEPSEEK"}) {
            mockMvc.perform(in(tenant, post(agentsPath(tenant)), ownerJwt(tenant),
                            createBody(uniqueAgentName(), "API", provider, "\"KAZAN\"")))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void createAgent_byMember_answers403() throws Exception {
        Tenant tenant = newTenant();

        mockMvc.perform(in(tenant, post(agentsPath(tenant)), jwtOf(tenant.member().getUser()),
                        createBody(uniqueAgentName(), "API", "OPENAI", "\"KAZAN\"")))
                .andExpect(status().isForbidden());
    }

    @Test
    void renameAgent_toNameOfAnotherAgent_answers409AndToItsOwnNameSucceeds() throws Exception {
        Tenant tenant = newTenant();
        String first = uniqueAgentName();
        String firstId = read(mockMvc.perform(in(tenant, post(agentsPath(tenant)), ownerJwt(tenant),
                createBody(first, "API", "OPENAI", "\"KAZAN\""))).andExpect(status().isCreated()).andReturn(), "$.data.id");
        String secondId = read(mockMvc.perform(in(tenant, post(agentsPath(tenant)), ownerJwt(tenant),
                createBody(uniqueAgentName(), "API", "OPENAI", "\"KAZAN\""))).andExpect(status().isCreated()).andReturn(),
                "$.data.id");

        mockMvc.perform(in(tenant, put(agentsPath(tenant) + "/" + secondId), ownerJwt(tenant),
                        "{\"name\":\"" + first + "\",\"role\":\"SALES_ENGINEER\",\"apps\":[\"KAZAN\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AGT_STA_001"));
        mockMvc.perform(in(tenant, put(agentsPath(tenant) + "/" + firstId), ownerJwt(tenant),
                        "{\"name\":\"" + first + "\",\"role\":\"SALES_ENGINEER\",\"apps\":[\"KAZAN\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.flow").value("API"))
                .andExpect(jsonPath("$.data.provider").value("OPENAI"));
    }

    @Test
    void deleteAgent_freesTheNameAndRemovesTheAgentFromTheList() throws Exception {
        Tenant tenant = newTenant();
        String name = uniqueAgentName();
        String agentId = read(mockMvc.perform(in(tenant, post(agentsPath(tenant)), ownerJwt(tenant),
                createBody(name, "API", "OPENAI", "\"KAZAN\""))).andExpect(status().isCreated()).andReturn(), "$.data.id");

        mockMvc.perform(in(tenant, delete(agentsPath(tenant) + "/" + agentId), ownerJwt(tenant), null))
                .andExpect(status().isOk());

        mockMvc.perform(in(tenant, get(agentsPath(tenant)), ownerJwt(tenant), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(in(tenant, post(agentsPath(tenant)), ownerJwt(tenant),
                createBody(name, "API", "OPENAI", "\"KAZAN\""))).andExpect(status().isCreated());
    }

    @Test
    void options_listsAnthropicForRoutineAndAllFourForApi() throws Exception {
        Tenant tenant = newTenant();

        mockMvc.perform(in(tenant, get(agentsPath(tenant) + "/options"), ownerJwt(tenant), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.flows[?(@.flow == 'ROUTINE')].providers", contains(contains("ANTHROPIC"))))
                .andExpect(jsonPath("$.data.flows[?(@.flow == 'API')].providers", contains(hasSize(4))));
    }

    @Test
    void getAgents_ofAnotherOrganization_isNotVisible() throws Exception {
        Tenant tenant = newTenant();
        createAgent(newTenant(), uniqueAgentName(), AgentFlow.API, AgentProvider.OPENAI, "KAZAN");

        mockMvc.perform(in(tenant, get(agentsPath(tenant)), ownerJwt(tenant), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
