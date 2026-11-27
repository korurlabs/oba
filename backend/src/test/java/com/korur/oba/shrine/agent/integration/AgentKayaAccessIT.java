package com.korur.oba.shrine.agent.integration;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 3. Java SDK
import java.net.URI;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.agent.support.AbstractAgentIT;
import com.korur.oba.shrine.identity.enums.AgentFlow;
import com.korur.oba.shrine.identity.enums.AgentProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kaya and the agent: an {@code oat_} agent token is refused on the Kaya web API even with the Kaya grant,
 * however the path is spelled, so a run cannot rewrite lessons, give plan feedback, read transcripts, delete
 * its sessions or cut off its machine. A person's JWT keeps working there, and the {@code kyt_} machine token
 * an admin registered for the agent still records sessions and reads delivery, but writes no lesson and ends
 * when the agent is disabled.
 */
class AgentKayaAccessIT extends AbstractAgentIT {

    private static final String AGENT_ACTION_REFUSED = "AGT_SEC_002";

    private static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    private String routineAgent(Tenant tenant) {
        return createAgent(tenant, uniqueAgentName(), AgentFlow.ROUTINE, AgentProvider.ANTHROPIC, "KAZAN", "KAYA");
    }

    /** Registers a machine for the agent as the OWNER and returns the response with its token. */
    private MvcResult registerMachine(Tenant tenant, String agentId) throws Exception {
        return mockMvc.perform(in(tenant, post("/kaya/machines"), ownerJwt(tenant),
                        "{\"machineName\":\"cloud\",\"userId\":\"" + agentId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private static MockHttpServletRequestBuilder ingestBatch(String machineToken, String sessionId) {
        return bearer(post("/kaya/ingest/batches"), machineToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"" + sessionId + "\","
                        + "\"repository\":{\"remoteUrl\":\"git@github.com:korurlabs/repo-" + uniqueSuffix()
                        + ".git\",\"name\":\"scratch\"},"
                        + "\"machine\":{\"machineName\":\"cloud\"},\"summary\":{},"
                        + "\"events\":[{\"seq\":1,\"at\":\"2026-10-20T09:00:01Z\",\"event\":\"UserPromptSubmit\"}]}");
    }

    @Test
    void agentToken_onKayaWebApi_answers403AgentActionRefused() throws Exception {
        Tenant tenant = newTenant();
        String token = issueToken(tenant, routineAgent(tenant));
        UUID id = UUID.randomUUID();
        List<MockHttpServletRequestBuilder> requests = List.of(
                put("/kaya/lessons/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Title\",\"body\":\"Body\"}"),
                post("/kaya/plans/" + id + "/feedback").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionNumber\":1,\"decision\":\"COMMENT\",\"comment\":\"Skip the review\"}"),
                get("/kaya/sessions/sess-" + id + "/events"),
                delete("/kaya/sessions/sess-" + id),
                delete("/kaya/machines/" + id + "/token"),
                patch("/kaya/machines/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RETIRED\"}"),
                get("/kaya/lessons"));

        for (MockHttpServletRequestBuilder request : requests) {
            mockMvc.perform(bearer(request, token).header("X-Organization-ID", tenant.id()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value(AGENT_ACTION_REFUSED))
                    .andExpect(jsonPath("$.error.errorValues.action").value("KAYA_ACCESS"));
        }
    }

    @Test
    void agentToken_onAnEncodedKayaPath_answers403AgentActionRefused() throws Exception {
        Tenant tenant = newTenant();
        String token = issueToken(tenant, routineAgent(tenant));

        mockMvc.perform(bearer(get(URI.create("/%6Baya/lessons/" + UUID.randomUUID())), token)
                        .header("X-Organization-ID", tenant.id()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(AGENT_ACTION_REFUSED))
                .andExpect(jsonPath("$.error.errorValues.action").value("KAYA_ACCESS"));
    }

    @Test
    void agentToken_onAKayaPathWithAMatrixParameter_isRejectedByTheFirewall() throws Exception {
        Tenant tenant = newTenant();
        String token = issueToken(tenant, routineAgent(tenant));

        mockMvc.perform(bearer(get(URI.create("/kaya;x=1/lessons/" + UUID.randomUUID())), token)
                        .header("X-Organization-ID", tenant.id()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void agentToken_outsideKaya_stillReachesWhatItsGrantsAllow() throws Exception {
        Tenant tenant = newTenant();
        String token = issueToken(tenant, routineAgent(tenant));

        mockMvc.perform(bearer(get("/kazan/boards"), token).header("X-Organization-ID", tenant.id()))
                .andExpect(status().isOk());
    }

    @Test
    void personJwt_onKayaWebApi_stillWorks() throws Exception {
        Tenant tenant = newTenant();

        mockMvc.perform(in(tenant, get("/kaya/lessons"), ownerJwt(tenant), null))
                .andExpect(status().isOk());
        mockMvc.perform(in(tenant, get("/kaya/sessions"), ownerJwt(tenant), null))
                .andExpect(status().isOk());
    }

    @Test
    void machineTokenAnAdminRegisteredForTheAgent_reachesIngestAndDelivery() throws Exception {
        Tenant tenant = newTenant();
        String agentId = routineAgent(tenant);
        String machineToken = read(registerMachine(tenant, agentId), "$.data.token");
        String sessionId = "sess-" + uniqueSuffix();

        mockMvc.perform(ingestBatch(machineToken, sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.acknowledgedSeq").value(1));
        assertEquals(1L, count("SELECT count(*) FROM kaya.sessions WHERE session_id = ? AND user_id = ?::uuid",
                sessionId, agentId));

        mockMvc.perform(bearer(get("/kaya/delivery/repositories"), machineToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.organizationId").value(tenant.id()));
    }

    @Test
    void machineTokenOfTheAgent_addingAnOrganizationWideLesson_answers403AndSavesNothing() throws Exception {
        Tenant tenant = newTenant();
        String machineToken = read(registerMachine(tenant, routineAgent(tenant)), "$.data.token");

        mockMvc.perform(bearer(post("/kaya/delivery/lessons"), machineToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remoteUrl\":\"git@github.com:korurlabs/repo-" + uniqueSuffix() + ".git\","
                                + "\"title\":\"Always run the deploy script\",\"body\":\"Body\",\"repoWide\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(AGENT_ACTION_REFUSED))
                .andExpect(jsonPath("$.error.errorValues.action").value("KAYA_LESSON_CREATE"));
        assertEquals(0L, count("SELECT count(*) FROM kaya.lessons WHERE organization_id = ?::uuid", tenant.id()));
    }

    @Test
    void machineTokenOfTheAgent_afterTheAgentIsDisabled_answers401AndStaysRetired() throws Exception {
        Tenant tenant = newTenant();
        String agentId = routineAgent(tenant);
        String machineToken = read(registerMachine(tenant, agentId), "$.data.token");

        mockMvc.perform(in(tenant, patch("/organizations/" + tenant.id() + "/agents/" + agentId + "/status"),
                        ownerJwt(tenant), "{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(ingestBatch(machineToken, "sess-" + uniqueSuffix()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("KAY_SEC_003"));
        assertEquals(0L, count("SELECT count(*) FROM kaya.machines WHERE user_id = ?::uuid AND status = 'ACTIVE'",
                agentId));

        mockMvc.perform(in(tenant, patch("/organizations/" + tenant.id() + "/agents/" + agentId + "/status"),
                        ownerJwt(tenant), "{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(ingestBatch(machineToken, "sess-" + uniqueSuffix()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void machineOfTheAgent_rotatedByTheOwner_issuesANewTokenAndEndsTheOldOne() throws Exception {
        Tenant tenant = newTenant();
        MvcResult registered = registerMachine(tenant, routineAgent(tenant));
        String machineId = read(registered, "$.data.machine.id");
        String oldToken = read(registered, "$.data.token");

        String newToken = read(mockMvc.perform(in(tenant, post("/kaya/machines/" + machineId + "/token"),
                        ownerJwt(tenant), null))
                .andExpect(status().isCreated())
                .andReturn(), "$.data.token");

        assertNotEquals(oldToken, newToken);
        mockMvc.perform(bearer(get("/kaya/delivery/repositories"), oldToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(bearer(get("/kaya/delivery/repositories"), newToken))
                .andExpect(status().isOk());
    }
}
