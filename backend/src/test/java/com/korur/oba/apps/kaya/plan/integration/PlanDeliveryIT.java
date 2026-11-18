package com.korur.oba.apps.kaya.plan.integration;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 2. Third-party

// 3. Java SDK
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractIngestIT;

import static org.hamcrest.Matchers.endsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlanDeliveryIT extends AbstractIngestIT {

    private static final String PENDING = "/kaya/delivery/plan-feedback";
    private static final String ACKS = "/kaya/delivery/plan-feedback/acks";
    private static final String SPEC_PATH = "docs/private/plans/kaya/04_KAYA_PLANS.md";
    private static final String SPEC_CONTENT = "# Kaya plans\n\n## 1. Summary\n\nPlans are reviewed in Kaya.\n";
    private static final String BRANCH = "feature/plans";

    /** One organization with a spec written by Ada's session on a branch, and a comment of Bo on it. */
    private record Fixture(Tenant tenant, Machine ada, Person bo, String session, String remote, String planId,
            String feedbackId) {
    }

    private Fixture newFixture() throws Exception {
        Tenant tenant = newTenant();
        Machine ada = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
        Person bo = signIn(tenant, "bo");
        String session = sessionId();
        String remote = remote();
        Map<String, Object> batch = batch(ada, session, remote, events(1, 1));
        section(batch, "summary").put("branch", BRANCH);
        batch.put("plans", List.of(spec()));
        send(ada.token(), batch).andExpect(status().isOk());
        String planId = jdbcTemplate.queryForObject("""
                SELECT p.id::text FROM kaya.plans p JOIN kaya.sessions s ON s.repository_id = p.repository_id
                WHERE s.session_id = ? AND p.path = ?
                """, String.class, session, SPEC_PATH);
        MvcResult given = mockMvc.perform(in(tenant, post("/kaya/plans/" + planId + "/feedback"), bo.jwt(),
                        "{\"versionNumber\":1,\"decision\":\"REQUEST_CHANGES\",\"comment\":\"Split step 3\","
                                + "\"lineStart\":3,\"lineEnd\":4}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new Fixture(tenant, ada, bo, session, remote, planId, read(given, "$.data.id"));
    }

    private static Map<String, Object> spec() {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("kind", "SPEC");
        plan.put("source", "FILE");
        plan.put("path", SPEC_PATH);
        plan.put("content", SPEC_CONTENT);
        plan.put("capturedAt", "2026-10-25T09:00:00Z");
        return plan;
    }

    /** Ada's session reports that it has ended on the branch. */
    private void endSession(Fixture fixture) throws Exception {
        Map<String, Object> batch = batch(fixture.ada(), fixture.session(), fixture.remote(), events(2, 2));
        section(batch, "summary").put("branch", BRANCH);
        section(batch, "summary").put("ended", true);
        send(fixture.ada().token(), batch).andExpect(status().isOk());
    }

    private ResultActions machine(String token, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions ack(String token, String sessionId, String... feedbackIds) throws Exception {
        return machine(token, post(ACKS).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("sessionId", sessionId, "feedbackIds", List.of(feedbackIds)))));
    }

    private int openFeedbackCount(String planId) {
        Integer count = jdbcTemplate.queryForObject("SELECT open_feedback_count FROM kaya.plans WHERE id = ?::uuid",
                Integer.class, planId);
        assertNotNull(count);
        return count;
    }

    @Nested
    class PendingBySessions {

        @Test
        void getPending_sessionOfTheTokensUser_returnsTheFeedback() throws Exception {
            Fixture fixture = newFixture();

            machine(fixture.ada().token(), get(PENDING).param("sessionId", fixture.session()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value(fixture.feedbackId()))
                    .andExpect(jsonPath("$.data[0].planId").value(fixture.planId()))
                    .andExpect(jsonPath("$.data[0].planUrl").value(endsWith("/plans/" + fixture.planId())))
                    .andExpect(jsonPath("$.data[0].kind").value("SPEC"))
                    .andExpect(jsonPath("$.data[0].path").value(SPEC_PATH))
                    .andExpect(jsonPath("$.data[0].versionNumber").value(1))
                    .andExpect(jsonPath("$.data[0].latestVersionNumber").value(1))
                    .andExpect(jsonPath("$.data[0].sessionId").value(fixture.session()))
                    .andExpect(jsonPath("$.data[0].reviewerName").isNotEmpty())
                    .andExpect(jsonPath("$.data[0].decision").value("REQUEST_CHANGES"))
                    .andExpect(jsonPath("$.data[0].comment").value("Split step 3"))
                    .andExpect(jsonPath("$.data[0].lineStart").value(3))
                    .andExpect(jsonPath("$.data[0].lineEnd").value(4));
        }

        @Test
        void getPending_unknownSession_returnsEmpty() throws Exception {
            Fixture fixture = newFixture();

            machine(fixture.ada().token(), get(PENDING).param("sessionId", sessionId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        void getPending_tokenOfAnotherUser_returnsEmpty() throws Exception {
            Fixture fixture = newFixture();
            Machine boLaptop = registerMachine(fixture.tenant(), fixture.bo(), "bo-laptop");

            machine(boLaptop.token(), get(PENDING).param("sessionId", fixture.session()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        void getPending_tokenOfAnotherOrganization_returnsEmpty() throws Exception {
            Fixture fixture = newFixture();
            Tenant other = newTenant();
            Machine stranger = registerMachine(other, signIn(other, "eve"), "eve-laptop");

            machine(stranger.token(), get(PENDING).param("sessionId", fixture.session()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        void getPending_moreThanTwentySessions_returns422() throws Exception {
            Fixture fixture = newFixture();
            MockHttpServletRequestBuilder request = get(PENDING);
            for (int i = 0; i < 21; i++) {
                request.param("sessionId", sessionId());
            }

            machine(fixture.ada().token(), request)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));
        }
    }

    @Nested
    class PendingByBranch {

        @Test
        void getPending_latestSessionLive_returnsEmpty() throws Exception {
            Fixture fixture = newFixture();

            machine(fixture.ada().token(), get(PENDING).param("remoteUrl", fixture.remote()).param("branch", BRANCH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        void getPending_latestSessionEndedOnTheBranch_returnsTheFeedback() throws Exception {
            Fixture fixture = newFixture();
            endSession(fixture);

            machine(fixture.ada().token(), get(PENDING).param("remoteUrl", fixture.remote()).param("branch", BRANCH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value(fixture.feedbackId()))
                    .andExpect(jsonPath("$.data[0].sessionId").value(fixture.session()));
        }

        @Test
        void getPending_otherBranchOrUser_returnsEmpty() throws Exception {
            Fixture fixture = newFixture();
            endSession(fixture);
            Machine boLaptop = registerMachine(fixture.tenant(), fixture.bo(), "bo-laptop");

            machine(fixture.ada().token(), get(PENDING).param("remoteUrl", fixture.remote()).param("branch", "main"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
            machine(boLaptop.token(), get(PENDING).param("remoteUrl", fixture.remote()).param("branch", BRANCH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        void getPending_unknownRemote_returnsEmpty() throws Exception {
            Fixture fixture = newFixture();
            endSession(fixture);

            machine(fixture.ada().token(), get(PENDING).param("remoteUrl", remote()).param("branch", BRANCH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        void getPending_tokenOfAnotherOrganization_returnsEmpty() throws Exception {
            Fixture fixture = newFixture();
            endSession(fixture);
            Tenant other = newTenant();
            Machine stranger = registerMachine(other, signIn(other, "eve"), "eve-laptop");

            machine(stranger.token(), get(PENDING).param("remoteUrl", fixture.remote()).param("branch", BRANCH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        void getPending_neitherOrBothForms_returns400() throws Exception {
            Fixture fixture = newFixture();

            machine(fixture.ada().token(), get(PENDING))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("KAY_PLN_004"));
            machine(fixture.ada().token(), get(PENDING).param("sessionId", fixture.session())
                            .param("remoteUrl", fixture.remote()).param("branch", BRANCH))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("KAY_PLN_004"));
            machine(fixture.ada().token(), get(PENDING).param("remoteUrl", fixture.remote()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("KAY_PLN_004"));
        }
    }

    @Nested
    class Acknowledge {

        @Test
        void acknowledge_twice_marksOnceAndLowersTheCountOnce() throws Exception {
            Fixture fixture = newFixture();
            String receiver = sessionId();
            assertEquals(1, openFeedbackCount(fixture.planId()));

            ack(fixture.ada().token(), receiver, fixture.feedbackId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.sessionId").value(receiver))
                    .andExpect(jsonPath("$.data.acknowledged").value(1));
            ack(fixture.ada().token(), receiver, fixture.feedbackId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.acknowledged").value(0));

            assertEquals(0, openFeedbackCount(fixture.planId()));
            machine(fixture.ada().token(), get(PENDING).param("sessionId", fixture.session()))
                    .andExpect(jsonPath("$.data.length()").value(0));
            mockMvc.perform(in(fixture.tenant(), get("/kaya/plans/" + fixture.planId()), fixture.bo().jwt(), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.feedback[0].deliveredSessionId").value(receiver))
                    .andExpect(jsonPath("$.data.feedback[0].deliveredAt").isNotEmpty())
                    .andExpect(jsonPath("$.data.openFeedbackCount").value(0));
        }

        @Test
        void acknowledge_tokenOfAnotherUserOrOrganization_marksNothing() throws Exception {
            Fixture fixture = newFixture();
            Machine boLaptop = registerMachine(fixture.tenant(), fixture.bo(), "bo-laptop");
            Tenant other = newTenant();
            Machine stranger = registerMachine(other, signIn(other, "eve"), "eve-laptop");

            ack(boLaptop.token(), sessionId(), fixture.feedbackId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.acknowledged").value(0));
            ack(stranger.token(), sessionId(), fixture.feedbackId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.acknowledged").value(0));

            assertEquals(1, openFeedbackCount(fixture.planId()));
        }

        @Test
        void acknowledge_unknownId_marksNothing() throws Exception {
            Fixture fixture = newFixture();

            ack(fixture.ada().token(), sessionId(), UUID.randomUUID().toString())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.acknowledged").value(0));

            assertEquals(1, openFeedbackCount(fixture.planId()));
        }

        @Test
        void acknowledge_noFeedbackIds_returns422() throws Exception {
            Fixture fixture = newFixture();

            ack(fixture.ada().token(), sessionId())
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));
        }

        @Test
        void acknowledge_withoutToken_returns401() throws Exception {
            mockMvc.perform(post(ACKS).contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("sessionId", sessionId(), "feedbackIds", List.of(UUID.randomUUID().toString())))))
                    .andExpect(status().isUnauthorized());
        }
    }
}
