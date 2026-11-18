package com.korur.oba.apps.kaya.lesson.integration;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LessonControllerIT extends AbstractLessonIT {

    private static final String LESSONS = "/kaya/lessons";
    private static final String ERROR_CODE = "$.error.code";

    private long lessonRows(String lessonId) {
        return count("SELECT count(*) FROM kaya.lessons WHERE id = ?::uuid", lessonId);
    }

    @Nested
    class Create {

        @Test
        void createLesson_organizationWide_returnsDetailOfTheAuthor() throws Exception {
            Fixture fixture = newFixture();

            mockMvc.perform(in(fixture.tenant(), post(LESSONS), fixture.ada().jwt(),
                            lessonBody("Run flyway first", "Migrate before the tests.", null)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.title").value("Run flyway first"))
                    .andExpect(jsonPath("$.data.body").value("Migrate before the tests."))
                    .andExpect(jsonPath("$.data.repository").doesNotExist())
                    .andExpect(jsonPath("$.data.user.id").value(fixture.ada().userId()))
                    .andExpect(jsonPath("$.data.session").doesNotExist())
                    .andExpect(jsonPath("$.data.canEdit").value(true));
        }

        @Test
        void createLesson_withRepository_namesTheRepository() throws Exception {
            Fixture fixture = newFixture();

            mockMvc.perform(in(fixture.tenant(), post(LESSONS), fixture.ada().jwt(),
                            lessonBody("Scoped", "Body.", fixture.repositoryId())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.repository.id").value(fixture.repositoryId()))
                    .andExpect(jsonPath("$.data.repository.name").value("scratch"));
        }

        @Test
        void createLesson_repositoryOfAnotherOrganization_returns404() throws Exception {
            Fixture fixture = newFixture();
            Fixture other = newFixture();

            mockMvc.perform(in(fixture.tenant(), post(LESSONS), fixture.ada().jwt(),
                            lessonBody("Scoped", "Body.", other.repositoryId())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath(ERROR_CODE).value("KAY_NF_001"));
        }

        @Test
        void createLesson_unknownRepository_returns404() throws Exception {
            Fixture fixture = newFixture();

            mockMvc.perform(in(fixture.tenant(), post(LESSONS), fixture.ada().jwt(),
                            lessonBody("Scoped", "Body.", UUID.randomUUID().toString())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath(ERROR_CODE).value("KAY_NF_001"));
        }

        @Test
        void createLesson_archivedRepository_returns422() throws Exception {
            Fixture fixture = newFixture();
            jdbcTemplate.update("UPDATE kaya.repositories SET status = 'ARCHIVED'::repository_repository_status_enum WHERE id = ?::uuid",
                    fixture.repositoryId());

            mockMvc.perform(in(fixture.tenant(), post(LESSONS), fixture.ada().jwt(),
                            lessonBody("Scoped", "Body.", fixture.repositoryId())))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath(ERROR_CODE).value("KAY_VAL_002"));
        }

        @Test
        void createLesson_limitsExceeded_returns422() throws Exception {
            Fixture fixture = newFixture();

            mockMvc.perform(in(fixture.tenant(), post(LESSONS), fixture.ada().jwt(),
                            lessonBody("t".repeat(301), "Body.", null)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath(ERROR_CODE).value("GEN_VAL_001"));
            mockMvc.perform(in(fixture.tenant(), post(LESSONS), fixture.ada().jwt(),
                            lessonBody("Title", "b".repeat(4001), null)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath(ERROR_CODE).value("GEN_VAL_001"));
            mockMvc.perform(in(fixture.tenant(), post(LESSONS), fixture.ada().jwt(), lessonBody("  ", "Body.", null)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath(ERROR_CODE).value("GEN_VAL_001"));
            mockMvc.perform(in(fixture.tenant(), post(LESSONS), fixture.ada().jwt(), "{\"title\":\"Title\"}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath(ERROR_CODE).value("GEN_VAL_001"));
        }

        @Test
        void createLesson_limitsExactlyMet_returns201() throws Exception {
            Fixture fixture = newFixture();

            mockMvc.perform(in(fixture.tenant(), post(LESSONS), fixture.ada().jwt(),
                            lessonBody("t".repeat(300), "b".repeat(4000), null)))
                    .andExpect(status().isCreated());
        }

        @Test
        void createLesson_userOfAnotherOrganization_returns403() throws Exception {
            Fixture fixture = newFixture();
            Fixture other = newFixture();

            mockMvc.perform(in(fixture.tenant(), post(LESSONS), other.ada().jwt(), lessonBody("Title", "Body.", null)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void createLesson_withoutJwt_returns401() throws Exception {
            Fixture fixture = newFixture();

            mockMvc.perform(post(LESSONS).header("X-Organization-ID", fixture.tenant().id())
                            .contentType("application/json").content(lessonBody("Title", "Body.", null)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class ListAndRead {

        @Test
        void getLessons_several_listsNewestFirstWithPaging() throws Exception {
            Fixture fixture = newFixture();
            insertLesson(fixture, fixture.ada(), "oldest", null, "2026-10-01T00:00:00Z", "2026-10-01T00:00:00Z");
            insertLesson(fixture, fixture.bo(), "middle", fixture.repositoryId(), "2026-10-02T00:00:00Z", "2026-10-02T00:00:00Z");
            insertLesson(fixture, fixture.ada(), "newest", null, "2026-10-03T00:00:00Z", "2026-10-03T00:00:00Z");

            mockMvc.perform(in(fixture.tenant(), get(LESSONS).param("amount", "2"), fixture.ada().jwt(), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].title").value("newest"))
                    .andExpect(jsonPath("$.data[1].title").value("middle"))
                    .andExpect(jsonPath("$.data[1].repository.name").value("scratch"))
                    .andExpect(jsonPath("$.pagination.totalElements").value(3))
                    .andExpect(jsonPath("$.pagination.totalPages").value(2));
            mockMvc.perform(in(fixture.tenant(), get(LESSONS).param("amount", "2").param("page", "2"),
                            fixture.ada().jwt(), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].title").value("oldest"));
        }

        @Test
        void getLessons_filters_narrowTheList() throws Exception {
            Fixture fixture = newFixture();
            insertLesson(fixture, fixture.ada(), "Alpha rule", null, "2026-10-01T00:00:00Z", "2026-10-01T00:00:00Z");
            insertLesson(fixture, fixture.bo(), "Beta rule", fixture.repositoryId(), "2026-10-02T00:00:00Z", "2026-10-02T00:00:00Z");

            mockMvc.perform(in(fixture.tenant(), get(LESSONS).param("repositoryId", fixture.repositoryId()),
                            fixture.ada().jwt(), null))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].title").value("Beta rule"));
            mockMvc.perform(in(fixture.tenant(), get(LESSONS).param("organizationWide", "true"),
                            fixture.ada().jwt(), null))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].title").value("Alpha rule"));
            mockMvc.perform(in(fixture.tenant(), get(LESSONS).param("organizationWide", "false"),
                            fixture.ada().jwt(), null))
                    .andExpect(jsonPath("$.data.length()").value(2));
            mockMvc.perform(in(fixture.tenant(), get(LESSONS).param("userId", fixture.bo().userId()),
                            fixture.ada().jwt(), null))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].title").value("Beta rule"));
            mockMvc.perform(in(fixture.tenant(), get(LESSONS).param("search", "ALPHA"), fixture.ada().jwt(), null))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].title").value("Alpha rule"));
            mockMvc.perform(in(fixture.tenant(), get(LESSONS).param("search", "body of beta"), fixture.ada().jwt(), null))
                    .andExpect(jsonPath("$.data.length()").value(1));
            mockMvc.perform(in(fixture.tenant(), get(LESSONS).param("search", "%"), fixture.ada().jwt(), null))
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        void getLessons_sessionFilter_returnsOnlyThatSessionsLessons() throws Exception {
            Fixture fixture = newFixture();
            String session = sessionId();
            addFromCheckout(fixture.adaMachine().token(), checkoutLessonWithSession(fixture, "From session", session))
                    .andExpect(status().isCreated());
            createLesson(fixture, fixture.ada().jwt(), "No session", null);

            mockMvc.perform(in(fixture.tenant(), get(LESSONS).param("sessionId", session), fixture.ada().jwt(), null))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].title").value("From session"))
                    .andExpect(jsonPath("$.data[0].claudeSessionId").value(session));
            mockMvc.perform(in(fixture.tenant(), get(LESSONS).param("sessionId", sessionId()), fixture.ada().jwt(), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
            mockMvc.perform(in(fixture.tenant(), get(LESSONS).param("sessionId", "not a session"), fixture.ada().jwt(), null))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath(ERROR_CODE).value("GEN_VAL_001"));
        }

        @Test
        void getLessons_rowsOfKnownAndUnknownSessions_carryTheSessionOnlyWhenKayaKnowsIt() throws Exception {
            Fixture fixture = newFixture();
            String known = ingestSession(fixture.adaMachine(), fixture.remote(), 1);
            String unknown = sessionId();
            Fixture other = newFixture();
            String ofAnotherOrganization = ingestSession(other.adaMachine(), other.remote(), 1);
            addFromCheckout(fixture.adaMachine().token(), checkoutLessonWithSession(fixture, "twin one", known))
                    .andExpect(status().isCreated());
            addFromCheckout(fixture.boMachine().token(), checkoutLessonWithSession(fixture, "twin two", known))
                    .andExpect(status().isCreated());
            addFromCheckout(fixture.adaMachine().token(), checkoutLessonWithSession(fixture, "unknown", unknown))
                    .andExpect(status().isCreated());
            addFromCheckout(fixture.adaMachine().token(), checkoutLessonWithSession(fixture, "foreign", ofAnotherOrganization))
                    .andExpect(status().isCreated());
            createLesson(fixture, fixture.ada().jwt(), "none", null);
            String knownRowId = jdbcTemplate.queryForObject(
                    "SELECT id::text FROM kaya.sessions WHERE organization_id = ? AND session_id = ?", String.class,
                    fixture.tenant().organization().getId(), known);

            mockMvc.perform(in(fixture.tenant(), get(LESSONS).param("search", "twin"), fixture.ada().jwt(), null))
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].session.id").value(knownRowId))
                    .andExpect(jsonPath("$.data[0].session.sessionId").value(known))
                    .andExpect(jsonPath("$.data[1].session.id").value(knownRowId));
            mockMvc.perform(in(fixture.tenant(), get(LESSONS).param("search", "unknown"), fixture.ada().jwt(), null))
                    .andExpect(jsonPath("$.data[0].claudeSessionId").value(unknown))
                    .andExpect(jsonPath("$.data[0].session").doesNotExist());
            mockMvc.perform(in(fixture.tenant(), get(LESSONS).param("search", "foreign"), fixture.ada().jwt(), null))
                    .andExpect(jsonPath("$.data[0].claudeSessionId").value(ofAnotherOrganization))
                    .andExpect(jsonPath("$.data[0].session").doesNotExist());
            mockMvc.perform(in(fixture.tenant(), get(LESSONS).param("search", "none"), fixture.ada().jwt(), null))
                    .andExpect(jsonPath("$.data[0].claudeSessionId").doesNotExist())
                    .andExpect(jsonPath("$.data[0].session").doesNotExist());
        }

        @Test
        void getLessons_canEditPerViewer_isTrueForAuthorAndAdminOnly() throws Exception {
            Fixture fixture = newFixture();
            createLesson(fixture, fixture.ada().jwt(), "Ada's", null);

            mockMvc.perform(in(fixture.tenant(), get(LESSONS), fixture.ada().jwt(), null))
                    .andExpect(jsonPath("$.data[0].canEdit").value(true));
            mockMvc.perform(in(fixture.tenant(), get(LESSONS), fixture.bo().jwt(), null))
                    .andExpect(jsonPath("$.data[0].canEdit").value(false));
            mockMvc.perform(in(fixture.tenant(), get(LESSONS), fixture.adminJwt(), null))
                    .andExpect(jsonPath("$.data[0].canEdit").value(true));
        }

        @Test
        void getLessons_amountAboveHundred_returns422() throws Exception {
            Fixture fixture = newFixture();

            mockMvc.perform(in(fixture.tenant(), get(LESSONS).param("amount", "101"), fixture.ada().jwt(), null))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath(ERROR_CODE).value("GEN_VAL_001"));
        }

        @Test
        void getLessons_lessonsOfAnotherOrganization_areNotListed() throws Exception {
            Fixture fixture = newFixture();
            Fixture other = newFixture();
            createLesson(other, other.ada().jwt(), "Theirs", null);

            mockMvc.perform(in(fixture.tenant(), get(LESSONS), fixture.ada().jwt(), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        void getFilters_lessonsExist_returnsRepositoriesAndUsers() throws Exception {
            Fixture fixture = newFixture();
            createLesson(fixture, fixture.ada().jwt(), "Filterable", fixture.repositoryId());
            createLesson(fixture, fixture.bo().jwt(), "Wide", null);

            mockMvc.perform(in(fixture.tenant(), get(LESSONS + "/filters"), fixture.ada().jwt(), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.repositories[0].id").value(fixture.repositoryId()))
                    .andExpect(jsonPath("$.data.repositories[0].name").value("scratch"))
                    .andExpect(jsonPath("$.data.repositories.length()").value(1))
                    .andExpect(jsonPath("$.data.users.length()").value(2));
        }

        @Test
        void getLesson_exists_returnsDetail() throws Exception {
            Fixture fixture = newFixture();
            String id = createLesson(fixture, fixture.ada().jwt(), "Detail", fixture.repositoryId());

            mockMvc.perform(in(fixture.tenant(), get(LESSONS + "/" + id), fixture.bo().jwt(), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(id))
                    .andExpect(jsonPath("$.data.title").value("Detail"))
                    .andExpect(jsonPath("$.data.canEdit").value(false));
        }

        @Test
        void getLesson_ofAnotherOrganization_returns404() throws Exception {
            Fixture fixture = newFixture();
            Fixture other = newFixture();
            String id = createLesson(other, other.ada().jwt(), "Theirs", null);

            mockMvc.perform(in(fixture.tenant(), get(LESSONS + "/" + id), fixture.ada().jwt(), null))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath(ERROR_CODE).value("KAY_NF_001"));
        }

        @Test
        void getLesson_malformedId_returns400() throws Exception {
            Fixture fixture = newFixture();

            mockMvc.perform(in(fixture.tenant(), get(LESSONS + "/not-a-uuid"), fixture.ada().jwt(), null))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class UpdateAndDelete {

        @Test
        void updateLesson_byAuthor_replacesTheFieldsAndMovesToTheOrganization() throws Exception {
            Fixture fixture = newFixture();
            String id = createLesson(fixture, fixture.ada().jwt(), "Before", fixture.repositoryId());

            mockMvc.perform(in(fixture.tenant(), put(LESSONS + "/" + id), fixture.ada().jwt(),
                            lessonBody("After", "New body.", null)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.title").value("After"))
                    .andExpect(jsonPath("$.data.body").value("New body."))
                    .andExpect(jsonPath("$.data.repository").doesNotExist());
            assertEquals(1L, count("SELECT count(*) FROM kaya.lessons WHERE id = ?::uuid AND repository_id IS NULL AND title = 'After'", id));
        }

        @Test
        void updateLesson_byAdmin_succeedsAndKeepsTheAuthor() throws Exception {
            Fixture fixture = newFixture();
            String id = createLesson(fixture, fixture.ada().jwt(), "Before", null);

            mockMvc.perform(in(fixture.tenant(), put(LESSONS + "/" + id), fixture.adminJwt(),
                            lessonBody("Edited by admin", "Body.", null)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.title").value("Edited by admin"))
                    .andExpect(jsonPath("$.data.user.id").value(fixture.ada().userId()));
        }

        @Test
        void updateLesson_byAnotherMember_returns403AndKeepsTheLesson() throws Exception {
            Fixture fixture = newFixture();
            String id = createLesson(fixture, fixture.ada().jwt(), "Before", null);

            mockMvc.perform(in(fixture.tenant(), put(LESSONS + "/" + id), fixture.bo().jwt(),
                            lessonBody("Hijacked", "Body.", null)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath(ERROR_CODE).value("GEN_SEC_003"));
            assertEquals(1L, count("SELECT count(*) FROM kaya.lessons WHERE id = ?::uuid AND title = 'Before'", id));
        }

        @Test
        void updateLesson_moveToArchivedRepository_returns422() throws Exception {
            Fixture fixture = newFixture();
            String archived = secondRepository(fixture, remote());
            jdbcTemplate.update("UPDATE kaya.repositories SET status = 'ARCHIVED'::repository_repository_status_enum WHERE id = ?::uuid",
                    archived);
            String id = createLesson(fixture, fixture.ada().jwt(), "Before", null);

            mockMvc.perform(in(fixture.tenant(), put(LESSONS + "/" + id), fixture.ada().jwt(),
                            lessonBody("Moved", "Body.", archived)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath(ERROR_CODE).value("KAY_VAL_002"));
        }

        @Test
        void updateLesson_inRepositoryArchivedLater_stillSucceedsInPlace() throws Exception {
            Fixture fixture = newFixture();
            String id = createLesson(fixture, fixture.ada().jwt(), "Before", fixture.repositoryId());
            jdbcTemplate.update("UPDATE kaya.repositories SET status = 'ARCHIVED'::repository_repository_status_enum WHERE id = ?::uuid",
                    fixture.repositoryId());

            mockMvc.perform(in(fixture.tenant(), put(LESSONS + "/" + id), fixture.ada().jwt(),
                            lessonBody("Corrected", "Body.", fixture.repositoryId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.title").value("Corrected"));
        }

        @Test
        void updateLesson_unknownAndForeign_return404BeforeAnyAccessCheck() throws Exception {
            Fixture fixture = newFixture();
            Fixture other = newFixture();
            String foreign = createLesson(other, other.ada().jwt(), "Theirs", null);

            mockMvc.perform(in(fixture.tenant(), put(LESSONS + "/" + UUID.randomUUID()), fixture.ada().jwt(),
                            lessonBody("X", "Y", null)))
                    .andExpect(status().isNotFound());
            mockMvc.perform(in(fixture.tenant(), put(LESSONS + "/" + foreign), fixture.bo().jwt(),
                            lessonBody("X", "Y", null)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath(ERROR_CODE).value("KAY_NF_001"));
        }

        @Test
        void updateLesson_titleOver300_returns422() throws Exception {
            Fixture fixture = newFixture();
            String id = createLesson(fixture, fixture.ada().jwt(), "Before", null);

            mockMvc.perform(in(fixture.tenant(), put(LESSONS + "/" + id), fixture.ada().jwt(),
                            lessonBody("t".repeat(301), "Y", null)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath(ERROR_CODE).value("GEN_VAL_001"));
        }

        @Test
        void deleteLesson_byAuthor_removesTheRow() throws Exception {
            Fixture fixture = newFixture();
            String id = createLesson(fixture, fixture.ada().jwt(), "Doomed", null);

            mockMvc.perform(in(fixture.tenant(), delete(LESSONS + "/" + id), fixture.ada().jwt(), null))
                    .andExpect(status().isOk());
            assertEquals(0L, lessonRows(id));
        }

        @Test
        void deleteLesson_byAdmin_removesAnotherMembersLesson() throws Exception {
            Fixture fixture = newFixture();
            String id = createLesson(fixture, fixture.ada().jwt(), "Doomed", null);

            mockMvc.perform(in(fixture.tenant(), delete(LESSONS + "/" + id), fixture.adminJwt(), null))
                    .andExpect(status().isOk());
            assertEquals(0L, lessonRows(id));
        }

        @Test
        void deleteLesson_byOwner_removesAnotherMembersLesson() throws Exception {
            Fixture fixture = newFixture();
            String id = createLesson(fixture, fixture.ada().jwt(), "Doomed", null);

            mockMvc.perform(in(fixture.tenant(), delete(LESSONS + "/" + id), jwtOf(fixture.tenant().owner().getUser()), null))
                    .andExpect(status().isOk());
            assertEquals(0L, lessonRows(id));
        }

        @Test
        void deleteLesson_byAnotherMember_returns403AndKeepsTheRow() throws Exception {
            Fixture fixture = newFixture();
            String id = createLesson(fixture, fixture.ada().jwt(), "Kept", null);

            mockMvc.perform(in(fixture.tenant(), delete(LESSONS + "/" + id), fixture.bo().jwt(), null))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath(ERROR_CODE).value("GEN_SEC_003"));
            assertEquals(1L, lessonRows(id));
        }

        @Test
        void deleteLesson_unknownAndForeign_return404AndKeepTheForeignRow() throws Exception {
            Fixture fixture = newFixture();
            Fixture other = newFixture();
            String foreign = createLesson(other, other.ada().jwt(), "Theirs", null);

            mockMvc.perform(in(fixture.tenant(), delete(LESSONS + "/" + UUID.randomUUID()), fixture.ada().jwt(), null))
                    .andExpect(status().isNotFound());
            mockMvc.perform(in(fixture.tenant(), delete(LESSONS + "/" + foreign), fixture.adminJwt(), null))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath(ERROR_CODE).value("KAY_NF_001"));
            assertEquals(1L, lessonRows(foreign));
        }
    }

    private Map<String, Object> checkoutLessonWithSession(Fixture fixture, String title, String claudeSessionId) {
        Map<String, Object> body = checkoutLesson(fixture.remote(), title, false);
        body.put("claudeSessionId", claudeSessionId);
        return body;
    }
}
