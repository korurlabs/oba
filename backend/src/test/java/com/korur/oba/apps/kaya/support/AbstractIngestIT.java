package com.korur.oba.apps.kaya.support;

// 1. Framework-level
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

// 2. Third-party
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

// 3. Java SDK
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base of the ingest tests: registers machines through POST /machines and posts recorder batches
 * with the machine token, the way the recorder's sender does.
 */
public abstract class AbstractIngestIT extends AbstractTenantIT {

    protected static final Instant BASE_TIME = Instant.parse("2026-10-20T09:00:00Z");
    protected static final String CLAUDE_ORGANIZATION_UUID = "11111111-2222-3333-4444-555555555555";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** A registered machine: its id and name, the token shown once and the person who owns it. */
    protected record Machine(String machineId, String token, String machineName, Person owner) {
    }

    protected Machine registerMachine(Tenant tenant, Person owner, String machineName) throws Exception {
        MvcResult result = mockMvc.perform(in(tenant, post("/kaya/machines"), owner.jwt(), "{\"machineName\":\"" + machineName + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new Machine(read(result, "$.data.machine.id"), read(result, "$.data.token"), machineName, owner);
    }

    /** A machine for a user, registered by the tenant's ADMIN. */
    protected Machine registerMachineForUser(Tenant tenant, String userId, String machineName) throws Exception {
        MvcResult result = mockMvc.perform(in(tenant, post("/kaya/machines"), jwtOf(tenant.admin().getUser()),
                        "{\"machineName\":\"" + machineName + "\",\"userId\":\"" + userId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new Machine(read(result, "$.data.machine.id"), read(result, "$.data.token"), machineName, null);
    }

    protected static String remote() {
        return "git@github.com:senseylabs/repo-" + uniqueSuffix() + ".git";
    }

    protected static String sessionId() {
        return "sess-" + uniqueSuffix();
    }

    /** One recorder event with a time of one second per seq after BASE_TIME. */
    protected static Map<String, Object> event(long seq, String name) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("seq", seq);
        event.put("at", BASE_TIME.plusSeconds(seq).toString());
        event.put("event", name);
        return event;
    }

    protected static List<Map<String, Object>> events(long fromSeq, long toSeq) {
        List<Map<String, Object>> events = new ArrayList<>();
        for (long seq = fromSeq; seq <= toSeq; seq++) {
            events.add(event(seq, "UserPromptSubmit"));
        }
        return events;
    }

    /** A batch with events, an empty summary and no handoffs or usage; callers add what the case needs. */
    protected static Map<String, Object> batch(Machine machine, String sessionId, String remote,
            List<Map<String, Object>> events) {
        Map<String, Object> batch = new LinkedHashMap<>();
        batch.put("sessionId", sessionId);
        batch.put("repository", new LinkedHashMap<>(Map.of("remoteUrl", remote, "name", "scratch")));
        batch.put("machine", new LinkedHashMap<>(Map.of("machineName", machine.machineName())));
        batch.put("summary", new LinkedHashMap<String, Object>());
        batch.put("events", events);
        return batch;
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Object> section(Map<String, Object> batch, String key) {
        return (Map<String, Object>) batch.get(key);
    }

    protected static Map<String, Object> usage(Instant capturedAt, double fiveHourPercentage, long fiveHourResetsAt,
            double sevenDayPercentage, long sevenDayResetsAt) {
        return Map.of("capturedAt", capturedAt.toString(),
                "fiveHour", Map.of("usedPercentage", fiveHourPercentage, "resetsAt", fiveHourResetsAt),
                "sevenDay", Map.of("usedPercentage", sevenDayPercentage, "resetsAt", sevenDayResetsAt));
    }

    protected static Map<String, Object> account(String email) {
        return Map.of("emailAddress", email, "organizationUuid", CLAUDE_ORGANIZATION_UUID,
                "organizationName", "Korur", "billingType", "stripe_subscription", "seatTier", "team_premium");
    }

    protected ResultActions send(String token, Object body) throws Exception {
        return mockMvc.perform(post("/kaya/ingest/batches")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
    }

    protected String json(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }

    protected long sessionCount(String sessionId) {
        return count("SELECT count(*) FROM kaya.sessions WHERE session_id = ?", sessionId);
    }

    protected long eventCount(String sessionId) {
        return count("""
                SELECT count(*) FROM kaya.session_events e JOIN kaya.sessions c ON c.id = e.session_id
                WHERE c.session_id = ?
                """, sessionId);
    }
}
