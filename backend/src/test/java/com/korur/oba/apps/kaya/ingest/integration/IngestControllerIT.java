package com.korur.oba.apps.kaya.ingest.integration;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

// 2. Third-party
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

// 3. Java SDK
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

// 4. Internal
import com.korur.oba.apps.kaya.session.helper.SessionActivityDeriver;
import com.korur.oba.apps.kaya.support.AbstractIngestIT;
import com.korur.oba.apps.kaya.support.RunFixtures;
import com.korur.oba.apps.kaya.support.RunFixtures.HandoffFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IngestControllerIT extends AbstractIngestIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Machine newMachine() throws Exception {
        Tenant tenant = newTenant();
        return registerMachine(tenant, signIn(tenant, "ada"), "laptop");
    }

    private Map<String, Object> sessionRowOf(String sessionId) {
        return jdbcTemplate.queryForMap("""
                SELECT c.title, c.branch, c.model, c.cwd, c.claude_code_version, c.account_email, c.seat_tier,
                       c.git_author_email, c.event_count, c.ended, c.started_at, c.last_event_at,
                       c.features::text AS features, c.first_prompt,
                       c.session_title, c.organization_id, c.user_id, c.identity_conflict_user_id, c.machine_id, c.repository_id
                FROM kaya.sessions c WHERE c.session_id = ?
                """, sessionId);
    }

    @Nested
    class FirstBatch {

        @Test
        void firstBatch_createsTheSessionWithItsSummaryAndAcknowledgesTheHighestSeq() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            Map<String, Object> batch = batch(machine, session, remote(), events(1, 4));
            section(batch, "machine").put("claudeCodeVersion", "2.3.1");
            Map<String, Object> summary = section(batch, "summary");
            summary.put("title", "Push the branch");
            summary.put("first_prompt", "Push the branch and open the pull request");
            summary.put("session_title", "Pushing");
            summary.put("branch", "feature/board-export");
            summary.put("cwd", "/repo");
            summary.put("model", "claude-opus-5-5");
            summary.put("features", List.of("board-export"));
            summary.put("started_at", "2026-10-20T08:59:00Z");
            summary.put("ended", true);

            send(machine.token(), batch)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.sessionId").value(session))
                    .andExpect(jsonPath("$.data.acknowledgedSeq").value(4));

            Map<String, Object> sessionRow = sessionRowOf(session);
            assertEquals("Push the branch", sessionRow.get("title"));
            assertEquals("Pushing", sessionRow.get("session_title"));
            assertEquals("Push the branch and open the pull request", sessionRow.get("first_prompt"));
            assertEquals("feature/board-export", sessionRow.get("branch"));
            assertEquals("/repo", sessionRow.get("cwd"));
            assertEquals("claude-opus-5-5", sessionRow.get("model"));
            assertEquals("2.3.1", sessionRow.get("claude_code_version"));
            assertEquals(4, sessionRow.get("event_count"));
            assertEquals(true, sessionRow.get("ended"));
            assertEquals("[\"board-export\"]", sessionRow.get("features"));
            assertEquals(Instant.parse("2026-10-20T08:59:00Z"), ((Timestamp) sessionRow.get("started_at")).toInstant());
            assertEquals(BASE_TIME.plusSeconds(4), ((Timestamp) sessionRow.get("last_event_at")).toInstant());
            assertEquals(machine.machineId(), sessionRow.get("machine_id").toString());
            assertEquals(machine.owner().userId(), sessionRow.get("user_id").toString());
        }

        @Test
        void firstBatch_withTheOldAgentKey_isAcceptedAndRecordsTheMachine() throws Exception {
            // Recorders released before the rename still send the machine section under "agent".
            Machine machine = newMachine();
            String session = sessionId();
            Map<String, Object> batch = batch(machine, session, remote(), events(1, 2));
            Map<String, Object> machineSection = section(batch, "machine");
            machineSection.put("claudeCodeVersion", "2.3.1");
            batch.remove("machine");
            batch.put("agent", machineSection);

            send(machine.token(), batch)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.sessionId").value(session))
                    .andExpect(jsonPath("$.data.acknowledgedSeq").value(2));

            Map<String, Object> sessionRow = sessionRowOf(session);
            assertEquals(machine.machineId(), sessionRow.get("machine_id").toString());
            assertEquals("2.3.1", sessionRow.get("claude_code_version"));
        }

        @Test
        void firstBatch_withTheMachineNameOfAnotherMachine_isAcceptedForTheTokensMachine() throws Exception {
            // The recorder reports the host name, which changes with the network; the token alone
            // identifies the machine, even when the name is that of another machine of the user.
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            Machine laptop = registerMachine(tenant, ada, "laptop");
            Machine desktop = registerMachine(tenant, ada, "desktop");
            String session = sessionId();
            Map<String, Object> batch = batch(laptop, session, remote(), events(1, 2));
            section(batch, "machine").put("machineName", "desktop");

            send(laptop.token(), batch)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.sessionId").value(session))
                    .andExpect(jsonPath("$.data.acknowledgedSeq").value(2));

            Map<String, Object> sessionRow = sessionRowOf(session);
            assertEquals(laptop.machineId(), sessionRow.get("machine_id").toString());
            assertEquals(ada.userId(), sessionRow.get("user_id").toString());
            assertEquals(0L, count("SELECT count(*) FROM kaya.sessions WHERE machine_id = ?::uuid", desktop.machineId()));
            assertEquals(1L, count("SELECT count(*) FROM kaya.machines WHERE id = ?::uuid AND machine_name = 'laptop' "
                    + "AND last_seen_at IS NOT NULL", laptop.machineId()));
        }

        @Test
        void firstBatch_withoutTitleOrPrompt_titlesTheSessionFromTheSessionTitleThenThePrompt() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            Map<String, Object> batch = batch(machine, session, remote(), events(1, 1));
            section(batch, "summary").put("first_prompt", "Fix the export");

            send(machine.token(), batch).andExpect(status().isOk());

            assertEquals("Fix the export", sessionRowOf(session).get("title"));
        }

        @Test
        void firstBatch_connectsTheRepositoryInTheTokensOrganization() throws Exception {
            Tenant tenant = newTenant();
            Machine machine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            String session = sessionId();
            String remote = "git@github.com:senseylabs/connect-" + uniqueSuffix() + ".git";

            send(machine.token(), batch(machine, session, remote, events(1, 1))).andExpect(status().isOk());

            Map<String, Object> repository = jdbcTemplate.queryForMap("""
                    SELECT r.organization_id, r.status::text AS status, r.remote_url
                    FROM kaya.repositories r JOIN kaya.sessions c ON c.repository_id = r.id
                    WHERE c.session_id = ?
                    """, session);
            assertEquals(tenant.id(), repository.get("organization_id").toString());
            assertEquals("ACTIVE", repository.get("status"));
            assertTrue(repository.get("remote_url").toString().startsWith("github.com/senseylabs/connect-"));
        }

        @Test
        void firstBatch_storesTheKeysOfAnEventNextToItsColumns() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            Map<String, Object> bash = event(1, "PreToolUse");
            bash.put("tool_name", "Bash");
            bash.put("command", "git status");
            bash.put("agent_id", "sub-1");

            send(machine.token(), batch(machine, session, remote(), List.of(bash))).andExpect(status().isOk());

            Map<String, Object> row = jdbcTemplate.queryForMap("""
                    SELECT e.seq, e.event, e.agent_id, e.payload::text AS payload
                    FROM kaya.session_events e JOIN kaya.sessions c ON c.id = e.session_id WHERE c.session_id = ?
                    """, session);
            assertEquals(1L, row.get("seq"));
            assertEquals("PreToolUse", row.get("event"));
            assertEquals("sub-1", row.get("agent_id"));
            Map<String, Object> payload = MAPPER.readValue((String) row.get("payload"), new TypeReference<>() {
            });
            assertEquals(Map.of("tool_name", "Bash", "command", "git status"), payload);
        }

        @Test
        void firstBatch_overlongSummaryTextIsCutToItsColumnsAndNeverRefused() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            Map<String, Object> batch = batch(machine, session, remote(), events(1, 1));
            section(batch, "summary").put("first_prompt", "p".repeat(900));
            section(batch, "summary").put("branch", "b".repeat(400));
            section(batch, "summary").put("title", "t".repeat(900));

            send(machine.token(), batch).andExpect(status().isOk());

            Map<String, Object> sessionRow = sessionRowOf(session);
            assertEquals(500, sessionRow.get("first_prompt").toString().length());
            assertEquals(255, sessionRow.get("branch").toString().length());
            assertEquals(500, sessionRow.get("title").toString().length());
        }

        @Test
        void firstBatch_emptySummaryStringsReadAsAbsent() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            Map<String, Object> batch = batch(machine, session, remote(), events(1, 1));
            section(batch, "summary").put("branch", "");
            section(batch, "summary").put("model", "  ");
            section(batch, "summary").put("title", "Real title");

            send(machine.token(), batch).andExpect(status().isOk());

            Map<String, Object> sessionRow = sessionRowOf(session);
            assertNull(sessionRow.get("branch"));
            assertNull(sessionRow.get("model"));
        }

        @Test
        void firstBatch_withoutSummaryStartUsesTheEarliestEvent() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();

            send(machine.token(), batch(machine, session, remote(), events(1, 3))).andExpect(status().isOk());

            assertEquals(BASE_TIME.plusSeconds(1), ((Timestamp) sessionRowOf(session).get("started_at")).toInstant());
        }
    }

    @Nested
    class LinksAndHandoffs {

        @Test
        void batch_withPullRequestUrlAndTicket_addsRecorderLinksOnce() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            Map<String, Object> pullRequest = event(1, "PostToolUse");
            pullRequest.put("url", "https://github.com/senseylabs/village/pull/643");
            Map<String, Object> other = event(2, "PostToolUse");
            other.put("url", "https://example.com/not/a/pull-request");
            Map<String, Object> batch = batch(machine, session, remote(), List.of(pullRequest, other));
            section(batch, "summary").put("ticket", "KAYA-42");

            send(machine.token(), batch).andExpect(status().isOk());
            send(machine.token(), batch).andExpect(status().isOk());

            List<Map<String, Object>> links = jdbcTemplate.queryForList("""
                    SELECT l.kind::text AS kind, l.reference, l.source::text AS source
                    FROM kaya.session_links l JOIN kaya.sessions c ON c.id = l.session_id
                    WHERE c.session_id = ? ORDER BY l.kind::text
                    """, session);
            assertEquals(2, links.size());
            assertEquals("KAZAN_TICKET", links.get(0).get("kind"));
            assertEquals("KAYA-42", links.get(0).get("reference"));
            assertEquals("PULL_REQUEST", links.get(1).get("kind"));
            assertEquals("https://github.com/senseylabs/village/pull/643", links.get(1).get("reference"));
            assertEquals("RECORDER", links.get(1).get("source"));
        }

        @Test
        void batch_withTicketNotMatchingTheKeyPattern_addsNoLink() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            Map<String, Object> batch = batch(machine, session, remote(), events(1, 1));
            section(batch, "summary").put("ticket", "not a ticket");

            send(machine.token(), batch).andExpect(status().isOk());

            assertEquals(0L, count("SELECT count(*) FROM kaya.session_links l JOIN kaya.sessions c ON c.id = l.session_id WHERE c.session_id = ?",
                    session));
        }

        @Test
        void batch_withNewerHandoffSnapshot_replacesItAndOlderOneIsIgnored() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            String path = "board-export/planning.md";

            send(machine.token(), withHandoff(batch(machine, session, remote(), events(1, 1)), path, "# H\n\n**Status:** PARTIAL\n",
                    "2026-10-20T09:10:00Z")).andExpect(status().isOk());
            send(machine.token(), withHandoff(batch(machine, session, remote(), events(2, 2)), path, "# H\n\n**Status:** DONE\n",
                    "2026-10-20T09:20:00Z")).andExpect(status().isOk());
            send(machine.token(), withHandoff(batch(machine, session, remote(), events(3, 3)), path, "# H\n\n**Status:** BLOCKED\n",
                    "2026-10-20T09:05:00Z")).andExpect(status().isOk());

            List<String> contents = jdbcTemplate.queryForList("""
                    SELECT h.content FROM kaya.session_handoff_snapshots h JOIN kaya.sessions c ON c.id = h.session_id
                    WHERE c.session_id = ? AND h.path = ?
                    """, String.class, session, path);
            assertEquals(List.of("# H\n\n**Status:** DONE\n"), contents);
        }

        private Map<String, Object> withHandoff(Map<String, Object> batch, String path, String content, String capturedAt) {
            batch.put("handoffs", List.of(Map.of("path", path, "content", content, "capturedAt", capturedAt)));
            return batch;
        }
    }

    @Nested
    class DerivedActivity {

        @Test
        void laterBatch_derivesOverEveryEventOfTheSessionNotOnlyTheNewOnes() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            List<Map<String, Object>> all = RunFixtures.events("plan-waiting");
            int split = all.size() / 2;
            String remote = remote();
            int expected = new SessionActivityDeriver().derive(RunFixtures.events("plan-waiting")).subagents().size();

            send(machine.token(), batch(machine, session, remote, new ArrayList<>(all.subList(0, split)))).andExpect(status().isOk());
            send(machine.token(), batch(machine, session, remote, new ArrayList<>(all.subList(split, all.size()))))
                    .andExpect(status().isOk());

            assertEquals((long) expected, count("SELECT count(*) FROM kaya.session_subagents s JOIN kaya.sessions c ON c.id = s.session_id WHERE c.session_id = ?",
                    session));
            assertEquals(all.size(), sessionRowOf(session).get("event_count"));
        }

        @Test
        void sessionWithSubagentsAndSkills_storesThemOnceAcrossReplays() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            Map<String, Object> batch = batch(machine, session, remote(), RunFixtures.events("plan-waiting"));
            List<Map<String, Object>> handoffs = new ArrayList<>();
            for (HandoffFile file : RunFixtures.handoffs("plan-waiting")) {
                handoffs.add(Map.of("path", file.path(), "content", file.content(), "capturedAt", "2026-10-20T10:00:00Z"));
            }
            batch.put("handoffs", handoffs);

            send(machine.token(), batch).andExpect(status().isOk());
            long subagents = count("SELECT count(*) FROM kaya.session_subagents s JOIN kaya.sessions c ON c.id = s.session_id WHERE c.session_id = ?",
                    session);
            long skills = count("SELECT count(*) FROM kaya.session_skill_uses s JOIN kaya.sessions c ON c.id = s.session_id WHERE c.session_id = ?",
                    session);
            send(machine.token(), batch).andExpect(status().isOk());

            assertTrue(subagents >= 1, "the plan run names a subagent");
            assertEquals(subagents, count("SELECT count(*) FROM kaya.session_subagents s JOIN kaya.sessions c ON c.id = s.session_id WHERE c.session_id = ?",
                    session));
            assertEquals(skills, count("SELECT count(*) FROM kaya.session_skill_uses s JOIN kaya.sessions c ON c.id = s.session_id WHERE c.session_id = ?",
                    session));
        }
    }

    @Nested
    class Replay {

        @Test
        void sameBatchTwice_storesEachEventOnceAndAcknowledgesTheSameSeq() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            Map<String, Object> batch = batch(machine, session, remote(), events(1, 6));

            send(machine.token(), batch).andExpect(status().isOk()).andExpect(jsonPath("$.data.acknowledgedSeq").value(6));
            send(machine.token(), batch).andExpect(status().isOk()).andExpect(jsonPath("$.data.acknowledgedSeq").value(6));

            assertEquals(6L, eventCount(session));
            assertEquals(1L, sessionCount(session));
            assertEquals(6, sessionRowOf(session).get("event_count"));
        }

        @Test
        void overlappingBatch_storesOnlyTheNewEvents() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            String remote = remote();

            send(machine.token(), batch(machine, session, remote, events(1, 3))).andExpect(status().isOk());
            send(machine.token(), batch(machine, session, remote, events(2, 5)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.acknowledgedSeq").value(5));

            assertEquals(5L, eventCount(session));
            assertEquals(5, sessionRowOf(session).get("event_count"));
        }

        @Test
        void replayWithChangedPayload_keepsTheEventAlreadyStored() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            Map<String, Object> first = event(1, "UserPromptSubmit");
            first.put("prompt", "original");
            Map<String, Object> again = event(1, "UserPromptSubmit");
            again.put("prompt", "changed");

            send(machine.token(), batch(machine, session, remote(), List.of(first))).andExpect(status().isOk());
            send(machine.token(), batch(machine, session, remote(), List.of(again))).andExpect(status().isOk());

            String payload = jdbcTemplate.queryForObject("""
                    SELECT e.payload::text FROM kaya.session_events e JOIN kaya.sessions c ON c.id = e.session_id WHERE c.session_id = ?
                    """, String.class, session);
            assertTrue(payload.contains("original"), payload);
        }

        @Test
        void seqGap_storesWhatArrivedAcknowledgesUpToTheGapAndAcceptsTheLateFill() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            String remote = remote();

            send(machine.token(), batch(machine, session, remote, events(1, 3))).andExpect(status().isOk());
            send(machine.token(), batch(machine, session, remote, events(6, 8)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.acknowledgedSeq").value(3));
            assertEquals(6L, eventCount(session));

            send(machine.token(), batch(machine, session, remote, events(4, 5)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.acknowledgedSeq").value(8));
            assertEquals(8L, eventCount(session));
            assertEquals(8, sessionRowOf(session).get("event_count"));
        }

        @Test
        void eventsOutOfOrderInsideABatch_areStoredAndAcknowledgedByTheHighestSeq() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            List<Map<String, Object>> shuffled = new ArrayList<>(List.of(event(3, "Stop"), event(1, "SessionStart"), event(2, "Stop")));

            send(machine.token(), batch(machine, session, remote(), shuffled))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.acknowledgedSeq").value(3));

            assertEquals(3L, eventCount(session));
        }

        @Test
        void laterBatch_extendsTheSessionsLastEventTimeButNeverMovesItsStartLater() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            String remote = remote();

            send(machine.token(), batch(machine, session, remote, events(1, 3))).andExpect(status().isOk());
            send(machine.token(), batch(machine, session, remote, events(4, 9))).andExpect(status().isOk());

            Map<String, Object> sessionRow = sessionRowOf(session);
            assertEquals(BASE_TIME.plusSeconds(1), ((Timestamp) sessionRow.get("started_at")).toInstant());
            assertEquals(BASE_TIME.plusSeconds(9), ((Timestamp) sessionRow.get("last_event_at")).toInstant());
        }
    }

    @Nested
    class Refusals {

        @Test
        void batchWithoutEventsAndWithoutUsage_returns400() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();

            send(machine.token(), batch(machine, session, remote(), List.of()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("KAY_VAL_014"))
                    .andExpect(jsonPath("$.error.errorValues.sessionId").value(session));
        }

        @Test
        void batchWithoutEventsKeyAndWithoutUsage_returns400() throws Exception {
            Machine machine = newMachine();
            Map<String, Object> batch = batch(machine, sessionId(), remote(), List.of());
            batch.remove("events");

            send(machine.token(), batch)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("KAY_VAL_014"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.korur.oba.apps.kaya.ingest.integration.IngestControllerIT#invalidEvents")
        void invalidEvent_returns400NamingTheIndexAndFieldAndStoresNothing(String name, Map<String, Object> bad, String field)
                throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            List<Map<String, Object>> events = new ArrayList<>(List.of(event(1, "SessionStart"), bad));

            send(machine.token(), batch(machine, session, remote(), events))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("KAY_VAL_013"))
                    .andExpect(jsonPath("$.error.errorValues.index").value(1))
                    .andExpect(jsonPath("$.error.errorValues.field").value(field));

            assertEquals(0L, sessionCount(session), "a refused batch rolls back as a whole");
        }

        @Test
        void remoteNotInAnAcceptedForm_returns400() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();

            send(machine.token(), batch(machine, session, "not a remote", events(1, 1)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("GEN_API_001"));

            assertEquals(0L, sessionCount(session));
        }

        @Test
        void archivedRepository_returns422AndStoresNothing() throws Exception {
            Tenant tenant = newTenant();
            Machine machine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            String remote = remote();
            send(machine.token(), batch(machine, sessionId(), remote, events(1, 1))).andExpect(status().isOk());
            jdbcTemplate.update("UPDATE kaya.repositories SET status = 'ARCHIVED' WHERE organization_id = ?::uuid", tenant.id());
            String session = sessionId();

            send(machine.token(), batch(machine, session, remote, events(1, 1)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("KAY_VAL_002"));

            assertEquals(0L, sessionCount(session));
        }

        @Test
        void sessionOfAnotherUser_returns409AndLeavesTheSessionUntouched() throws Exception {
            Tenant tenant = newTenant();
            Machine ada = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            Machine bo = registerMachine(tenant, signIn(tenant, "bo"), "desktop");
            String session = sessionId();
            String remote = remote();
            send(ada.token(), batch(ada, session, remote, events(1, 2))).andExpect(status().isOk());

            send(bo.token(), batch(bo, session, remote, events(3, 4)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("KAY_VAL_001"))
                    .andExpect(jsonPath("$.error.errorValues.sessionId").value(session));

            assertEquals(2L, eventCount(session));
            assertEquals(ada.owner().userId(), sessionRowOf(session).get("user_id").toString());
        }

        @Test
        void usageOnlyBatchForASessionOfAnotherUser_returns409() throws Exception {
            Tenant tenant = newTenant();
            Machine ada = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            Machine bo = registerMachine(tenant, signIn(tenant, "bo"), "desktop");
            String session = sessionId();
            String remote = remote();
            send(ada.token(), batch(ada, session, remote, events(1, 2))).andExpect(status().isOk());
            Map<String, Object> usageOnly = batch(bo, session, remote, List.of());
            long now = Instant.now().getEpochSecond();
            usageOnly.put("usage", usage(Instant.now(), 10, now + 3600, 20, now + 86400));

            send(bo.token(), usageOnly)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("KAY_VAL_001"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.korur.oba.apps.kaya.ingest.integration.IngestControllerIT#invalidBodies")
        void invalidBodyField_returns422(String name, String key, Object value) throws Exception {
            Machine machine = newMachine();
            Map<String, Object> batch = batch(machine, sessionId(), remote(), events(1, 1));
            batch.put(key, value);

            send(machine.token(), batch)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));
        }

        @Test
        void missingRepository_returns422() throws Exception {
            Machine machine = newMachine();
            Map<String, Object> batch = batch(machine, sessionId(), remote(), events(1, 1));
            batch.remove("repository");

            send(machine.token(), batch)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));
        }

        @Test
        void fiveHundredAndOneEvents_returns422() throws Exception {
            Machine machine = newMachine();

            send(machine.token(), batch(machine, sessionId(), remote(), events(1, 501)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));
        }

        @Test
        void fiveHundredEvents_areAccepted() throws Exception {
            Machine machine = newMachine();

            send(machine.token(), batch(machine, sessionId(), remote(), events(1, 500)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.acknowledgedSeq").value(500));
        }

        @Test
        void rateLimit_afterOneHundredAndTwentyBatchesTheNextOneIs429() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            String remote = remote();
            Map<String, Object> batch = batch(machine, session, remote, events(1, 1));

            for (int sent = 1; sent <= 120; sent++) {
                send(machine.token(), batch).andExpect(status().isOk());
            }
            send(machine.token(), batch)
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.error.code").value("KAY_SEC_004"))
                    .andExpect(jsonPath("$.error.errorValues.limit").value(120));
        }

        @Test
        void rateLimit_isPerTokenSoAnotherMachineIsNotAffected() throws Exception {
            Tenant tenant = newTenant();
            Machine busy = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            Machine quiet = registerMachine(tenant, signIn(tenant, "bo"), "desktop");
            Map<String, Object> batch = batch(busy, sessionId(), remote(), events(1, 1));
            for (int sent = 1; sent <= 121; sent++) {
                send(busy.token(), batch);
            }

            send(busy.token(), batch).andExpect(status().isTooManyRequests());
            send(quiet.token(), batch(quiet, sessionId(), remote(), events(1, 1))).andExpect(status().isOk());
        }
    }

    static Stream<Arguments> invalidEvents() {
        return Stream.of(
                invalid("seq zero", mutate(event(2, "Stop"), "seq", 0), "seq"),
                invalid("seq negative", mutate(event(2, "Stop"), "seq", -4), "seq"),
                invalid("seq text", mutate(event(2, "Stop"), "seq", "2"), "seq"),
                invalid("seq fraction", mutate(event(2, "Stop"), "seq", 2.5), "seq"),
                invalid("seq missing", without(event(2, "Stop"), "seq"), "seq"),
                invalid("at not a time", mutate(event(2, "Stop"), "at", "yesterday"), "at"),
                invalid("at missing", without(event(2, "Stop"), "at"), "at"),
                invalid("at a number", mutate(event(2, "Stop"), "at", 1760000000), "at"),
                invalid("event blank", mutate(event(2, "Stop"), "event", "  "), "event"),
                invalid("event missing", without(event(2, "Stop"), "event"), "event"),
                invalid("event over 40 characters", mutate(event(2, "Stop"), "event", "E".repeat(41)), "event"),
                invalid("agent_id a number", mutate(event(2, "Stop"), "agent_id", 7), "agent_id"),
                invalid("agent_id over 100 characters", mutate(event(2, "Stop"), "agent_id", "a".repeat(101)), "agent_id"));
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("session id with a slash", "sessionId", "a/b"),
                Arguments.of("session id over 100 characters", "sessionId", "s".repeat(101)),
                Arguments.of("session id blank", "sessionId", ""),
                Arguments.of("handoff content empty", "handoffs",
                        List.of(Map.of("path", "f/a.md", "content", "", "capturedAt", "2026-10-20T10:00:00Z"))),
                Arguments.of("handoff content over 200000 characters", "handoffs",
                        List.of(Map.of("path", "f/a.md", "content", "x".repeat(200_001), "capturedAt", "2026-10-20T10:00:00Z"))),
                Arguments.of("handoff without capture time", "handoffs",
                        List.of(Map.of("path", "f/a.md", "content", "x"))));
    }

    private static Arguments invalid(String name, Map<String, Object> event, String field) {
        return Arguments.of(name, event, field);
    }

    private static Map<String, Object> mutate(Map<String, Object> event, String key, Object value) {
        event.put(key, value);
        return event;
    }

    private static Map<String, Object> without(Map<String, Object> event, String key) {
        event.remove(key);
        return event;
    }

    @Nested
    class UsageOnlyBatch {

        @Test
        void unknownSession_acknowledgesZeroAndCreatesNoSession() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            Map<String, Object> usageOnly = batch(machine, session, remote(), List.of());
            long now = Instant.now().getEpochSecond();
            usageOnly.put("usage", usage(Instant.now(), 23.5, now + 3600, 41, now + 86400));

            send(machine.token(), usageOnly)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.acknowledgedSeq").value(0));

            assertEquals(0L, sessionCount(session));
            assertEquals(1L, count("SELECT count(*) FROM kaya.machine_usage_snapshots WHERE machine_id = ?::uuid", machine.machineId()));
        }

        @Test
        void knownSession_acknowledgesTheHighestSeqHeld() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            String remote = remote();
            send(machine.token(), batch(machine, session, remote, events(1, 5))).andExpect(status().isOk());
            Map<String, Object> usageOnly = batch(machine, session, remote, List.of());
            long now = Instant.now().getEpochSecond();
            usageOnly.put("usage", usage(Instant.now(), 10, now + 3600, 20, now + 86400));

            send(machine.token(), usageOnly)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.acknowledgedSeq").value(5));

            assertEquals(5L, eventCount(session));
        }
    }

    @Nested
    class TenantIsolation {

        @Test
        void sameSessionIdInTwoOrganizations_isTwoSessionsEachInItsOwnOrganization() throws Exception {
            Tenant first = newTenant();
            Tenant second = newTenant();
            Machine inFirst = registerMachine(first, signIn(first, "ada"), "laptop");
            Machine inSecond = registerMachine(second, signIn(second, "bo"), "laptop");
            String session = sessionId();
            String remote = remote();

            send(inFirst.token(), batch(inFirst, session, remote, events(1, 2))).andExpect(status().isOk());
            send(inSecond.token(), batch(inSecond, session, remote, events(1, 4)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.acknowledgedSeq").value(4));

            assertEquals(2L, sessionCount(session));
            assertEquals(1L, count("SELECT count(*) FROM kaya.sessions WHERE session_id = ? AND organization_id = ?::uuid AND event_count = 2",
                    session, first.id()));
            assertEquals(1L, count("SELECT count(*) FROM kaya.sessions WHERE session_id = ? AND organization_id = ?::uuid AND event_count = 4",
                    session, second.id()));
            assertEquals(2L, count("SELECT count(DISTINCT repository_id) FROM kaya.sessions WHERE session_id = ?", session));
        }

        @Test
        void tokenOfOneOrganization_neverWritesAUserMachineOrRepositoryOfAnother() throws Exception {
            Tenant first = newTenant();
            Tenant second = newTenant();
            Machine inFirst = registerMachine(first, signIn(first, "ada"), "laptop");
            Person inSecondPerson = signIn(second, "bo");
            String session = sessionId();
            addGitEmail(first, inFirst.owner().userId(), "ada.git@" + first.domain());
            Map<String, Object> batch = batch(inFirst, session, remote(), events(1, 3));
            section(batch, "summary").put("gitAuthorEmail", "ada.git@" + first.domain());
            section(batch, "machine").put("account", account("ada.claude@" + first.domain()));

            send(inFirst.token(), batch).andExpect(status().isOk());

            Map<String, Object> sessionRow = sessionRowOf(session);
            assertEquals(first.id(), sessionRow.get("organization_id").toString());
            assertEquals(0L, count("SELECT count(*) FROM kaya.sessions WHERE organization_id = ?::uuid", second.id()));
            assertEquals(0L, count("SELECT count(*) FROM kaya.repositories WHERE organization_id = ?::uuid", second.id()));
            assertEquals(0L, count("SELECT count(*) FROM kaya.user_git_emails WHERE organization_id = ?::uuid AND source = 'RECORDER'",
                    second.id()));
            assertEquals(0L, count("SELECT count(*) FROM kaya.accounts WHERE organization_id = ?::uuid", second.id()));
            assertEquals(0L, count("SELECT count(*) FROM kaya.machine_usage_snapshots WHERE organization_id = ?::uuid", second.id()));
            assertEquals(1L, count("SELECT count(*) FROM shrine.organization_members WHERE user_id = ?::uuid AND organization_id = ?::uuid",
                    inSecondPerson.userId(), second.id()));
        }

        @Test
        void sameGitEmailInTwoOrganizations_resolvesToEachTokensOwnUserWithoutAConflict() throws Exception {
            Tenant first = newTenant();
            Tenant second = newTenant();
            Machine inFirst = registerMachine(first, signIn(first, "ada"), "laptop");
            Machine inSecond = registerMachine(second, signIn(second, "bo"), "laptop");
            String email = "shared.dev@elsewhere.example";
            addGitEmail(first, inFirst.owner().userId(), email);
            addGitEmail(second, inSecond.owner().userId(), email);
            for (Machine machine : List.of(inFirst, inSecond)) {
                String session = sessionId();
                Map<String, Object> batch = batch(machine, session, remote(), events(1, 1));
                section(batch, "summary").put("gitAuthorEmail", email);

                send(machine.token(), batch).andExpect(status().isOk());

                Map<String, Object> row = sessionRowOf(session);
                assertEquals(machine.owner().userId(), row.get("user_id").toString());
                assertNull(row.get("identity_conflict_user_id"));
            }
        }

        @Test
        void retiredMachineOfOneOrganization_cannotWriteAnywhere() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            Machine machine = registerMachine(tenant, ada, "laptop");
            mockMvc.perform(in(tenant, patch("/kaya/machines/" + machine.machineId()), ada.jwt(), "{\"status\":\"RETIRED\"}"))
                    .andExpect(status().isOk());
            String session = sessionId();

            send(machine.token(), batch(machine, session, remote(), events(1, 1))).andExpect(status().isUnauthorized());

            assertEquals(0L, sessionCount(session));
        }
    }
}
