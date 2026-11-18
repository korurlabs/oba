package com.korur.oba.apps.kaya.lesson.integration;

// 1. Framework-level
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// 3. Java SDK
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractSessionIT;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base of the lesson tests: one organization with an author (Ada, a MEMBER with a machine), a second
 * MEMBER (Bo, with a machine) and the tenant's ADMIN, plus one repository recorded by Ada's session.
 */
abstract class AbstractLessonIT extends AbstractSessionIT {

    /** The organization of one test and the people and the repository in it. */
    protected record Fixture(Tenant tenant, Person ada, Machine adaMachine, Person bo, Machine boMachine,
            String remote, String repositoryId) {

        RequestPostProcessor adminJwt() {
            return jwtOf(tenant.admin().getUser());
        }
    }

    protected Fixture newFixture() throws Exception {
        Tenant tenant = newTenant();
        Person ada = signIn(tenant, "ada");
        Machine adaMachine = registerMachine(tenant, ada, "ada-laptop");
        Person bo = signIn(tenant, "bo");
        Machine boMachine = registerMachine(tenant, bo, "bo-laptop");
        String remote = remote();
        String session = ingestSession(adaMachine, remote, 1);
        String repositoryId = jdbcTemplate.queryForObject(
                "SELECT repository_id::text FROM kaya.sessions WHERE organization_id = ? AND session_id = ?",
                String.class, tenant.organization().getId(), session);
        return new Fixture(tenant, ada, adaMachine, bo, boMachine, remote, repositoryId);
    }

    /** A second repository of the organization, recorded by Ada's machine; returns its id. */
    protected String secondRepository(Fixture fixture, String remote) throws Exception {
        String session = ingestSession(fixture.adaMachine(), remote, 1);
        return jdbcTemplate.queryForObject(
                "SELECT repository_id::text FROM kaya.sessions WHERE organization_id = ? AND session_id = ?",
                String.class, fixture.tenant().organization().getId(), session);
    }

    protected static String lessonBody(String title, String body, String repositoryId) {
        StringBuilder json = new StringBuilder("{\"title\":\"").append(title).append("\",\"body\":\"").append(body)
                .append('"');
        if (repositoryId != null) {
            json.append(",\"repositoryId\":\"").append(repositoryId).append('"');
        }
        return json.append('}').toString();
    }

    /** POST /lessons as the given person; returns the id of the new lesson. */
    protected String createLesson(Fixture fixture, RequestPostProcessor jwt, String title, String repositoryId)
            throws Exception {
        MvcResult result = mockMvc.perform(in(fixture.tenant(), post("/kaya/lessons"), jwt,
                        lessonBody(title, "Body of " + title, repositoryId)))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result, "$.data.id");
    }

    /** Inserts a lesson row directly, with chosen timestamps, and returns its id. */
    protected String insertLesson(Fixture fixture, Person author, String title, String repositoryId,
            String createdAt, String updatedAt) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO kaya.lessons (id, title, body, organization_id, repository_id, user_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?::uuid, ?, ?::timestamptz, ?::timestamptz)
                """, id, title, "Body of " + title, fixture.tenant().organization().getId(), repositoryId,
                author.user().getId(), createdAt, updatedAt);
        return id.toString();
    }

    protected ResultActions machine(String token, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    protected ResultActions addFromCheckout(String token, Map<String, Object> body) throws Exception {
        return machine(token, post("/kaya/delivery/lessons").contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    protected static Map<String, Object> checkoutLesson(String remote, String title, boolean repoWide) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("remoteUrl", remote);
        body.put("title", title);
        body.put("body", "Body of " + title);
        body.put("repoWide", repoWide);
        return body;
    }
}
