package com.korur.oba.shrine.agent.integration;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 4. Internal
import com.korur.oba.shrine.agent.support.AbstractAgentIT;
import com.korur.oba.shrine.identity.enums.AgentFlow;
import com.korur.oba.shrine.identity.enums.AgentProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An {@code oat_} agent token end to end: issued by an administrator, accepted on the endpoints an agent may
 * call, refused on the paths reserved for another token kind and on the handlers a person must call, and
 * refused again once the agent is disabled, its token revoked or the agent deleted.
 */
class AgentTokenIT extends AbstractAgentIT {

    private static final String AGENT_TOKEN_INVALID = "AGT_SEC_001";
    private static final String AGENT_ACTION_REFUSED = "AGT_SEC_002";

    private static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    private String routineAgent(Tenant tenant) {
        return createAgent(tenant, uniqueAgentName(), AgentFlow.ROUTINE, AgentProvider.ANTHROPIC, "KAZAN", "KAYA");
    }

    @Test
    void issueToken_returnsPlaintextOnceWithPrefixAndStoresOnlyItsHash() throws Exception {
        Tenant tenant = newTenant();
        String agentId = routineAgent(tenant);

        String token = issueToken(tenant, agentId);

        assertTrue(token.matches("^oat_[A-Za-z0-9_-]{43}$"), "token format: " + token);
        assertEquals(1L, count("SELECT count(*) FROM dojo.api_tokens WHERE kind = 'AGENT' AND subject_id = ?::uuid "
                + "AND token_hash <> ? AND token_prefix = ?", agentId, token, token.substring(0, 12)));
        mockMvc.perform(in(tenant, get("/organizations/" + tenant.id() + "/agents"), ownerJwt(tenant), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + agentId + "')].tokenPrefix").value(token.substring(0, 12)))
                .andExpect(jsonPath("$.data[*].token").doesNotExist());
    }

    @Test
    void token_onCurrentUserEndpoint_answers200() throws Exception {
        Tenant tenant = newTenant();
        String agentId = routineAgent(tenant);
        String token = issueToken(tenant, agentId);

        mockMvc.perform(bearer(get("/users/me"), token))
                .andExpect(status().isOk());
    }

    @Test
    void token_onAnnotatedMemberEndpoint_actsInItsOwnOrganizationAndNeverListsAgents() throws Exception {
        Tenant tenant = newTenant();
        String agentId = routineAgent(tenant);
        String token = issueToken(tenant, agentId);

        mockMvc.perform(bearer(get("/organizations/" + tenant.id() + "/members"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].userId").isNotEmpty())
                .andExpect(jsonPath("$.data[?(@.userId == '" + agentId + "')]").isEmpty())
                .andExpect(jsonPath("$.data[?(@.userId == '" + tenant.owner().getUser().getId() + "')]").isNotEmpty());
    }

    @Test
    void token_namingAnotherOrganization_isRefused() throws Exception {
        Tenant tenant = newTenant();
        Tenant other = newTenant();
        String token = issueToken(tenant, routineAgent(tenant));

        mockMvc.perform(bearer(get("/organizations/" + other.id() + "/members"), token))
                .andExpect(status().isForbidden());
    }

    @Test
    void token_onHandlerAboveTheMemberRole_answers403() throws Exception {
        Tenant tenant = newTenant();
        String token = issueToken(tenant, routineAgent(tenant));

        mockMvc.perform(bearer(get("/organizations/" + tenant.id() + "/agents"), token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ORG_SEC_003"));
    }

    @Test
    void token_onHumanOnlyHandler_isRefusedWithAgentActionRefused() throws Exception {
        Tenant tenant = newTenant();
        String token = issueToken(tenant, routineAgent(tenant));

        mockMvc.perform(bearer(post("/kaya/machines"), token)
                        .header("X-Organization-ID", tenant.id())
                        .contentType("application/json").content("{\"machineName\":\"laptop\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(AGENT_ACTION_REFUSED));
    }

    @Test
    void token_onUnannotatedHandlerOutsideItsExemptList_answers403() throws Exception {
        Tenant tenant = newTenant();
        String token = issueToken(tenant, routineAgent(tenant));

        mockMvc.perform(bearer(post("/users/me"), token))
                .andExpect(status().isForbidden());
        mockMvc.perform(bearer(delete("/users/me"), token))
                .andExpect(status().isForbidden());
    }

    @Test
    void token_onPathReservedForKayaMachineTokens_answers401() throws Exception {
        Tenant tenant = newTenant();
        String token = issueToken(tenant, routineAgent(tenant));

        mockMvc.perform(bearer(post("/kaya/ingest/batches"), token)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void token_ofDisabledAgent_answers401AndWorksAgainWhenEnabled() throws Exception {
        Tenant tenant = newTenant();
        String agentId = routineAgent(tenant);
        String token = issueToken(tenant, agentId);
        mockMvc.perform(bearer(get("/users/me"), token)).andExpect(status().isOk());

        mockMvc.perform(in(tenant, patch("/organizations/" + tenant.id() + "/agents/" + agentId + "/status"),
                        ownerJwt(tenant), "{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(bearer(get("/users/me"), token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(AGENT_TOKEN_INVALID));
        mockMvc.perform(in(tenant, patch("/organizations/" + tenant.id() + "/agents/" + agentId + "/status"),
                        ownerJwt(tenant), "{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(bearer(get("/users/me"), token)).andExpect(status().isOk());
    }

    @Test
    void token_afterRevoke_answers401() throws Exception {
        Tenant tenant = newTenant();
        String agentId = routineAgent(tenant);
        String token = issueToken(tenant, agentId);

        mockMvc.perform(in(tenant, delete("/organizations/" + tenant.id() + "/agents/" + agentId + "/token"),
                        ownerJwt(tenant), null))
                .andExpect(status().isOk());

        mockMvc.perform(bearer(get("/users/me"), token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(AGENT_TOKEN_INVALID));
    }

    @Test
    void reissue_revokesTheOldTokenAndTheNewOneWorks() throws Exception {
        Tenant tenant = newTenant();
        String agentId = routineAgent(tenant);
        String first = issueToken(tenant, agentId);

        String second = issueToken(tenant, agentId);

        mockMvc.perform(bearer(get("/users/me"), first)).andExpect(status().isUnauthorized());
        mockMvc.perform(bearer(get("/users/me"), second)).andExpect(status().isOk());
    }

    @Test
    void token_afterAgentDelete_answers401() throws Exception {
        Tenant tenant = newTenant();
        String agentId = routineAgent(tenant);
        String token = issueToken(tenant, agentId);

        mockMvc.perform(in(tenant, delete("/organizations/" + tenant.id() + "/agents/" + agentId),
                        ownerJwt(tenant), null))
                .andExpect(status().isOk());

        mockMvc.perform(bearer(get("/users/me"), token)).andExpect(status().isUnauthorized());
    }

    @Test
    void issueToken_forApiAgent_answers422() throws Exception {
        Tenant tenant = newTenant();
        String agentId = createAgent(tenant, uniqueAgentName(), AgentFlow.API, AgentProvider.OPENAI, "KAZAN");

        mockMvc.perform(in(tenant, post("/organizations/" + tenant.id() + "/agents/" + agentId + "/token"),
                        ownerJwt(tenant), null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("AGT_VAL_003"));
    }

    @Test
    void issueToken_byMember_answers403() throws Exception {
        Tenant tenant = newTenant();
        String agentId = routineAgent(tenant);

        mockMvc.perform(in(tenant, post("/organizations/" + tenant.id() + "/agents/" + agentId + "/token"),
                        jwtOf(tenant.member().getUser()), null))
                .andExpect(status().isForbidden());
    }

    @Test
    void issueToken_forAgentOfAnotherOrganization_answers404() throws Exception {
        Tenant tenant = newTenant();
        String foreignAgentId = routineAgent(newTenant());

        mockMvc.perform(in(tenant, post("/organizations/" + tenant.id() + "/agents/" + foreignAgentId + "/token"),
                        ownerJwt(tenant), null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("AGT_NOT_001"));
    }
}
