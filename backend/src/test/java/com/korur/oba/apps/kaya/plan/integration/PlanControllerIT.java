package com.korur.oba.apps.kaya.plan.integration;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// 2. Third-party

// 3. Java SDK
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.identity.helper.UserDisplayNameHelper;
import com.korur.oba.apps.kaya.support.AbstractIngestIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlanControllerIT extends AbstractIngestIT {

    private static final String SPEC_PATH = "docs/private/plans/kaya/04_KAYA_PLANS.md";
    private static final String SPEC_CONTENT = "# Kaya plans\n\n## 1. Summary\n\nPlans are reviewed in Kaya.\n";
    private static final String SPEC_CONTENT_REVISED = SPEC_CONTENT + "\n## 2. Stories\n";
    private static final String PLANNING_PATH = "kaya-plans/planning.md";
    private static final String PLANNING_CONTENT = "# Planning\n\n**Status:** DONE\n\n## Done so far\n\nSpec: `"
            + SPEC_PATH + "`.\n";
    private static final String UNIT_PATH = "kaya-plans/backend/member-api.md";
    private static final String UNIT_CONTENT = "# Member API\n\n**Status:** PARTIAL\n\n## Scope given\n\nRole: coding-engineer. "
            + "Task: unit 4 of the spec.\n\n## Next step\n\nunit 9 is not this one.\n";

    /** One organization with a recorded spec plan written by Ada's session. */
    private record Fixture(Tenant tenant, Machine ada, Person bo, String session, String remote, String planId) {

        RequestPostProcessor adaJwt() {
            return ada.owner().jwt();
        }
    }

    private Fixture newFixture() throws Exception {
        Tenant tenant = newTenant();
        Machine ada = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
        Person bo = signIn(tenant, "bo");
        String session = sessionId();
        String remote = remote();
        Map<String, Object> batch = batch(ada, session, remote, events(1, 1));
        batch.put("handoffs", List.of(
                Map.of("path", PLANNING_PATH, "content", PLANNING_CONTENT, "capturedAt", "2026-10-25T08:59:00Z"),
                Map.of("path", UNIT_PATH, "content", UNIT_CONTENT, "capturedAt", "2026-10-25T08:59:30Z")));
        batch.put("plans", List.of(spec(SPEC_CONTENT, "2026-10-25T09:00:00Z")));
        send(ada.token(), batch).andExpect(status().isOk());
        String planId = jdbcTemplate.queryForObject("""
                SELECT p.id::text FROM kaya.plans p JOIN kaya.sessions s ON s.repository_id = p.repository_id
                WHERE s.session_id = ? AND p.path = ?
                """, String.class, session, SPEC_PATH);
        return new Fixture(tenant, ada, bo, session, remote, planId);
    }

    private static Map<String, Object> spec(String content, String capturedAt) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("kind", "SPEC");
        plan.put("source", "FILE");
        plan.put("path", SPEC_PATH);
        plan.put("content", content);
        plan.put("capturedAt", capturedAt);
        return plan;
    }

    /** Ada's session writes the spec again with new content, which is version 2. */
    private void reviseSpec(Fixture fixture) throws Exception {
        Map<String, Object> batch = batch(fixture.ada(), fixture.session(), fixture.remote(), events(2, 2));
        batch.put("plans", List.of(spec(SPEC_CONTENT_REVISED, "2026-10-25T09:10:00Z")));
        send(fixture.ada().token(), batch).andExpect(status().isOk());
    }

    private static String feedback(int versionNumber, String decision, String comment, Integer lineStart, Integer lineEnd) {
        StringBuilder body = new StringBuilder("{\"versionNumber\":").append(versionNumber)
                .append(",\"decision\":\"").append(decision).append('"');
        if (comment != null) {
            body.append(",\"comment\":\"").append(comment).append('"');
        }
        if (lineStart != null) {
            body.append(",\"lineStart\":").append(lineStart);
        }
        if (lineEnd != null) {
            body.append(",\"lineEnd\":").append(lineEnd);
        }
        return body.append('}').toString();
    }

    private MvcResult give(Fixture fixture, RequestPostProcessor jwt, String body, int expectedStatus) throws Exception {
        return mockMvc.perform(in(fixture.tenant(), post("/kaya/plans/" + fixture.planId() + "/feedback"), jwt, body))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    private Map<String, Object> planRow(String planId) {
        return jdbcTemplate.queryForMap("SELECT review_state::text AS review_state, open_feedback_count FROM kaya.plans WHERE id = ?::uuid",
                planId);
    }

    @Nested
    class Reads {

        @Test
        void getPlans_specRecorded_listsItWithNames() throws Exception {
            Fixture fixture = newFixture();

            mockMvc.perform(in(fixture.tenant(), get("/kaya/plans"), fixture.adaJwt(), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value(fixture.planId()))
                    .andExpect(jsonPath("$.data[0].kind").value("SPEC"))
                    .andExpect(jsonPath("$.data[0].title").value("Kaya plans"))
                    .andExpect(jsonPath("$.data[0].path").value(SPEC_PATH))
                    .andExpect(jsonPath("$.data[0].repository.name").value("scratch"))
                    .andExpect(jsonPath("$.data[0].user.id").value(fixture.ada().owner().userId()))
                    .andExpect(jsonPath("$.data[0].pack").doesNotExist())
                    .andExpect(jsonPath("$.data[0].reviewState").value("OPEN"))
                    .andExpect(jsonPath("$.data[0].latestVersionNumber").value(1))
                    .andExpect(jsonPath("$.data[0].openFeedbackCount").value(0));
        }

        @Test
        void getPlans_filters_narrowTheList() throws Exception {
            Fixture fixture = newFixture();

            mockMvc.perform(in(fixture.tenant(), get("/kaya/plans").param("kind", "SESSION"), fixture.adaJwt(), null))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
            mockMvc.perform(in(fixture.tenant(), get("/kaya/plans").param("reviewState", "APPROVED"), fixture.adaJwt(), null))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
            mockMvc.perform(in(fixture.tenant(), get("/kaya/plans").param("search", "04_kaya"), fixture.adaJwt(), null))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
            mockMvc.perform(in(fixture.tenant(), get("/kaya/plans").param("search", "04%kaya"), fixture.adaJwt(), null))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
            mockMvc.perform(in(fixture.tenant(), get("/kaya/plans").param("sessionId", fixture.session()), fixture.adaJwt(), null))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
            mockMvc.perform(in(fixture.tenant(), get("/kaya/plans").param("userId", fixture.bo().userId()), fixture.adaJwt(), null))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        void getPlans_amountAboveHundred_returns422() throws Exception {
            Fixture fixture = newFixture();

            mockMvc.perform(in(fixture.tenant(), get("/kaya/plans").param("amount", "101"), fixture.adaJwt(), null))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));
        }

        @Test
        void getFilters_specRecorded_returnsRepositoryAndUser() throws Exception {
            Fixture fixture = newFixture();

            mockMvc.perform(in(fixture.tenant(), get("/kaya/plans/filters"), fixture.adaJwt(), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.repositories.length()").value(1))
                    .andExpect(jsonPath("$.data.repositories[0].name").value("scratch"))
                    .andExpect(jsonPath("$.data.packs.length()").value(0))
                    .andExpect(jsonPath("$.data.users[0].id").value(fixture.ada().owner().userId()));
        }

        @Test
        void getPlan_specWithFeature_returnsVersionsHandoffsAndPlanningHandoff() throws Exception {
            Fixture fixture = newFixture();

            mockMvc.perform(in(fixture.tenant(), get("/kaya/plans/" + fixture.planId()), fixture.adaJwt(), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.feature").value("kaya-plans"))
                    .andExpect(jsonPath("$.data.latestSession.sessionId").value(fixture.session()))
                    .andExpect(jsonPath("$.data.latestSession.ended").value(false))
                    .andExpect(jsonPath("$.data.versions.length()").value(1))
                    .andExpect(jsonPath("$.data.versions[0].number").value(1))
                    .andExpect(jsonPath("$.data.versions[0].source").value("FILE"))
                    .andExpect(jsonPath("$.data.versions[0].sessionId").value(fixture.session()))
                    .andExpect(jsonPath("$.data.versions[0].lineCount").value(5))
                    .andExpect(jsonPath("$.data.feedback.length()").value(0))
                    .andExpect(jsonPath("$.data.handoffs.length()").value(2))
                    .andExpect(jsonPath("$.data.handoffs[0].path").value(UNIT_PATH))
                    .andExpect(jsonPath("$.data.handoffs[0].status").value("PARTIAL"))
                    .andExpect(jsonPath("$.data.handoffs[0].unitNumber").value(4))
                    .andExpect(jsonPath("$.data.handoffs[1].path").value(PLANNING_PATH))
                    .andExpect(jsonPath("$.data.handoffs[1].unitNumber").doesNotExist())
                    .andExpect(jsonPath("$.data.planningHandoffPath").value(PLANNING_PATH))
                    .andExpect(jsonPath("$.data.canApprove").value(true));
        }

        @Test
        void getPlan_handoffOfTheFeatureFromAnotherSession_showsTheNewestSnapshot() throws Exception {
            Fixture fixture = newFixture();
            String later = sessionId();
            Map<String, Object> batch = batch(fixture.ada(), later, fixture.remote(), events(1, 1));
            batch.put("handoffs", List.of(Map.of("path", UNIT_PATH,
                    "content", UNIT_CONTENT.replace("PARTIAL", "DONE"), "capturedAt", "2026-10-25T10:00:00Z")));
            send(fixture.ada().token(), batch).andExpect(status().isOk());

            mockMvc.perform(in(fixture.tenant(), get("/kaya/plans/" + fixture.planId()), fixture.adaJwt(), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.handoffs.length()").value(2))
                    .andExpect(jsonPath("$.data.handoffs[0].status").value("DONE"));
        }

        @Test
        void getPlan_canApprove_followsUserAdminAndOwner() throws Exception {
            Fixture fixture = newFixture();
            String path = "/kaya/plans/" + fixture.planId();

            mockMvc.perform(in(fixture.tenant(), get(path), fixture.bo().jwt(), null))
                    .andExpect(jsonPath("$.data.canApprove").value(false));
            mockMvc.perform(in(fixture.tenant(), get(path), jwtOf(fixture.tenant().admin().getUser()), null))
                    .andExpect(jsonPath("$.data.canApprove").value(true));
            mockMvc.perform(in(fixture.tenant(), get(path), jwtOf(fixture.tenant().owner().getUser()), null))
                    .andExpect(jsonPath("$.data.canApprove").value(true));
        }

        @Test
        void getVersionAndDiff_secondVersion_returnContentAndDiff() throws Exception {
            Fixture fixture = newFixture();
            reviseSpec(fixture);
            String path = "/kaya/plans/" + fixture.planId() + "/versions/";

            mockMvc.perform(in(fixture.tenant(), get(path + "2"), fixture.adaJwt(), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.number").value(2))
                    .andExpect(jsonPath("$.data.content").value(SPEC_CONTENT_REVISED));
            MvcResult diff = mockMvc.perform(in(fixture.tenant(), get(path + "2/diff"), fixture.adaJwt(), null))
                    .andExpect(status().isOk())
                    .andReturn();
            String text = read(diff, "$.data.diff");
            assertTrue(text.startsWith("--- a/" + SPEC_PATH), text);
            assertTrue(text.contains("+## 2. Stories"), text);
            mockMvc.perform(in(fixture.tenant(), get(path + "1/diff"), fixture.adaJwt(), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.diff").value(""));
        }

        @Test
        void getVersion_unknownNumber_returns404() throws Exception {
            Fixture fixture = newFixture();

            mockMvc.perform(in(fixture.tenant(), get("/kaya/plans/" + fixture.planId() + "/versions/3"), fixture.adaJwt(), null))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
        }

        @Test
        void getPlan_otherOrganization_returns404() throws Exception {
            Fixture fixture = newFixture();
            Tenant other = newTenant();
            RequestPostProcessor otherAdmin = jwtOf(other.admin().getUser());

            mockMvc.perform(in(other, get("/kaya/plans/" + fixture.planId()), otherAdmin, null))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
            mockMvc.perform(in(other, post("/kaya/plans/" + fixture.planId() + "/feedback"), otherAdmin,
                            feedback(1, "COMMENT", "Looks fine", null, null)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
            mockMvc.perform(in(other, get("/kaya/plans"), otherAdmin, null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        void getPlan_malformedId_returns400() throws Exception {
            Fixture fixture = newFixture();

            mockMvc.perform(in(fixture.tenant(), get("/kaya/plans/not-a-uuid"), fixture.adaJwt(), null))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Feedback {

        @Test
        void giveFeedback_requestChangesOnLines_setsStateAndCountsIt() throws Exception {
            Fixture fixture = newFixture();

            MvcResult result = give(fixture, fixture.bo().jwt(), feedback(1, "REQUEST_CHANGES", "Split the summary", 3, 5), 201);

            assertEquals("1", read(result, "$.data.versionNumber"));
            assertEquals(fixture.bo().user().getId().toString(), read(result, "$.data.reviewer.id"));
            assertEquals("3", read(result, "$.data.lineStart"));
            Map<String, Object> plan = planRow(fixture.planId());
            assertEquals("CHANGES_REQUESTED", plan.get("review_state"));
            assertEquals(1, plan.get("open_feedback_count"));
            mockMvc.perform(in(fixture.tenant(), get("/kaya/plans/" + fixture.planId()), fixture.adaJwt(), null))
                    .andExpect(jsonPath("$.data.feedback.length()").value(1))
                    .andExpect(jsonPath("$.data.feedback[0].decision").value("REQUEST_CHANGES"))
                    .andExpect(jsonPath("$.data.feedback[0].reviewer.name").value(UserDisplayNameHelper.displayNameOf(fixture.bo().user())))
                    .andExpect(jsonPath("$.data.feedback[0].deliveredAt").doesNotExist());
        }

        @Test
        void giveFeedback_commentKeepsState_approveByUserApproves() throws Exception {
            Fixture fixture = newFixture();

            give(fixture, fixture.bo().jwt(), feedback(1, "COMMENT", "Nice", null, null), 201);
            assertEquals("OPEN", planRow(fixture.planId()).get("review_state"));

            give(fixture, fixture.adaJwt(), feedback(1, "APPROVE", null, null, null), 201);
            Map<String, Object> plan = planRow(fixture.planId());
            assertEquals("APPROVED", plan.get("review_state"));
            assertEquals(2, plan.get("open_feedback_count"));
        }

        @Test
        void giveFeedback_approveByAdminAndOwner_isAllowed() throws Exception {
            Fixture fixture = newFixture();

            give(fixture, jwtOf(fixture.tenant().admin().getUser()), feedback(1, "APPROVE", null, null, null), 201);
            give(fixture, jwtOf(fixture.tenant().owner().getUser()), feedback(1, "APPROVE", "Go", null, null), 201);

            assertEquals("APPROVED", planRow(fixture.planId()).get("review_state"));
        }

        @Test
        void giveFeedback_approveByOtherMember_returns403() throws Exception {
            Fixture fixture = newFixture();

            MvcResult result = give(fixture, fixture.bo().jwt(), feedback(1, "APPROVE", null, null, null), 403);

            assertEquals("GEN_SEC_003", read(result, "$.error.code"));
            assertEquals("OPEN", planRow(fixture.planId()).get("review_state"));
        }

        @Test
        void giveFeedback_decisionOnOldVersion_returns409AndCommentIsAccepted() throws Exception {
            Fixture fixture = newFixture();
            reviseSpec(fixture);

            MvcResult refused = give(fixture, fixture.bo().jwt(), feedback(1, "REQUEST_CHANGES", "Old", null, null), 409);
            assertEquals("KAY_PLN_001", read(refused, "$.error.code"));
            give(fixture, fixture.adaJwt(), feedback(1, "APPROVE", null, null, null), 409);

            give(fixture, fixture.bo().jwt(), feedback(1, "COMMENT", "Still holds", 1, 1), 201);
            assertEquals("OPEN", planRow(fixture.planId()).get("review_state"));
        }

        @Test
        void giveFeedback_invalidCommentOrLines_returns422() throws Exception {
            Fixture fixture = newFixture();

            assertEquals("KAY_PLN_002", read(give(fixture, fixture.bo().jwt(),
                    feedback(1, "COMMENT", null, null, null), 422), "$.error.code"));
            assertEquals("KAY_PLN_002", read(give(fixture, fixture.bo().jwt(),
                    feedback(1, "REQUEST_CHANGES", null, 1, 2), 422), "$.error.code"));
            assertEquals("KAY_PLN_002", read(give(fixture, fixture.bo().jwt(),
                    feedback(1, "COMMENT", "Lines", 2, null), 422), "$.error.code"));
            assertEquals("KAY_PLN_002", read(give(fixture, fixture.bo().jwt(),
                    feedback(1, "COMMENT", "Lines", 4, 2), 422), "$.error.code"));
            assertEquals("KAY_PLN_002", read(give(fixture, fixture.bo().jwt(),
                    feedback(1, "COMMENT", "Lines", 5, 6), 422), "$.error.code"));
            assertEquals("GEN_VAL_001", read(give(fixture, fixture.bo().jwt(),
                    feedback(1, "COMMENT", "", null, null), 422), "$.error.code"));
            assertEquals("GEN_VAL_001", read(give(fixture, fixture.bo().jwt(),
                    feedback(1, "COMMENT", "Lines", 0, 1), 422), "$.error.code"));
            assertEquals(0, planRow(fixture.planId()).get("open_feedback_count"));
        }

        @Test
        void giveFeedback_unknownVersion_returns404() throws Exception {
            Fixture fixture = newFixture();

            MvcResult result = give(fixture, fixture.bo().jwt(), feedback(2, "COMMENT", "Ahead", null, null), 404);

            assertEquals("KAY_NF_001", read(result, "$.error.code"));
        }
    }

    @Nested
    class Withdraw {

        private String withdrawPath(Fixture fixture, String feedbackId) {
            return "/kaya/plans/" + fixture.planId() + "/feedback/" + feedbackId;
        }

        @Test
        void withdrawFeedback_ownPendingDecision_recomputesState() throws Exception {
            Fixture fixture = newFixture();
            give(fixture, fixture.adaJwt(), feedback(1, "APPROVE", null, null, null), 201);
            String changes = read(give(fixture, fixture.bo().jwt(), feedback(1, "REQUEST_CHANGES", "No", null, null), 201),
                    "$.data.id");
            assertEquals("CHANGES_REQUESTED", planRow(fixture.planId()).get("review_state"));

            mockMvc.perform(in(fixture.tenant(), delete(withdrawPath(fixture, changes)), fixture.bo().jwt(), null))
                    .andExpect(status().isOk());

            Map<String, Object> plan = planRow(fixture.planId());
            assertEquals("APPROVED", plan.get("review_state"));
            assertEquals(1, plan.get("open_feedback_count"));
            assertEquals(0, count("SELECT count(*) FROM kaya.plan_feedback WHERE id = ?::uuid", changes));
        }

        @Test
        void withdrawFeedback_onlyDecision_returnsStateToOpen() throws Exception {
            Fixture fixture = newFixture();
            String approval = read(give(fixture, fixture.adaJwt(), feedback(1, "APPROVE", null, null, null), 201), "$.data.id");

            mockMvc.perform(in(fixture.tenant(), delete(withdrawPath(fixture, approval)), fixture.adaJwt(), null))
                    .andExpect(status().isOk());

            Map<String, Object> plan = planRow(fixture.planId());
            assertEquals("OPEN", plan.get("review_state"));
            assertEquals(0, plan.get("open_feedback_count"));
        }

        @Test
        void withdrawFeedback_anotherMembersFeedback_returns403() throws Exception {
            Fixture fixture = newFixture();
            String comment = read(give(fixture, fixture.bo().jwt(), feedback(1, "COMMENT", "Mine", null, null), 201), "$.data.id");

            mockMvc.perform(in(fixture.tenant(), delete(withdrawPath(fixture, comment)), fixture.adaJwt(), null))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("GEN_SEC_003"));
        }

        @Test
        void withdrawFeedback_delivered_returns409() throws Exception {
            Fixture fixture = newFixture();
            String comment = read(give(fixture, fixture.bo().jwt(), feedback(1, "COMMENT", "Sent", null, null), 201), "$.data.id");
            jdbcTemplate.update("UPDATE kaya.plan_feedback SET delivered_at = now(), delivered_session_id = ? WHERE id = ?::uuid",
                    fixture.session(), comment);

            mockMvc.perform(in(fixture.tenant(), delete(withdrawPath(fixture, comment)), fixture.bo().jwt(), null))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("KAY_PLN_003"));
        }

        @Test
        void withdrawFeedback_unknownFeedback_returns404() throws Exception {
            Fixture fixture = newFixture();

            mockMvc.perform(in(fixture.tenant(), delete(withdrawPath(fixture, UUID.randomUUID().toString())),
                            fixture.bo().jwt(), null))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
        }
    }
}
