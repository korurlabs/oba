package com.korur.oba.apps.kaya.plan.integration;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

// 2. Third-party

// 3. Java SDK
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractIngestIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlanIngestIT extends AbstractIngestIT {

    private static final String SPEC_PATH = "docs/private/plans/kaya/04_KAYA_PLANS.md";
    private static final String SPEC_CONTENT = "# Kaya plans\n\n## 1. Summary\n\nPlans are reviewed in Kaya.\n";
    private static final String SPEC_CONTENT_REVISED = SPEC_CONTENT + "\n## 2. Stories\n";
    private static final String SESSION_PLAN = "1. **Goal**: Store plans from the recorder.\n2. **Steps**:\n   1. Add it.\n";

    private Machine newMachine() throws Exception {
        Tenant tenant = newTenant();
        return registerMachine(tenant, signIn(tenant, "ada"), "laptop");
    }

    private static Map<String, Object> plan(String kind, String source, String path, String content, String capturedAt) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("kind", kind);
        plan.put("source", source);
        plan.put("path", path);
        plan.put("content", content);
        plan.put("capturedAt", capturedAt);
        return plan;
    }

    private static Map<String, Object> spec(String path, String content, String capturedAt) {
        return plan("SPEC", "FILE", path, content, capturedAt);
    }

    private static Map<String, Object> withPlans(Map<String, Object> batch, List<Map<String, Object>> plans) {
        batch.put("plans", plans);
        return batch;
    }

    private static Map<String, Object> withHandoff(Map<String, Object> batch, String path, String content, String capturedAt) {
        batch.put("handoffs", List.of(Map.of("path", path, "content", content, "capturedAt", capturedAt)));
        return batch;
    }

    private UUID repositoryOf(String sessionId) {
        return jdbcTemplate.queryForObject("SELECT repository_id FROM kaya.sessions WHERE session_id = ?", UUID.class,
                sessionId);
    }

    private UUID sessionRowOf(String sessionId) {
        return jdbcTemplate.queryForObject("SELECT id FROM kaya.sessions WHERE session_id = ?", UUID.class, sessionId);
    }

    private Map<String, Object> specRow(UUID repositoryId, String path) {
        return jdbcTemplate.queryForMap("""
                SELECT p.id, p.title, p.review_state::text AS review_state, p.latest_version_number, p.latest_session_id,
                       p.user_id, p.pack_id, p.feature, p.organization_id
                FROM kaya.plans p WHERE p.repository_id = ? AND p.path = ? AND p.kind = 'SPEC'
                """, repositoryId, path);
    }

    private List<Map<String, Object>> versionsOf(Object planId) {
        return jdbcTemplate.queryForList("""
                SELECT v.number, v.source::text AS source, v.session_id, v.content, v.content_sha256, v.line_count
                FROM kaya.plan_versions v WHERE v.plan_id = ? ORDER BY v.number
                """, planId);
    }

    /** Creates a pack of the repository's organization, a child of the parent when one is given. */
    private UUID insertPack(UUID repositoryId, String name, UUID parentPackId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO kaya.packs (organization_id, name, parent_pack_id)
                SELECT organization_id, ?, ? FROM kaya.repositories WHERE id = ? RETURNING id
                """, UUID.class, name, parentPackId, repositoryId);
    }

    private void usePack(UUID repositoryId, UUID packId) {
        jdbcTemplate.update("""
                INSERT INTO kaya.repository_packs (repository_id, pack_id, position)
                SELECT ?, ?, COALESCE(MAX(position) + 1, 0) FROM kaya.repository_packs WHERE repository_id = ?
                """, repositoryId, packId, repositoryId);
    }

    @Nested
    class Specs {

        @Test
        void storePlans_newSpecPath_createsPlanAndVersionOne() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();

            send(machine.token(), withPlans(batch(machine, session, remote(), events(1, 1)),
                    List.of(spec(SPEC_PATH, SPEC_CONTENT, "2026-10-25T09:00:00Z"))))
                    .andExpect(status().isOk());

            Map<String, Object> plan = specRow(repositoryOf(session), SPEC_PATH);
            assertEquals("Kaya plans", plan.get("title"));
            assertEquals("OPEN", plan.get("review_state"));
            assertEquals(1, plan.get("latest_version_number"));
            assertEquals(sessionRowOf(session), plan.get("latest_session_id"));
            assertEquals(machine.owner().userId(), plan.get("user_id").toString());
            assertNull(plan.get("pack_id"));
            List<Map<String, Object>> versions = versionsOf(plan.get("id"));
            assertEquals(1, versions.size());
            assertEquals("FILE", versions.getFirst().get("source"));
            assertEquals(SPEC_CONTENT, versions.getFirst().get("content"));
            assertEquals(5, versions.getFirst().get("line_count"));
            assertEquals(64, versions.getFirst().get("content_sha256").toString().length());
        }

        @Test
        void storePlans_specInThePlansDirectoryOfAUsedPackOrItsParent_isGivenThatPackOnEveryNewVersion() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            String remote = remote();
            String childSpec = "docs/claude/plans/kaya/01_PACKS.md";
            String parentSpec = "docs/claude/plans/org/01_ORG.md";
            String otherSpec = "docs/claude/plans/elsewhere/01_OTHER.md";
            send(machine.token(), withPlans(batch(machine, session, remote, events(1, 1)),
                    List.of(spec(childSpec, SPEC_CONTENT, "2026-10-25T09:00:00Z")))).andExpect(status().isOk());
            UUID repositoryId = repositoryOf(session);
            assertNull(specRow(repositoryId, childSpec).get("pack_id"), "the repository used no pack yet");
            UUID org = insertPack(repositoryId, "org", null);
            UUID kaya = insertPack(repositoryId, "kaya", org);
            usePack(repositoryId, kaya);

            send(machine.token(), withPlans(batch(machine, session, remote, events(2, 2)), List.of(
                    spec(childSpec, SPEC_CONTENT_REVISED, "2026-10-25T09:05:00Z"),
                    spec(parentSpec, SPEC_CONTENT, "2026-10-25T09:05:00Z"),
                    spec(otherSpec, SPEC_CONTENT, "2026-10-25T09:05:00Z")))).andExpect(status().isOk());

            assertEquals(kaya, specRow(repositoryId, childSpec).get("pack_id"));
            assertEquals(org, specRow(repositoryId, parentSpec).get("pack_id"));
            assertNull(specRow(repositoryId, otherSpec).get("pack_id"));
        }

        @Test
        void storePlans_sameContentAgain_addsNoVersion() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            String remote = remote();
            send(machine.token(), withPlans(batch(machine, session, remote, events(1, 1)),
                    List.of(spec(SPEC_PATH, SPEC_CONTENT, "2026-10-25T09:00:00Z")))).andExpect(status().isOk());

            send(machine.token(), withPlans(batch(machine, session, remote, events(2, 2)),
                    List.of(spec(SPEC_PATH, SPEC_CONTENT, "2026-10-25T09:05:00Z")))).andExpect(status().isOk());

            Map<String, Object> plan = specRow(repositoryOf(session), SPEC_PATH);
            assertEquals(1, plan.get("latest_version_number"));
            assertEquals(1, versionsOf(plan.get("id")).size());
        }

        @Test
        void storePlans_changedContentOnApprovedPlan_addsVersionTwoAndReopensReview() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            String remote = remote();
            send(machine.token(), withPlans(batch(machine, session, remote, events(1, 1)),
                    List.of(spec(SPEC_PATH, SPEC_CONTENT, "2026-10-25T09:00:00Z")))).andExpect(status().isOk());
            Object planId = specRow(repositoryOf(session), SPEC_PATH).get("id");
            jdbcTemplate.update("UPDATE kaya.plans SET review_state = 'APPROVED' WHERE id = ?", planId);

            send(machine.token(), withPlans(batch(machine, session, remote, events(2, 2)),
                    List.of(spec(SPEC_PATH, SPEC_CONTENT_REVISED, "2026-10-25T09:05:00Z")))).andExpect(status().isOk());

            Map<String, Object> plan = specRow(repositoryOf(session), SPEC_PATH);
            assertEquals("OPEN", plan.get("review_state"));
            assertEquals(2, plan.get("latest_version_number"));
            List<Map<String, Object>> versions = versionsOf(planId);
            assertEquals(2, versions.size());
            assertEquals(SPEC_CONTENT_REVISED, versions.get(1).get("content"));
        }

        @Test
        void storePlans_olderCaptureAfterNewerOne_addsNoVersion() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            String remote = remote();
            send(machine.token(), withPlans(batch(machine, session, remote, events(1, 1)),
                    List.of(spec(SPEC_PATH, SPEC_CONTENT_REVISED, "2026-10-25T09:05:00Z")))).andExpect(status().isOk());

            send(machine.token(), withPlans(batch(machine, session, remote, events(2, 2)),
                    List.of(spec(SPEC_PATH, SPEC_CONTENT, "2026-10-25T09:00:00Z")))).andExpect(status().isOk());

            assertEquals(1, specRow(repositoryOf(session), SPEC_PATH).get("latest_version_number"));
        }

        @Test
        void storePlans_secondSessionWritesTheSameSpec_joinsTheSamePlan() throws Exception {
            Tenant tenant = newTenant();
            Machine ada = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            Machine bo = registerMachine(tenant, signIn(tenant, "bo"), "desktop");
            String remote = remote();
            String first = sessionId();
            String second = sessionId();
            send(ada.token(), withPlans(batch(ada, first, remote, events(1, 1)),
                    List.of(spec(SPEC_PATH, SPEC_CONTENT, "2026-10-25T09:00:00Z")))).andExpect(status().isOk());

            send(bo.token(), withPlans(batch(bo, second, remote, events(1, 1)),
                    List.of(spec(SPEC_PATH, SPEC_CONTENT_REVISED, "2026-10-25T09:10:00Z")))).andExpect(status().isOk());

            UUID repositoryId = repositoryOf(first);
            assertEquals(repositoryId, repositoryOf(second));
            assertEquals(1, count("SELECT count(*) FROM kaya.plans WHERE repository_id = ? AND path = ?", repositoryId, SPEC_PATH));
            Map<String, Object> plan = specRow(repositoryId, SPEC_PATH);
            assertEquals(2, plan.get("latest_version_number"));
            assertEquals(sessionRowOf(second), plan.get("latest_session_id"));
            assertEquals(bo.owner().userId(), plan.get("user_id").toString());
            List<Map<String, Object>> versions = versionsOf(plan.get("id"));
            assertEquals(sessionRowOf(first), versions.get(0).get("session_id"));
            assertEquals(sessionRowOf(second), versions.get(1).get("session_id"));
        }

        @Test
        void storePlans_withoutHeading_titlesTheSpecByItsFileName() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();

            send(machine.token(), withPlans(batch(machine, session, remote(), events(1, 1)),
                    List.of(spec("docs/plans/07_EXPORT.md", "No heading here.\n", "2026-10-25T09:00:00Z"))))
                    .andExpect(status().isOk());

            assertEquals("07_EXPORT.md", specRow(repositoryOf(session), "docs/plans/07_EXPORT.md").get("title"));
        }
    }

    @Nested
    class SessionPlans {

        @Test
        void storePlans_sessionPlanCaptures_formOnePlanWithAVersionPerNewContent() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            String remote = remote();
            send(machine.token(), withPlans(batch(machine, session, remote, events(1, 1)),
                    List.of(plan("SESSION", "PLAN_MODE", null, SESSION_PLAN, "2026-10-25T09:00:00Z"))))
                    .andExpect(status().isOk());

            send(machine.token(), withPlans(batch(machine, session, remote, events(2, 2)), List.of(
                    plan("SESSION", "MESSAGE", null, SESSION_PLAN, "2026-10-25T09:01:00Z"),
                    plan("SESSION", "MESSAGE", null, "## Goal\n\nShip the plan ingest.\n\n## Steps\n1. Do it.\n",
                            "2026-10-25T09:02:00Z"))))
                    .andExpect(status().isOk());

            List<Map<String, Object>> plans = jdbcTemplate.queryForList("""
                    SELECT p.id, p.title, p.latest_version_number, p.path FROM kaya.plans p WHERE p.session_id = ?
                    """, sessionRowOf(session));
            assertEquals(1, plans.size());
            assertEquals("Ship the plan ingest.", plans.getFirst().get("title"));
            assertEquals(2, plans.getFirst().get("latest_version_number"));
            assertNull(plans.getFirst().get("path"));
            List<Map<String, Object>> versions = versionsOf(plans.getFirst().get("id"));
            assertEquals("PLAN_MODE", versions.get(0).get("source"));
            assertEquals("MESSAGE", versions.get(1).get("source"));
        }

        @Test
        void storePlans_goalLine_titlesTheSessionPlan() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();

            send(machine.token(), withPlans(batch(machine, session, remote(), events(1, 1)),
                    List.of(plan("SESSION", "PLAN_MODE", null, SESSION_PLAN, "2026-10-25T09:00:00Z"))))
                    .andExpect(status().isOk());

            assertEquals("Store plans from the recorder.", jdbcTemplate.queryForObject(
                    "SELECT title FROM kaya.plans WHERE session_id = ?", String.class, sessionRowOf(session)));
        }

        @Test
        void storePlans_twoSessions_haveOneSessionPlanEach() throws Exception {
            Machine machine = newMachine();
            String remote = remote();
            String first = sessionId();
            String second = sessionId();

            send(machine.token(), withPlans(batch(machine, first, remote, events(1, 1)),
                    List.of(plan("SESSION", "PLAN_MODE", null, SESSION_PLAN, "2026-10-25T09:00:00Z")))).andExpect(status().isOk());
            send(machine.token(), withPlans(batch(machine, second, remote, events(1, 1)),
                    List.of(plan("SESSION", "PLAN_MODE", null, SESSION_PLAN, "2026-10-25T09:00:00Z")))).andExpect(status().isOk());

            assertEquals(1, count("SELECT count(*) FROM kaya.plans WHERE session_id = ?", sessionRowOf(first)));
            assertEquals(1, count("SELECT count(*) FROM kaya.plans WHERE session_id = ?", sessionRowOf(second)));
        }
    }

    @Nested
    class Features {

        private static final String PLANNING = "# Handoff: kaya-plans / planning\n\n**Status:** DONE\n\nSpec: `"
                + SPEC_PATH + "`.\n";

        @Test
        void storePlans_planningHandoffInTheSameBatch_linksTheFeature() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            Map<String, Object> batch = withHandoff(batch(machine, session, remote(), events(1, 1)),
                    "kaya-plans/planning.md", PLANNING, "2026-10-25T09:00:00Z");

            send(machine.token(), withPlans(batch, List.of(spec(SPEC_PATH, SPEC_CONTENT, "2026-10-25T09:00:00Z"))))
                    .andExpect(status().isOk());

            assertEquals("kaya-plans", specRow(repositoryOf(session), SPEC_PATH).get("feature"));
        }

        @Test
        void linkFeature_planningHandoffInALaterBatch_linksTheFeature() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            String remote = remote();
            send(machine.token(), withPlans(batch(machine, session, remote, events(1, 1)),
                    List.of(spec(SPEC_PATH, SPEC_CONTENT, "2026-10-25T09:00:00Z")))).andExpect(status().isOk());
            assertNull(specRow(repositoryOf(session), SPEC_PATH).get("feature"));

            send(machine.token(), withHandoff(batch(machine, session, remote, events(2, 2)), "kaya-plans/planning.md",
                    "Worktree copy: /repo/.claude/worktrees/x/" + SPEC_PATH + "\n", "2026-10-25T09:05:00Z"))
                    .andExpect(status().isOk());

            assertEquals("kaya-plans", specRow(repositoryOf(session), SPEC_PATH).get("feature"));
        }

        @Test
        void linkFeature_otherHandoffNamingTheSpec_linksNothing() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            String remote = remote();
            send(machine.token(), withPlans(batch(machine, session, remote, events(1, 1)),
                    List.of(spec(SPEC_PATH, SPEC_CONTENT, "2026-10-25T09:00:00Z")))).andExpect(status().isOk());

            send(machine.token(), withHandoff(batch(machine, session, remote, events(2, 2)), "kaya-plans/backend/ingest.md",
                    PLANNING, "2026-10-25T09:05:00Z")).andExpect(status().isOk());

            assertNull(specRow(repositoryOf(session), SPEC_PATH).get("feature"));
        }
    }

    @Nested
    class Refusals {

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.korur.oba.apps.kaya.plan.integration.PlanIngestIT#invalidPlans")
        void ingestBatch_invalidPlan_returns422AndStoresNothing(String name, Map<String, Object> plan) throws Exception {
            Machine machine = newMachine();
            String session = sessionId();

            send(machine.token(), withPlans(batch(machine, session, remote(), events(1, 1)), List.of(plan)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));

            assertEquals(0, sessionCount(session));
        }

        @Test
        void ingestBatch_twentyOnePlans_returns422() throws Exception {
            Machine machine = newMachine();
            List<Map<String, Object>> plans = new ArrayList<>();
            for (int index = 0; index < 21; index++) {
                plans.add(spec("docs/plans/p" + index + ".md", "# P\n", "2026-10-25T09:00:00Z"));
            }

            send(machine.token(), withPlans(batch(machine, sessionId(), remote(), events(1, 1)), plans))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));
        }
    }

    static Stream<Arguments> invalidPlans() {
        String at = "2026-10-25T09:00:00Z";
        return Stream.of(
                Arguments.of("spec path outside docs", spec("src/plans/a.md", "# A\n", at)),
                Arguments.of("spec path without a plans folder", spec("docs/specs/a.md", "# A\n", at)),
                Arguments.of("spec path not markdown", spec("docs/plans/a.txt", "# A\n", at)),
                Arguments.of("spec path with a worktree prefix", spec(".claude/worktrees/x/docs/plans/a.md", "# A\n", at)),
                Arguments.of("spec path with a parent segment", spec("docs/plans/../plans/a.md", "# A\n", at)),
                Arguments.of("spec path absolute", spec("/docs/plans/a.md", "# A\n", at)),
                Arguments.of("spec without path", spec(null, "# A\n", at)),
                Arguments.of("spec path over 500 characters", spec("docs/plans/" + "a".repeat(490) + ".md", "# A\n", at)),
                Arguments.of("session plan with a path", plan("SESSION", "PLAN_MODE", "docs/plans/a.md", "x", at)),
                Arguments.of("spec from plan mode", plan("SPEC", "PLAN_MODE", "docs/plans/a.md", "x", at)),
                Arguments.of("session plan from a file", plan("SESSION", "FILE", null, "x", at)),
                Arguments.of("content empty", spec("docs/plans/a.md", "", at)),
                Arguments.of("content over 200000 characters", spec("docs/plans/a.md", "x".repeat(200_001), at)),
                Arguments.of("without capture time", spec("docs/plans/a.md", "x", null)),
                Arguments.of("without kind", plan(null, "FILE", "docs/plans/a.md", "x", at)),
                Arguments.of("without source", plan("SPEC", null, "docs/plans/a.md", "x", at)));
    }
}
