package com.korur.oba.apps.kaya.lesson.integration;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

// 3. Java SDK
import java.util.Map;
import java.util.UUID;

// 4. Internal

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LessonDeliveryIT extends AbstractLessonIT {

    private static final String DELIVERY = "/kaya/delivery/lessons";
    private static final String ERROR_CODE = "$.error.code";
    private static final String REMOTE_URL = "remoteUrl";

    private String etagOf(Fixture fixture, String token) throws Exception {
        MvcResult result = machine(token, get(DELIVERY).param(REMOTE_URL, fixture.remote()))
                .andExpect(status().isOk())
                .andReturn();
        String etag = result.getResponse().getHeader(HttpHeaders.ETAG);
        assertTrue(etag != null && etag.startsWith("\"") && etag.endsWith("\""), "ETag is quoted: " + etag);
        return etag;
    }

    private long rows(String lessonId) {
        return count("SELECT count(*) FROM kaya.lessons WHERE id = ?::uuid", lessonId);
    }

    @Nested
    class AddFromCheckout {

        @Test
        void addLesson_withoutRepoWide_belongsToTheCheckoutsRepository() throws Exception {
            Fixture fixture = newFixture();

            MvcResult result = addFromCheckout(fixture.adaMachine().token(), checkoutLesson(fixture.remote(), "Scoped", false))
                    .andExpect(status().isCreated())
                    .andReturn();
            String id = read(result, "$.data.id");
            assertTrue(read(result, "$.data.url").endsWith("/lessons/" + id));

            assertEquals(1L, count("SELECT count(*) FROM kaya.lessons WHERE id = ?::uuid AND repository_id = ?::uuid AND user_id = ?",
                    id, fixture.repositoryId(), fixture.ada().user().getId()));
        }

        @Test
        void addLesson_repoWide_belongsToTheOrganizationWithoutResolvingTheRemote() throws Exception {
            Fixture fixture = newFixture();

            MvcResult result = addFromCheckout(fixture.adaMachine().token(),
                    checkoutLesson("git@github.com:senseylabs/never-seen.git", "Wide", true))
                    .andExpect(status().isCreated())
                    .andReturn();

            assertEquals(1L, count("SELECT count(*) FROM kaya.lessons WHERE id = ?::uuid AND repository_id IS NULL",
                    read(result, "$.data.id")));
        }

        @Test
        void addLesson_repoWideOmitted_isScopedToTheRepository() throws Exception {
            Fixture fixture = newFixture();
            Map<String, Object> body = checkoutLesson(fixture.remote(), "Scoped", false);
            body.remove("repoWide");

            MvcResult result = addFromCheckout(fixture.adaMachine().token(), body)
                    .andExpect(status().isCreated())
                    .andReturn();

            assertEquals(1L, count("SELECT count(*) FROM kaya.lessons WHERE id = ?::uuid AND repository_id = ?::uuid",
                    read(result, "$.data.id"), fixture.repositoryId()));
        }

        @Test
        void addLesson_withClaudeSessionId_storesIt() throws Exception {
            Fixture fixture = newFixture();
            String session = sessionId();
            Map<String, Object> body = checkoutLesson(fixture.remote(), "Linked", false);
            body.put("claudeSessionId", session);

            MvcResult result = addFromCheckout(fixture.adaMachine().token(), body)
                    .andExpect(status().isCreated())
                    .andReturn();

            assertEquals(1L, count("SELECT count(*) FROM kaya.lessons WHERE id = ?::uuid AND claude_session_id = ?",
                    read(result, "$.data.id"), session));
        }

        @Test
        void addLesson_unknownRemote_returns404() throws Exception {
            Fixture fixture = newFixture();

            addFromCheckout(fixture.adaMachine().token(),
                    checkoutLesson("git@github.com:senseylabs/never-seen.git", "Scoped", false))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath(ERROR_CODE).value("KAY_NF_001"));
        }

        @Test
        void addLesson_archivedRepository_returns422ButRepoWideStillWorks() throws Exception {
            Fixture fixture = newFixture();
            jdbcTemplate.update("UPDATE kaya.repositories SET status = 'ARCHIVED'::repository_repository_status_enum WHERE id = ?::uuid",
                    fixture.repositoryId());

            addFromCheckout(fixture.adaMachine().token(), checkoutLesson(fixture.remote(), "Scoped", false))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath(ERROR_CODE).value("KAY_VAL_002"));
            addFromCheckout(fixture.adaMachine().token(), checkoutLesson(fixture.remote(), "Wide", true))
                    .andExpect(status().isCreated());
        }

        @Test
        void addLesson_invalidFields_return422() throws Exception {
            Fixture fixture = newFixture();
            Map<String, Object> longTitle = checkoutLesson(fixture.remote(), "t".repeat(301), false);
            Map<String, Object> badSession = checkoutLesson(fixture.remote(), "Title", false);
            badSession.put("claudeSessionId", "not a session id");
            Map<String, Object> noRemote = checkoutLesson(fixture.remote(), "Title", false);
            noRemote.remove(REMOTE_URL);

            addFromCheckout(fixture.adaMachine().token(), longTitle)
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath(ERROR_CODE).value("GEN_VAL_001"));
            addFromCheckout(fixture.adaMachine().token(), badSession)
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath(ERROR_CODE).value("GEN_VAL_001"));
            addFromCheckout(fixture.adaMachine().token(), noRemote)
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath(ERROR_CODE).value("GEN_VAL_001"));
        }

        @Test
        void addLesson_malformedBody_returns400() throws Exception {
            Fixture fixture = newFixture();

            machine(fixture.adaMachine().token(), post(DELIVERY).contentType(MediaType.APPLICATION_JSON).content("{"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class ListForCheckout {

        @Test
        void getLessons_returnsRepositoryAndOrganizationWideOnlyWithoutBodies() throws Exception {
            Fixture fixture = newFixture();
            String otherRepository = secondRepository(fixture, remote());
            insertLesson(fixture, fixture.ada(), "scoped", fixture.repositoryId(), "2026-10-02T00:00:00Z", "2026-10-02T00:00:00Z");
            insertLesson(fixture, fixture.ada(), "wide", null, "2026-10-03T00:00:00Z", "2026-10-03T00:00:00Z");
            insertLesson(fixture, fixture.ada(), "elsewhere", otherRepository, "2026-10-04T00:00:00Z", "2026-10-04T00:00:00Z");

            machine(fixture.boMachine().token(), get(DELIVERY).param(REMOTE_URL, fixture.remote()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].title").value("wide"))
                    .andExpect(jsonPath("$.data[0].repository").doesNotExist())
                    .andExpect(jsonPath("$.data[1].title").value("scoped"))
                    .andExpect(jsonPath("$.data[1].repository").value("scratch"))
                    .andExpect(content().string(not(containsString("\"body\""))));
        }

        @Test
        void getLessons_noLessons_returnsEmptyListWithAnEtag() throws Exception {
            Fixture fixture = newFixture();

            machine(fixture.adaMachine().token(), get(DELIVERY).param(REMOTE_URL, fixture.remote()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().exists(HttpHeaders.ETAG));
        }

        @Test
        void getLessons_matchingIfNoneMatch_returns304WithoutBody() throws Exception {
            Fixture fixture = newFixture();
            createLesson(fixture, fixture.ada().jwt(), "One", fixture.repositoryId());
            String etag = etagOf(fixture, fixture.adaMachine().token());

            machine(fixture.adaMachine().token(), get(DELIVERY).param(REMOTE_URL, fixture.remote())
                    .header(HttpHeaders.IF_NONE_MATCH, etag))
                    .andExpect(status().isNotModified())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string(HttpHeaders.ETAG, etag))
                    .andExpect(content().string(""));
            machine(fixture.adaMachine().token(), get(DELIVERY).param(REMOTE_URL, fixture.remote())
                    .header(HttpHeaders.IF_NONE_MATCH, "\"stale\""))
                    .andExpect(status().isOk());
        }

        @Test
        void getLessons_addEditAndDelete_eachChangeTheEtag() throws Exception {
            Fixture fixture = newFixture();
            String token = fixture.adaMachine().token();
            String initial = etagOf(fixture, token);

            String id = createLesson(fixture, fixture.ada().jwt(), "One", fixture.repositoryId());
            String afterAdd = etagOf(fixture, token);
            mockMvc.perform(in(fixture.tenant(), put("/kaya/lessons/" + id), fixture.ada().jwt(),
                    lessonBody("One edited", "Body.", fixture.repositoryId()))).andExpect(status().isOk());
            String afterEdit = etagOf(fixture, token);
            mockMvc.perform(in(fixture.tenant(), delete("/kaya/lessons/" + id), fixture.ada().jwt(), null))
                    .andExpect(status().isOk());
            String afterDelete = etagOf(fixture, token);

            assertNotEquals(initial, afterAdd);
            assertNotEquals(afterAdd, afterEdit);
            assertNotEquals(afterEdit, afterDelete);
            assertEquals(initial, afterDelete);
        }

        @Test
        void getLessons_lessonOfAnotherRepository_doesNotMoveTheEtag() throws Exception {
            Fixture fixture = newFixture();
            String elsewhere = secondRepository(fixture, remote());
            String before = etagOf(fixture, fixture.adaMachine().token());

            createLesson(fixture, fixture.ada().jwt(), "Elsewhere", elsewhere);

            assertEquals(before, etagOf(fixture, fixture.adaMachine().token()));
        }

        @Test
        void getLessons_past100_capsTheListButStillTracksEveryLesson() throws Exception {
            Fixture fixture = newFixture();
            jdbcTemplate.update("""
                    INSERT INTO kaya.lessons (title, body, organization_id, user_id, created_at, updated_at)
                    SELECT 'bulk ' || n, 'body', ?, ?, TIMESTAMPTZ '2026-09-01' + n * INTERVAL '1 minute', TIMESTAMPTZ '2026-09-01'
                    FROM generate_series(1, 105) AS n
                    """, fixture.tenant().organization().getId(), fixture.ada().user().getId());
            String token = fixture.adaMachine().token();
            String before = etagOf(fixture, token);
            machine(token, get(DELIVERY).param(REMOTE_URL, fixture.remote()))
                    .andExpect(jsonPath("$.data.length()").value(100));

            jdbcTemplate.update("UPDATE kaya.lessons SET updated_at = TIMESTAMPTZ '2026-09-02' WHERE title = 'bulk 1'");

            assertNotEquals(before, etagOf(fixture, token));
        }

        @Test
        void getLessons_since_marksOnlyLaterLessonsAsChanged() throws Exception {
            Fixture fixture = newFixture();
            insertLesson(fixture, fixture.ada(), "old", null, "2026-10-01T00:00:00Z", "2026-10-01T00:00:00Z");
            insertLesson(fixture, fixture.ada(), "new", null, "2026-10-02T00:00:00Z", "2026-10-20T00:00:00Z");

            machine(fixture.adaMachine().token(), get(DELIVERY).param(REMOTE_URL, fixture.remote())
                    .param("since", "2026-10-10T00:00:00Z"))
                    .andExpect(jsonPath("$.data[0].title").value("new"))
                    .andExpect(jsonPath("$.data[0].changed").value(true))
                    .andExpect(jsonPath("$.data[1].title").value("old"))
                    .andExpect(jsonPath("$.data[1].changed").value(false));
            machine(fixture.adaMachine().token(), get(DELIVERY).param(REMOTE_URL, fixture.remote()))
                    .andExpect(jsonPath("$.data[0].changed").value(false))
                    .andExpect(jsonPath("$.data[1].changed").value(false));
        }

        @Test
        void getLessons_invalidSinceOrMissingRemote_return422() throws Exception {
            Fixture fixture = newFixture();

            machine(fixture.adaMachine().token(), get(DELIVERY).param(REMOTE_URL, fixture.remote()).param("since", "yesterday"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath(ERROR_CODE).value("GEN_VAL_001"))
                    .andExpect(jsonPath("$.error.errorValues.since").exists());
            machine(fixture.adaMachine().token(), get(DELIVERY))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath(ERROR_CODE).value("GEN_VAL_001"));
        }

        @Test
        void getLessons_unknownOrUnreadableRemote_returnsOrganizationWideOnly() throws Exception {
            Fixture fixture = newFixture();
            insertLesson(fixture, fixture.ada(), "scoped", fixture.repositoryId(), "2026-10-02T00:00:00Z", "2026-10-02T00:00:00Z");
            insertLesson(fixture, fixture.ada(), "wide", null, "2026-10-03T00:00:00Z", "2026-10-03T00:00:00Z");

            machine(fixture.adaMachine().token(), get(DELIVERY).param(REMOTE_URL, "git@github.com:senseylabs/never-seen.git"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].title").value("wide"));
            machine(fixture.adaMachine().token(), get(DELIVERY).param(REMOTE_URL, "not a remote"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].title").value("wide"));
        }

        @Test
        void getLessons_archivedRepository_returns422() throws Exception {
            Fixture fixture = newFixture();
            jdbcTemplate.update("UPDATE kaya.repositories SET status = 'ARCHIVED'::repository_repository_status_enum WHERE id = ?::uuid",
                    fixture.repositoryId());

            machine(fixture.adaMachine().token(), get(DELIVERY).param(REMOTE_URL, fixture.remote()))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath(ERROR_CODE).value("KAY_VAL_002"));
            machine(fixture.adaMachine().token(), get(DELIVERY + "/search").param(REMOTE_URL, fixture.remote()).param("search", "x"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath(ERROR_CODE).value("KAY_VAL_002"));
        }

        @Test
        void getLessons_lessonsOfAnotherOrganization_areNotDelivered() throws Exception {
            Fixture fixture = newFixture();
            Fixture other = newFixture();
            createLesson(other, other.ada().jwt(), "Theirs", null);

            machine(fixture.adaMachine().token(), get(DELIVERY).param(REMOTE_URL, fixture.remote()))
                    .andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    @Nested
    class Search {

        @Test
        void search_matchesTitleOrBodyCaseIgnoredAndReturnsBodies() throws Exception {
            Fixture fixture = newFixture();
            insertLesson(fixture, fixture.ada(), "Flyway first", fixture.repositoryId(), "2026-10-02T00:00:00Z", "2026-10-02T00:00:00Z");
            insertLesson(fixture, fixture.ada(), "Other", null, "2026-10-03T00:00:00Z", "2026-10-03T00:00:00Z");

            machine(fixture.adaMachine().token(), get(DELIVERY + "/search").param(REMOTE_URL, fixture.remote()).param("search", "FLYWAY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].title").value("Flyway first"))
                    .andExpect(jsonPath("$.data[0].body").value("Body of Flyway first"))
                    .andExpect(jsonPath("$.data[0].repository").value("scratch"))
                    .andExpect(jsonPath("$.data[0].changed").doesNotExist());
            machine(fixture.adaMachine().token(), get(DELIVERY + "/search").param(REMOTE_URL, fixture.remote()).param("search", "body of other"))
                    .andExpect(jsonPath("$.data.length()").value(1));
            machine(fixture.adaMachine().token(), get(DELIVERY + "/search").param(REMOTE_URL, fixture.remote()).param("search", "%"))
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        void search_blankOrMissingSearch_returns422() throws Exception {
            Fixture fixture = newFixture();

            machine(fixture.adaMachine().token(), get(DELIVERY + "/search").param(REMOTE_URL, fixture.remote()))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath(ERROR_CODE).value("GEN_VAL_001"));
            machine(fixture.adaMachine().token(), get(DELIVERY + "/search").param(REMOTE_URL, fixture.remote()).param("search", " "))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    @Nested
    class DetailAndDelete {

        @Test
        void getLesson_exists_returnsDetailWithCanEditOfTheTokensUser() throws Exception {
            Fixture fixture = newFixture();
            String id = createLesson(fixture, fixture.ada().jwt(), "Detail", null);

            machine(fixture.adaMachine().token(), get(DELIVERY + "/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.title").value("Detail"))
                    .andExpect(jsonPath("$.data.canEdit").value(true));
            machine(fixture.boMachine().token(), get(DELIVERY + "/" + id))
                    .andExpect(jsonPath("$.data.canEdit").value(false));
        }

        @Test
        void getLesson_ofAnotherOrganizationOrUnknown_returns404() throws Exception {
            Fixture fixture = newFixture();
            Fixture other = newFixture();
            String foreign = createLesson(other, other.ada().jwt(), "Theirs", null);

            machine(fixture.adaMachine().token(), get(DELIVERY + "/" + foreign))
                    .andExpect(status().isNotFound()).andExpect(jsonPath(ERROR_CODE).value("KAY_NF_001"));
            machine(fixture.adaMachine().token(), get(DELIVERY + "/" + UUID.randomUUID()))
                    .andExpect(status().isNotFound());
            machine(fixture.adaMachine().token(), get(DELIVERY + "/not-a-uuid"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void deleteLesson_byAuthor_removesTheRow() throws Exception {
            Fixture fixture = newFixture();
            String id = createLesson(fixture, fixture.ada().jwt(), "Doomed", null);

            machine(fixture.adaMachine().token(), delete(DELIVERY + "/" + id)).andExpect(status().isOk());

            assertEquals(0L, rows(id));
        }

        @Test
        void deleteLesson_byAdminThroughTheMachineChain_removesAnotherMembersLesson() throws Exception {
            Fixture fixture = newFixture();
            Machine adminMachine = registerMachineForUser(fixture.tenant(),
                    fixture.tenant().admin().getUser().getId().toString(), "admin-laptop");
            String id = createLesson(fixture, fixture.ada().jwt(), "Doomed", null);

            machine(adminMachine.token(), delete(DELIVERY + "/" + id)).andExpect(status().isOk());

            assertEquals(0L, rows(id));
        }

        @Test
        void deleteLesson_byAnotherMember_returns403AndKeepsTheRow() throws Exception {
            Fixture fixture = newFixture();
            String id = createLesson(fixture, fixture.ada().jwt(), "Kept", null);

            machine(fixture.boMachine().token(), delete(DELIVERY + "/" + id))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath(ERROR_CODE).value("GEN_SEC_003"));
            assertEquals(1L, rows(id));
        }

        @Test
        void deleteLesson_ofAnotherOrganization_returns404() throws Exception {
            Fixture fixture = newFixture();
            Fixture other = newFixture();
            String foreign = createLesson(other, other.ada().jwt(), "Theirs", null);

            machine(fixture.adaMachine().token(), delete(DELIVERY + "/" + foreign))
                    .andExpect(status().isNotFound());
            assertEquals(1L, rows(foreign));
        }
    }

    @Nested
    class TokenAccess {

        @Test
        void everyCall_withoutAMachineToken_returns401() throws Exception {
            Fixture fixture = newFixture();

            mockMvc.perform(get(DELIVERY).param(REMOTE_URL, fixture.remote()))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath(ERROR_CODE).value("KAY_SEC_003"));
            mockMvc.perform(post(DELIVERY).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(delete(DELIVERY + "/" + UUID.randomUUID()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void everyCall_userNoLongerMemberOfTheOrganization_isRefusedAndStoresNothing() throws Exception {
            Fixture fixture = newFixture();
            String id = createLesson(fixture, fixture.ada().jwt(), "Kept", null);
            jdbcTemplate.update("DELETE FROM shrine.organization_members WHERE user_id = ?", fixture.bo().user().getId());

            machine(fixture.boMachine().token(), get(DELIVERY).param(REMOTE_URL, fixture.remote()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath(ERROR_CODE).value("GEN_SEC_002"));
            addFromCheckout(fixture.boMachine().token(), checkoutLesson(fixture.remote(), "Refused", false))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath(ERROR_CODE).value("GEN_SEC_002"));
            machine(fixture.boMachine().token(), get(DELIVERY + "/search").param(REMOTE_URL, fixture.remote()).param("search", "x"))
                    .andExpect(status().isForbidden());
            machine(fixture.boMachine().token(), get(DELIVERY + "/" + id)).andExpect(status().isForbidden());
            machine(fixture.boMachine().token(), delete(DELIVERY + "/" + id)).andExpect(status().isForbidden());
            assertEquals(0L, count("SELECT count(*) FROM kaya.lessons WHERE title = 'Refused'"));
            assertEquals(1L, rows(id));
        }

        @Test
        void everyCall_userDisabled_answers403WithTheStatus() throws Exception {
            Fixture fixture = newFixture();
            jdbcTemplate.update("UPDATE shrine.users SET status = 'DISABLED'::shrine_identity_user_status_enum WHERE id = ?",
                    fixture.bo().user().getId());

            machine(fixture.boMachine().token(), get(DELIVERY).param(REMOTE_URL, fixture.remote()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath(ERROR_CODE).value("GEN_SEC_002"))
                    .andExpect(jsonPath("$.error.errorValues.userStatus").value("DISABLED"));
        }

        @Test
        void everyCall_membershipDisabled_answers403KagiSec002() throws Exception {
            Fixture fixture = newFixture();
            jdbcTemplate.update("UPDATE shrine.organization_members SET status = 'DISABLED'::shrine_organization_member_status_enum "
                    + "WHERE user_id = ?", fixture.bo().user().getId());

            machine(fixture.boMachine().token(), get(DELIVERY).param(REMOTE_URL, fixture.remote()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath(ERROR_CODE).value("KAY_SEC_002"));
        }
    }
}
