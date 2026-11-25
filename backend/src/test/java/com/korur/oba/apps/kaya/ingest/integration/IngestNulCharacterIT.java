package com.korur.oba.apps.kaya.ingest.integration;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// 2. Third-party
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

// 3. Java SDK
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractIngestIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Batches that carry U+0000, which PostgreSQL cannot hold in text or jsonb. Each batch is sent
 * through the endpoint as the recorder sends it, with the character escaped in the JSON, and must
 * be accepted.
 */
class IngestNulCharacterIT extends AbstractIngestIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // The start of an image file as the recorder reads it: bytes that are no UTF-8 arrive as U+FFFD,
    // and the zero bytes arrive as U+0000.
    private static final String IMAGE_CONTENT = "\uFFFDPNG\r\n\u001A\n\u0000\u0000\u0000\rIHDR\u0000\u0000\u0001";
    // An image as an older recorder sends it: cut at 200000 code points, of which the characters outside
    // the Basic Multilingual Plane count twice in Java, so it is longer than the 200000-character limit.
    private static final String LONG_IMAGE_CONTENT = IMAGE_CONTENT + "\uD83D\uDE00".repeat(100) + "x".repeat(200_000);
    private static final String NOTE_CONTENT = "# Export\n\n**Status:** PARTIAL\n";
    private static final String CAPTURED_AT = "2026-10-20T09:10:00Z";

    private Machine newMachine() throws Exception {
        Tenant tenant = newTenant();
        return registerMachine(tenant, signIn(tenant, "ada"), "laptop");
    }

    private static Map<String, Object> snapshot(String path, String content) {
        return Map.of("path", path, "content", content, "capturedAt", CAPTURED_AT);
    }

    private static Map<String, Object> plan(String kind, String source, String path, String content) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("kind", kind);
        plan.put("source", source);
        plan.put("path", path);
        plan.put("content", content);
        plan.put("capturedAt", CAPTURED_AT);
        return plan;
    }

    private List<Map<String, Object>> handoffsOf(String sessionId) {
        return jdbcTemplate.queryForList("""
                SELECT h.path, h.content FROM kaya.session_handoff_snapshots h
                JOIN kaya.sessions c ON c.id = h.session_id WHERE c.session_id = ?
                """, sessionId);
    }

    private Map<String, Object> sessionRowOf(String sessionId) {
        return jdbcTemplate.queryForMap("""
                SELECT c.title, c.first_prompt, c.branch, c.claude_code_version
                FROM kaya.sessions c WHERE c.session_id = ?
                """, sessionId);
    }

    @Nested
    class BinarySnapshots {

        @Test
        void batch_withHandoffHoldingNul_isAcceptedAndStoresEverythingElse() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            Map<String, Object> batch = batch(machine, session, remote(), events(1, 3));
            section(batch, "summary").put("title", "Export the board");
            batch.put("handoffs", List.of(snapshot("export/screen.png", IMAGE_CONTENT),
                    snapshot("export/backend.md", NOTE_CONTENT)));

            send(machine.token(), batch)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.acknowledgedSeq").value(3));

            assertEquals(3L, eventCount(session));
            assertEquals("Export the board", sessionRowOf(session).get("title"));
            assertEquals(List.of(Map.of("path", "export/backend.md", "content", NOTE_CONTENT)), handoffsOf(session));
        }

        @Test
        void batch_withBinarySnapshotsOverTheTextLimit_isAcceptedAndLeavesThemOut() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            Map<String, Object> batch = batch(machine, session, remote(), events(1, 2));
            batch.put("handoffs", List.of(snapshot("export/screen.png", LONG_IMAGE_CONTENT),
                    snapshot("export/backend.md", NOTE_CONTENT)));
            batch.put("plans", List.of(plan("SPEC", "FILE", "docs/plans/export-diagram.md", LONG_IMAGE_CONTENT)));

            send(machine.token(), batch)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.acknowledgedSeq").value(2));

            assertEquals(2L, eventCount(session));
            assertEquals(List.of(Map.of("path", "export/backend.md", "content", NOTE_CONTENT)), handoffsOf(session));
            assertEquals(0L, jdbcTemplate.queryForObject("""
                    SELECT count(*) FROM kaya.plans p
                    JOIN kaya.sessions c ON c.repository_id = p.repository_id WHERE c.session_id = ?
                    """, Long.class, session));
        }

        @Test
        void batch_withPlanHoldingNul_isAcceptedAndStoresTheOtherPlan() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            Map<String, Object> batch = batch(machine, session, remote(), events(1, 1));
            batch.put("plans", List.of(plan("SPEC", "FILE", "docs/plans/export-diagram.md", IMAGE_CONTENT),
                    plan("SESSION", "MESSAGE", null, "1. **Goal**: Export the board.\n")));

            send(machine.token(), batch)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.acknowledgedSeq").value(1));

            List<Map<String, Object>> plans = jdbcTemplate.queryForList("""
                    SELECT p.kind::text AS kind, v.content FROM kaya.plans p
                    JOIN kaya.plan_versions v ON v.plan_id = p.id
                    JOIN kaya.sessions c ON c.repository_id = p.repository_id WHERE c.session_id = ?
                    """, session);
            assertEquals(List.of(Map.of("kind", "SESSION", "content", "1. **Goal**: Export the board.\n")), plans);
        }
    }

    @Nested
    class OtherText {

        @Test
        void batch_withNulInAnEventPayload_isAcceptedAndStoresTheReplacementCharacterInJsonb() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            Map<String, Object> bash = event(1, "PostToolUse");
            bash.put("tool_name", "Bash");
            bash.put("tool_input", Map.of("command", "printf 'a\u0000b'"));
            bash.put("tool_response", Map.of("std\u0000out", "a\u0000b"));

            send(machine.token(), batch(machine, session, remote(), List.of(bash)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.acknowledgedSeq").value(1));

            String payload = jdbcTemplate.queryForObject("""
                    SELECT e.payload::text FROM kaya.session_events e
                    JOIN kaya.sessions c ON c.id = e.session_id WHERE c.session_id = ? AND e.seq = 1
                    """, String.class, session);
            Map<String, Object> stored = MAPPER.readValue(payload, new TypeReference<>() {
            });
            assertEquals(Map.of("tool_name", "Bash",
                    "tool_input", Map.of("command", "printf 'a\uFFFDb'"),
                    "tool_response", Map.of("std\uFFFDout", "a\uFFFDb")), stored);
        }

        @Test
        void batch_withNulInSummaryAndMachineText_isAcceptedAndStoresTheReplacementCharacter() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            Map<String, Object> batch = batch(machine, session, remote(), events(1, 2));
            section(batch, "machine").put("claudeCodeVersion", "2.3\u0000.1");
            Map<String, Object> summary = section(batch, "summary");
            summary.put("title", "Export\u0000 the board");
            summary.put("first_prompt", "Export the board\u0000 as CSV");
            summary.put("branch", "feature/\u0000export");

            send(machine.token(), batch)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.acknowledgedSeq").value(2));

            Map<String, Object> sessionRow = sessionRowOf(session);
            assertEquals("Export\uFFFD the board", sessionRow.get("title"));
            assertEquals("Export the board\uFFFD as CSV", sessionRow.get("first_prompt"));
            assertEquals("feature/\uFFFDexport", sessionRow.get("branch"));
            assertEquals("2.3\uFFFD.1", sessionRow.get("claude_code_version"));
            assertEquals(2L, eventCount(session));
        }
    }
}
