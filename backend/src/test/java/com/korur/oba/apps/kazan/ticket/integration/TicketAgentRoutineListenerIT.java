package com.korur.oba.apps.kazan.ticket.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

// 2. Third-party
import org.awaitility.Awaitility;

// 3. Java SDK
import java.time.Duration;

// 4. Internal
import com.korur.oba.connect.routine.anthropic.client.AnthropicRoutineClient;
import com.korur.oba.connect.routine.core.dto.RoutineFireResult;
import com.korur.oba.connect.routine.core.enums.RoutineFireStatus;
import com.korur.oba.shrine.agent.support.AbstractAgentIT;
import com.korur.oba.shrine.identity.enums.AgentFlow;
import com.korur.oba.shrine.identity.enums.AgentProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Assigning a ticket to an agent fires the agent's routine once the assignment has committed, on the routine
 * executor, with the ticket key as the only input. The vendor client is a spy that answers instead of calling
 * Anthropic; everything else is the real application. An agent as reviewer is refused before anything commits.
 */
class TicketAgentRoutineListenerIT extends AbstractAgentIT {

    private static final String SECRET = "sk-ant-routine-secret-0123456789";
    private static final String SESSION_URL = "https://claude.ai/code/session_test";

    @MockitoSpyBean
    private AnthropicRoutineClient anthropicRoutineClient;

    private Tenant tenant;
    private String boardId;
    private String sprintId;
    private String agentId;

    @BeforeEach
    void setUp() throws Exception {
        tenant = newTenant();
        String prefix = "L" + uniqueSuffix().substring(0, 8).toUpperCase();
        boardId = read(mockMvc.perform(in(tenant, post("/kazan/boards"), ownerJwt(tenant),
                        "{\"name\":\"Board " + prefix + "\",\"prefix\":\"" + prefix + "\"}"))
                .andExpect(status().is2xxSuccessful()).andReturn(), "$.data.id");
        sprintId = read(mockMvc.perform(in(tenant, post("/kazan/boards/" + boardId + "/sprints"), ownerJwt(tenant),
                "{\"name\":\"Sprint\"}")).andExpect(status().is2xxSuccessful()).andReturn(), "$.data.id");
        agentId = createAgent(tenant, uniqueAgentName(), AgentFlow.ROUTINE, AgentProvider.ANTHROPIC, "KAZAN");
        mockMvc.perform(in(tenant, put("/organizations/" + tenant.id() + "/agents/" + agentId + "/routine"),
                        ownerJwt(tenant), "{\"routineId\":\"" + ROUTINE_ID + "\",\"routineSecret\":\"" + SECRET + "\"}"))
                .andExpect(status().isOk());
        doReturn(new RoutineFireResult(RoutineFireStatus.FIRED, 200, SESSION_URL))
                .when(anthropicRoutineClient).fire(eq(ROUTINE_ID), eq(SECRET), anyString());
    }

    private String ticketBody(String field, String userId, String status) {
        return "{\"title\":\"Ticket\",\"type\":\"FEATURE\",\"priority\":\"MEDIUM\",\"status\":\"" + status + "\","
                + "\"missionRank\":\"M\",\"sprintId\":\"" + sprintId + "\",\"" + field + "\":[\"" + userId + "\"]}";
    }

    private String createTicketKey(String field, String userId) throws Exception {
        return read(mockMvc.perform(in(tenant, post("/kazan/boards/" + boardId + "/tickets"), ownerJwt(tenant),
                        ticketBody(field, userId, "TODO")))
                .andExpect(status().is2xxSuccessful()).andReturn(), "$.data.key");
    }

    private String lastFireStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT last_fire_status::text FROM shrine.agent_routines WHERE user_id = ?::uuid", String.class, agentId);
    }

    @Test
    void createTicket_agentAssignee_firesTheRoutineAfterCommitWithTheTicketKeyAndRecordsTheResult() throws Exception {
        String key = createTicketKey("assigneeIds", agentId);

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(anthropicRoutineClient, timeout(10_000)).fire(eq(ROUTINE_ID), eq(SECRET), text.capture());
        assertTrue(text.getValue().contains(key), "The routine input names the ticket: " + text.getValue());
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals("FIRED", lastFireStatus()));
        assertEquals(SESSION_URL, jdbcTemplate.queryForObject(
                "SELECT last_session_url FROM shrine.agent_routines WHERE user_id = ?::uuid", String.class, agentId));
        assertEquals(200, jdbcTemplate.queryForObject(
                "SELECT last_http_status FROM shrine.agent_routines WHERE user_id = ?::uuid", Integer.class, agentId));
    }

    @Test
    void updateTicket_agentAddedAsAssignee_firesOnceAndAnUnchangedAssigneeFiresNothing() throws Exception {
        String ticketId = read(mockMvc.perform(in(tenant, post("/kazan/boards/" + boardId + "/tickets"), ownerJwt(tenant),
                        ticketBody("assigneeIds", tenant.member().getUser().getId().toString(), "TODO")))
                .andExpect(status().is2xxSuccessful()).andReturn(), "$.data.id");
        verify(anthropicRoutineClient, after(1500).never()).fire(anyString(), anyString(), anyString());

        mockMvc.perform(in(tenant, put("/kazan/boards/" + boardId + "/tickets/" + ticketId), ownerJwt(tenant),
                        "{\"assigneeIds\":[\"" + agentId + "\"]}"))
                .andExpect(status().is2xxSuccessful());
        verify(anthropicRoutineClient, timeout(10_000)).fire(eq(ROUTINE_ID), eq(SECRET), anyString());

        mockMvc.perform(in(tenant, put("/kazan/boards/" + boardId + "/tickets/" + ticketId), ownerJwt(tenant),
                        "{\"assigneeIds\":[\"" + agentId + "\"]}"))
                .andExpect(status().is2xxSuccessful());
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals("FIRED", lastFireStatus()));
        verify(anthropicRoutineClient, after(1500).times(1))
                .fire(anyString(), anyString(), anyString());
    }

    @Test
    void createTicket_personAssignee_firesNothing() throws Exception {
        createTicketKey("assigneeIds", tenant.owner().getUser().getId().toString());

        verify(anthropicRoutineClient, after(1500).never()).fire(any(), any(), any());
        assertEquals(null, lastFireStatus());
    }

    @Test
    void createTicket_disabledAgentAssignee_firesNothing() throws Exception {
        jdbcTemplate.update("UPDATE shrine.users SET status = 'DISABLED'::shrine_identity_user_status_enum WHERE id = ?::uuid",
                agentId);

        createTicketKey("assigneeIds", agentId);

        verify(anthropicRoutineClient, after(1500).never()).fire(any(), any(), any());
    }

    @Test
    void createTicket_refusedAfterAssigningAnAgent_firesNothingBecauseNothingCommitted() throws Exception {
        String body = ticketBody("assigneeIds", agentId, "TODO").replaceFirst("}$",
                ",\"reviewerIds\":[\"" + agentId + "\"]}");

        mockMvc.perform(in(tenant, post("/kazan/boards/" + boardId + "/tickets"), ownerJwt(tenant), body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("AGT_VAL_001"));

        verify(anthropicRoutineClient, after(1500).never()).fire(any(), any(), any());
    }

    @Test
    void createTicket_agentAsReviewer_answers422AgentTargetRefusedAndCreatesNoTicket() throws Exception {
        long before = count("SELECT count(*) FROM kazan.tickets WHERE board_id = ?::uuid", boardId);

        mockMvc.perform(in(tenant, post("/kazan/boards/" + boardId + "/tickets"), ownerJwt(tenant),
                        ticketBody("reviewerIds", agentId, "TODO")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("AGT_VAL_001"));

        assertEquals(before, count("SELECT count(*) FROM kazan.tickets WHERE board_id = ?::uuid", boardId));
    }

    @Test
    void updateTicket_agentAddedAsReviewer_answers422AgentTargetRefused() throws Exception {
        String ticketId = read(mockMvc.perform(in(tenant, post("/kazan/boards/" + boardId + "/tickets"), ownerJwt(tenant),
                        ticketBody("assigneeIds", tenant.owner().getUser().getId().toString(), "TODO")))
                .andExpect(status().is2xxSuccessful()).andReturn(), "$.data.id");

        mockMvc.perform(in(tenant, put("/kazan/boards/" + boardId + "/tickets/" + ticketId), ownerJwt(tenant),
                        "{\"reviewerIds\":[\"" + agentId + "\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("AGT_VAL_001"));
    }
}
