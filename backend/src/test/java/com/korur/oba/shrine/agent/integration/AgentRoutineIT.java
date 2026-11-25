package com.korur.oba.shrine.agent.integration;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 2. Third-party
import org.awaitility.Awaitility;

// 3. Java SDK
import java.time.Duration;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.agent.support.AbstractAgentIT;
import com.korur.oba.shrine.identity.enums.AgentFlow;
import com.korur.oba.shrine.identity.enums.AgentProvider;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The routine endpoints of an agent: set stores the secret encrypted and shows only a prefix, a replaced
 * routine keeps one row, remove and agent delete drop it, and the refusals answer with their codes.
 */
class AgentRoutineIT extends AbstractAgentIT {

    private static final String SECRET = "sk-ant-secret-" + "0123456789abcdef";

    private String routinePath(Tenant tenant, String agentId) {
        return "/organizations/" + tenant.id() + "/agents/" + agentId + "/routine";
    }

    private static String body(String routineId, String secret) {
        return "{\"routineId\":\"" + routineId + "\",\"routineSecret\":\"" + secret + "\"}";
    }

    private MockHttpServletRequestBuilder setRoutine(Tenant tenant, String agentId) {
        return put(routinePath(tenant, agentId));
    }

    private String routineAgent(Tenant tenant) {
        return createAgent(tenant, uniqueAgentName(), AgentFlow.ROUTINE, AgentProvider.ANTHROPIC, "KAZAN");
    }

    private long routineRows(String agentId) {
        return count("SELECT count(*) FROM shrine.agent_routines WHERE user_id = ?::uuid", agentId);
    }

    @Test
    void setRoutine_validInput_storesEncryptedSecretAndReturnsOnlyAPrefix() throws Exception {
        Tenant tenant = newTenant();
        String agentId = routineAgent(tenant);

        String response = mockMvc.perform(in(tenant, setRoutine(tenant, agentId),
                        ownerJwt(tenant), body(ROUTINE_ID, SECRET)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routineId").value(ROUTINE_ID))
                .andExpect(jsonPath("$.data.routineSecretPrefix").isNotEmpty())
                .andExpect(jsonPath("$.data.routineSecret").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String stored = jdbcTemplate.queryForObject(
                "SELECT routine_secret_encrypted FROM shrine.agent_routines WHERE user_id = ?::uuid",
                String.class, agentId);
        assertNotEquals(SECRET, stored);
        assertEquals(false, response.contains(SECRET));
        String prefix = jdbcTemplate.queryForObject(
                "SELECT routine_secret_prefix FROM shrine.agent_routines WHERE user_id = ?::uuid", String.class, agentId);
        assertEquals(true, SECRET.startsWith(prefix) && prefix.length() < SECRET.length());
        mockMvc.perform(in(tenant, get("/organizations/" + tenant.id() + "/agents"), ownerJwt(tenant), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + agentId + "')].routineId").value(ROUTINE_ID))
                .andExpect(jsonPath("$.data[?(@.id == '" + agentId + "')].routineSecretPrefix").value(prefix));
    }

    @Test
    void setRoutine_neverWritesTheSecretToTheAuditLog() throws Exception {
        Tenant tenant = newTenant();
        String agentId = routineAgent(tenant);

        mockMvc.perform(in(tenant, setRoutine(tenant, agentId), ownerJwt(tenant),
                body(ROUTINE_ID, SECRET))).andExpect(status().isOk());

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals(1L, count(
                "SELECT count(*) FROM dojo.audit_logs WHERE organization_id = ?::uuid AND action = 'AGENT_ROUTINE_SET'",
                tenant.id())));
        assertEquals(0L, count("SELECT count(*) FROM dojo.audit_logs WHERE organization_id = ?::uuid "
                + "AND (coalesce(new_values::text, '') LIKE ? OR coalesce(old_values::text, '') LIKE ?)",
                tenant.id(), "%" + SECRET + "%", "%" + SECRET + "%"));
    }

    @Test
    void setRoutine_secondTime_replacesTheRoutineInOneRow() throws Exception {
        Tenant tenant = newTenant();
        String agentId = routineAgent(tenant);
        mockMvc.perform(in(tenant, setRoutine(tenant, agentId), ownerJwt(tenant),
                body(ROUTINE_ID, SECRET))).andExpect(status().isOk());

        mockMvc.perform(in(tenant, setRoutine(tenant, agentId), ownerJwt(tenant),
                        body("trig_Replaced1", "another-secret-value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routineId").value("trig_Replaced1"));

        assertEquals(1L, routineRows(agentId));
    }

    @Test
    void setRoutine_malformedRoutineId_answers422AgentRoutineIdFormat() throws Exception {
        Tenant tenant = newTenant();
        String agentId = routineAgent(tenant);

        mockMvc.perform(in(tenant, setRoutine(tenant, agentId), ownerJwt(tenant),
                        body("not-a-routine", SECRET)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("AGT_VAL_002"));

        assertEquals(0L, routineRows(agentId));
    }

    @Test
    void setRoutine_blankSecret_answers422() throws Exception {
        Tenant tenant = newTenant();
        String agentId = routineAgent(tenant);

        mockMvc.perform(in(tenant, setRoutine(tenant, agentId), ownerJwt(tenant),
                        body(ROUTINE_ID, "")))
                .andExpect(status().is4xxClientError());

        assertEquals(0L, routineRows(agentId));
    }

    @Test
    void setRoutine_forApiAgent_answers422FlowProviderUnsupported() throws Exception {
        Tenant tenant = newTenant();
        String agentId = createAgent(tenant, uniqueAgentName(), AgentFlow.API, AgentProvider.ANTHROPIC, "KAZAN");

        mockMvc.perform(in(tenant, setRoutine(tenant, agentId), ownerJwt(tenant),
                        body(ROUTINE_ID, SECRET)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("AGT_VAL_003"));

        assertEquals(0L, routineRows(agentId));
    }

    @Test
    void setRoutine_byMember_answers403() throws Exception {
        Tenant tenant = newTenant();
        String agentId = routineAgent(tenant);

        mockMvc.perform(in(tenant, setRoutine(tenant, agentId),
                        jwtOf(tenant.member().getUser()), body(ROUTINE_ID, SECRET)))
                .andExpect(status().isForbidden());
    }

    @Test
    void setRoutine_forAgentOfAnotherOrganization_answers404() throws Exception {
        Tenant tenant = newTenant();
        String foreignAgentId = routineAgent(newTenant());

        mockMvc.perform(in(tenant, setRoutine(tenant, foreignAgentId), ownerJwt(tenant),
                        body(ROUTINE_ID, SECRET)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("AGT_NOT_001"));
    }

    @Test
    void removeRoutine_existingRoutine_deletesItAndClearsTheResponseFields() throws Exception {
        Tenant tenant = newTenant();
        String agentId = routineAgent(tenant);
        mockMvc.perform(in(tenant, setRoutine(tenant, agentId), ownerJwt(tenant),
                body(ROUTINE_ID, SECRET))).andExpect(status().isOk());

        mockMvc.perform(in(tenant, delete(routinePath(tenant, agentId)), ownerJwt(tenant), null))
                .andExpect(status().isOk());

        assertEquals(0L, routineRows(agentId));
        mockMvc.perform(in(tenant, get("/organizations/" + tenant.id() + "/agents"), ownerJwt(tenant), null))
                .andExpect(jsonPath("$.data[?(@.id == '" + agentId + "')].routineId", contains(nullValue())));
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals(1L, count(
                "SELECT count(*) FROM dojo.audit_logs WHERE organization_id = ?::uuid AND action = 'AGENT_ROUTINE_REMOVED'",
                tenant.id())));
    }

    @Test
    void removeRoutine_withoutRoutine_answers200AndWritesNoAuditEntry() throws Exception {
        Tenant tenant = newTenant();
        String agentId = routineAgent(tenant);

        mockMvc.perform(in(tenant, delete(routinePath(tenant, agentId)), ownerJwt(tenant), null))
                .andExpect(status().isOk());

        assertEquals(0L, count("SELECT count(*) FROM dojo.audit_logs WHERE organization_id = ?::uuid "
                + "AND action = 'AGENT_ROUTINE_REMOVED'", tenant.id()));
    }

    @Test
    void removeRoutine_forUnknownAgent_answers404() throws Exception {
        Tenant tenant = newTenant();

        mockMvc.perform(in(tenant, delete(routinePath(tenant, UUID.randomUUID().toString())),
                        ownerJwt(tenant), null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("AGT_NOT_001"));
    }

    @Test
    void deleteAgent_withRoutine_removesTheRoutineRow() throws Exception {
        Tenant tenant = newTenant();
        String agentId = routineAgent(tenant);
        mockMvc.perform(in(tenant, setRoutine(tenant, agentId), ownerJwt(tenant),
                body(ROUTINE_ID, SECRET))).andExpect(status().isOk());

        mockMvc.perform(in(tenant, delete("/organizations/" + tenant.id() + "/agents/" + agentId),
                        ownerJwt(tenant), null))
                .andExpect(status().isOk());

        assertEquals(0L, routineRows(agentId));
    }
}
