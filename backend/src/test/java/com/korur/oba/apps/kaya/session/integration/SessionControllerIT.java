package com.korur.oba.apps.kaya.session.integration;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

// 2. Third-party
import com.jayway.jsonpath.JsonPath;

// 3. Java SDK
import java.util.List;
import java.util.Map;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractSessionIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SessionControllerIT extends AbstractSessionIT {

    private static final String HANDOFF_PATH = "board-export/planning.md";
    private static final String HANDOFF_CONTENT = "# Planning\n\n**Status:** PARTIAL\n";

    /** One organization with two users on two machines, the way the wall sees a team. */
    private record Team(Tenant tenant, Person ada, Person cy, Machine adaLaptop, Machine cyDesktop) {
    }

    private Team newTeam() throws Exception {
        Tenant tenant = newTenant();
        Person ada = signIn(tenant, "ada");
        Person cy = signIn(tenant, "cy");
        return new Team(tenant, ada, cy, registerMachine(tenant, ada, "laptop"), registerMachine(tenant, cy, "desktop"));
    }

    private MvcResult list(Team team, String query) throws Exception {
        return mockMvc.perform(in(team.tenant(), get("/kaya/sessions" + query), team.ada().jwt(), null))
                .andExpect(status().isOk())
                .andReturn();
    }

    private List<String> sessionsOf(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data[*].sessionId");
    }

    @Nested
    class ListSessions {

        @Test
        void sessions_areOrderedByLastEventNewestFirst() throws Exception {
            Team team = newTeam();
            String remote = remote();
            String oldest = ingestSession(team.adaLaptop(), remote, 2);
            String newest = ingestSession(team.adaLaptop(), remote, 9);
            String middle = ingestSession(team.adaLaptop(), remote, 5);

            assertEquals(List.of(newest, middle, oldest), sessionsOf(list(team, "")));
        }

        @Test
        void session_carriesNamesCountsAndLinksOfItsRow() throws Exception {
            Team team = newTeam();
            String session = ingestSession(team.adaLaptop(), remote(), 3, batch -> {
                section(batch, "summary").put("title", "Export the board");
                section(batch, "summary").put("branch", "feature/board-export");
                section(batch, "summary").put("model", "claude-opus-5-5");
                section(batch, "summary").put("ticket", "KAYA-42");
                section(batch, "machine").put("account", account("ada.claude@" + team.tenant().domain()));
            });

            mockMvc.perform(in(team.tenant(), get("/kaya/sessions"), team.ada().jwt(), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].sessionId").value(session))
                    .andExpect(jsonPath("$.data[0].title").value("Export the board"))
                    .andExpect(jsonPath("$.data[0].branch").value("feature/board-export"))
                    .andExpect(jsonPath("$.data[0].userId").value(team.ada().userId()))
                    .andExpect(jsonPath("$.data[0].userDisplayName").isNotEmpty())
                    .andExpect(jsonPath("$.data[0].machineId").value(team.adaLaptop().machineId()))
                    .andExpect(jsonPath("$.data[0].machineName").value("laptop"))
                    .andExpect(jsonPath("$.data[0].repositoryName").value("scratch"))
                    .andExpect(jsonPath("$.data[0].eventCount").value(3))
                    .andExpect(jsonPath("$.data[0].ended").value(false))
                    .andExpect(jsonPath("$.data[0].accountOrganizationUuid").value(CLAUDE_ORGANIZATION_UUID))
                    .andExpect(jsonPath("$.data[0].links.length()").value(1))
                    .andExpect(jsonPath("$.data[0].links[0].kind").value("KAZAN_TICKET"))
                    .andExpect(jsonPath("$.data[0].links[0].source").value("RECORDER"));
        }

        @Test
        void sessionWithoutLinks_hasAnEmptyLinkList() throws Exception {
            Team team = newTeam();
            ingestSession(team.adaLaptop(), remote(), 1);

            mockMvc.perform(in(team.tenant(), get("/kaya/sessions"), team.ada().jwt(), null))
                    .andExpect(jsonPath("$.data[0].links.length()").value(0));
        }

        @Test
        void emptyOrganization_returnsAnEmptyPage() throws Exception {
            Team team = newTeam();

            mockMvc.perform(in(team.tenant(), get("/kaya/sessions"), team.ada().jwt(), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0))
                    .andExpect(jsonPath("$.pagination.totalElements").value(0));
        }

        @Test
        void paging_splitsTheSessionsAndReportsTheTotals() throws Exception {
            Team team = newTeam();
            String remote = remote();
            String first = ingestSession(team.adaLaptop(), remote, 5);
            String second = ingestSession(team.adaLaptop(), remote, 4);
            String third = ingestSession(team.adaLaptop(), remote, 3);

            MvcResult pageOne = list(team, "?page=1&amount=2");
            MvcResult pageTwo = list(team, "?page=2&amount=2");

            assertEquals(List.of(first, second), sessionsOf(pageOne));
            assertEquals(List.of(third), sessionsOf(pageTwo));
            assertEquals("3", read(pageOne, "$.pagination.totalElements"));
            assertEquals("2", read(pageOne, "$.pagination.totalPages"));
            assertEquals("2", read(pageTwo, "$.pagination.currentPage"));
        }

        @Test
        void amountOfSixty_isTheDefaultAndTheLimit() throws Exception {
            Team team = newTeam();
            ingestSession(team.adaLaptop(), remote(), 1);

            assertEquals("60", read(list(team, ""), "$.pagination.pageSize"));
            assertEquals("60", read(list(team, "?amount=60"), "$.pagination.pageSize"));
            mockMvc.perform(in(team.tenant(), get("/kaya/sessions?amount=61"), team.ada().jwt(), null))
                    .andExpect(status().isUnprocessableEntity());
            mockMvc.perform(in(team.tenant(), get("/kaya/sessions?amount=0"), team.ada().jwt(), null))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void searchOverLimit_isRefused() throws Exception {
            Team team = newTeam();

            mockMvc.perform(in(team.tenant(), get("/kaya/sessions").param("search", "x".repeat(201)), team.ada().jwt(), null))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    @Nested
    class Filters {

        @Test
        void search_matchesTitleBranchAndTicketKeyIgnoringCase() throws Exception {
            Team team = newTeam();
            String remote = remote();
            String byTitle = ingestSession(team.adaLaptop(), remote, 1, batch -> section(batch, "summary").put("title", "Export the Board"));
            String byBranch = ingestSession(team.adaLaptop(), remote, 1, batch -> section(batch, "summary").put("branch", "feature/EXPORT-csv"));
            String byTicket = ingestSession(team.adaLaptop(), remote, 1, batch -> section(batch, "summary").put("ticket", "KAYA-42"));
            ingestSession(team.adaLaptop(), remote, 1, batch -> section(batch, "summary").put("title", "Unrelated"));

            List<String> exports = sessionsOf(list(team, "?search=export"));
            List<String> tickets = sessionsOf(list(team, "?search=kaya-42"));

            assertEquals(2, exports.size());
            assertEquals(true, exports.containsAll(List.of(byTitle, byBranch)));
            assertEquals(List.of(byTicket), tickets);
            assertEquals(List.of(), sessionsOf(list(team, "?search=nothing-like-it")));
        }

        @Test
        void searchWithLikeWildcards_matchesThemLiterally() throws Exception {
            Team team = newTeam();
            String remote = remote();
            String percent = ingestSession(team.adaLaptop(), remote, 1, batch -> section(batch, "summary").put("title", "Reach 100% coverage"));
            ingestSession(team.adaLaptop(), remote, 1, batch -> section(batch, "summary").put("title", "Reach 100 percent"));

            MvcResult result = mockMvc.perform(in(team.tenant(), get("/kaya/sessions").param("search", "100%"), team.ada().jwt(), null))
                    .andExpect(status().isOk())
                    .andReturn();

            assertEquals(List.of(percent), sessionsOf(result));
        }

        @Test
        void repositoryId_returnsOnlyThatRepositorysSessions() throws Exception {
            Team team = newTeam();
            String inFirst = ingestSession(team.adaLaptop(), remote(), 1);
            ingestSession(team.adaLaptop(), remote(), 1);
            String repositoryId = jdbcTemplate.queryForObject(
                    "SELECT repository_id::text FROM kaya.sessions WHERE session_id = ?", String.class, inFirst);

            assertEquals(List.of(inFirst), sessionsOf(list(team, "?repositoryId=" + repositoryId)));
        }

        @Test
        void userId_returnsOnlyThatUsersSessions() throws Exception {
            Team team = newTeam();
            String remote = remote();
            String adas = ingestSession(team.adaLaptop(), remote, 1);
            String cys = ingestSession(team.cyDesktop(), remote, 1);

            assertEquals(List.of(adas), sessionsOf(list(team, "?userId=" + team.ada().userId())));
            assertEquals(List.of(cys), sessionsOf(list(team, "?userId=" + team.cy().userId())));
        }

        @Test
        void machineId_returnsOnlyThatMachinesSessions() throws Exception {
            Team team = newTeam();
            String remote = remote();
            ingestSession(team.adaLaptop(), remote, 1);
            String cys = ingestSession(team.cyDesktop(), remote, 1);

            assertEquals(List.of(cys), sessionsOf(list(team, "?machineId=" + team.cyDesktop().machineId())));
        }

        @Test
        void model_returnsOnlySessionsOnThatModel() throws Exception {
            Team team = newTeam();
            String remote = remote();
            String opus = ingestSession(team.adaLaptop(), remote, 1, batch -> section(batch, "summary").put("model", "claude-opus-5-5"));
            ingestSession(team.adaLaptop(), remote, 1, batch -> section(batch, "summary").put("model", "claude-haiku-5"));

            assertEquals(List.of(opus), sessionsOf(list(team, "?model=claude-opus-5-5")));
        }

        @Test
        void accountOrganizationUuid_returnsOnlySessionsOfThatSubscription() throws Exception {
            Team team = newTeam();
            String remote = remote();
            String withAccount = ingestSession(team.adaLaptop(), remote, 1,
                    batch -> section(batch, "machine").put("account", account("ada.claude@" + team.tenant().domain())));
            ingestSession(team.cyDesktop(), remote, 1);

            assertEquals(List.of(withAccount), sessionsOf(list(team, "?accountOrganizationUuid=" + CLAUDE_ORGANIZATION_UUID)));
        }

        @Test
        void ended_splitsFinishedFromRunningSessions() throws Exception {
            Team team = newTeam();
            String remote = remote();
            String finished = ingestSession(team.adaLaptop(), remote, 1, batch -> section(batch, "summary").put("ended", true));
            String running = ingestSession(team.adaLaptop(), remote, 1);

            assertEquals(List.of(finished), sessionsOf(list(team, "?ended=true")));
            assertEquals(List.of(running), sessionsOf(list(team, "?ended=false")));
            assertEquals(2, sessionsOf(list(team, "")).size());
        }

        @Test
        void combinedFilters_applyTogether() throws Exception {
            Team team = newTeam();
            String remote = remote();
            String match = ingestSession(team.adaLaptop(), remote, 1, batch -> {
                section(batch, "summary").put("model", "claude-opus-5-5");
                section(batch, "summary").put("title", "Export");
            });
            ingestSession(team.cyDesktop(), remote, 1, batch -> {
                section(batch, "summary").put("model", "claude-opus-5-5");
                section(batch, "summary").put("title", "Export");
            });
            ingestSession(team.adaLaptop(), remote, 1, batch -> section(batch, "summary").put("model", "claude-haiku-5"));

            assertEquals(List.of(match), sessionsOf(list(team,
                    "?search=export&model=claude-opus-5-5&userId=" + team.ada().userId())));
        }

        @Test
        void filterOptions_listOnlyValuesTheOrganizationsSessionsHold() throws Exception {
            Team team = newTeam();
            Team other = newTeam();
            String remote = remote();
            ingestSession(team.adaLaptop(), remote, 1, batch -> {
                section(batch, "summary").put("model", "claude-opus-5-5");
                section(batch, "machine").put("account", account("ada.claude@" + team.tenant().domain()));
            });
            ingestSession(other.adaLaptop(), remote(), 1, batch -> section(batch, "summary").put("model", "claude-other-9"));

            mockMvc.perform(in(team.tenant(), get("/kaya/sessions/filters"), team.ada().jwt(), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.users.length()").value(1))
                    .andExpect(jsonPath("$.data.users[0].id").value(team.ada().userId()))
                    .andExpect(jsonPath("$.data.machines.length()").value(1))
                    .andExpect(jsonPath("$.data.machines[0].machineName").value("laptop"))
                    .andExpect(jsonPath("$.data.machines[0].userId").value(team.ada().userId()))
                    .andExpect(jsonPath("$.data.models.length()").value(1))
                    .andExpect(jsonPath("$.data.models[0]").value("claude-opus-5-5"))
                    .andExpect(jsonPath("$.data.subscriptions.length()").value(1))
                    .andExpect(jsonPath("$.data.subscriptions[0].uuid").value(CLAUDE_ORGANIZATION_UUID));
        }

        @Test
        void filterOptions_ofAnEmptyOrganization_areEmpty() throws Exception {
            Team team = newTeam();

            mockMvc.perform(in(team.tenant(), get("/kaya/sessions/filters"), team.ada().jwt(), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.users.length()").value(0))
                    .andExpect(jsonPath("$.data.models.length()").value(0));
        }
    }

    @Nested
    class Detail {

        @Test
        void session_returnsTheSummaryStagesSubagentsAndHandoffs() throws Exception {
            Team team = newTeam();
            String session = ingestSession(team.adaLaptop(), remote(), 4, batch -> {
                section(batch, "summary").put("title", "Export the board");
                section(batch, "summary").put("session_title", "Exporting");
                section(batch, "summary").put("first_prompt", "Export the board as CSV");
                section(batch, "summary").put("cwd", "/work/repo");
                withHandoffs(batch, handoff(HANDOFF_PATH, HANDOFF_CONTENT, "2026-10-20T09:10:00Z"));
            });

            mockMvc.perform(in(team.tenant(), get("/kaya/sessions/" + session), team.ada().jwt(), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.sessionId").value(session))
                    .andExpect(jsonPath("$.data.title").value("Export the board"))
                    .andExpect(jsonPath("$.data.sessionTitle").value("Exporting"))
                    .andExpect(jsonPath("$.data.firstPrompt").value("Export the board as CSV"))
                    .andExpect(jsonPath("$.data.cwd").value("/work/repo"))
                    .andExpect(jsonPath("$.data.eventCount").value(4))
                    .andExpect(jsonPath("$.data.machineName").value("laptop"))
                    .andExpect(jsonPath("$.data.stages").doesNotExist())
                    .andExpect(jsonPath("$.data.subagents").isArray())
                    .andExpect(jsonPath("$.data.skillUses").isArray())
                    .andExpect(jsonPath("$.data.handoffs.length()").value(1))
                    .andExpect(jsonPath("$.data.handoffs[0].path").value(HANDOFF_PATH))
                    .andExpect(jsonPath("$.data.handoffs[0].status").value("PARTIAL"))
                    .andExpect(jsonPath("$.data.handoffs[0].content").doesNotExist());
        }

        @Test
        void unknownSession_answers404() throws Exception {
            Team team = newTeam();

            mockMvc.perform(in(team.tenant(), get("/kaya/sessions/no-such-session"), team.ada().jwt(), null))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("KAY_NF_001"))
                    .andExpect(jsonPath("$.error.errorValues.sessionId").value("no-such-session"));
        }

        @Test
        void sessionIdWithForbiddenCharacters_answers422() throws Exception {
            Team team = newTeam();

            mockMvc.perform(in(team.tenant(), get("/kaya/sessions/bad.session"), team.ada().jwt(), null))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));
        }
    }

    @Nested
    class EventsAndHandoffs {

        @Test
        void events_comeInSeqOrderWithTheRecordedFields() throws Exception {
            Team team = newTeam();
            String session = ingestSession(team.adaLaptop(), remote(), 3);

            mockMvc.perform(in(team.tenant(), get("/kaya/sessions/" + session + "/events"), team.ada().jwt(), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(3))
                    .andExpect(jsonPath("$.data[0].seq").value(1))
                    .andExpect(jsonPath("$.data[2].seq").value(3))
                    .andExpect(jsonPath("$.data[0].event").value("UserPromptSubmit"))
                    .andExpect(jsonPath("$.data[0].at").value(BASE_TIME.plusSeconds(1).toString()));
        }

        @Test
        void events_afterSeq_returnOnlyLaterEvents() throws Exception {
            Team team = newTeam();
            String session = ingestSession(team.adaLaptop(), remote(), 5);

            mockMvc.perform(in(team.tenant(), get("/kaya/sessions/" + session + "/events?afterSeq=3"), team.ada().jwt(), null))
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].seq").value(4));
            mockMvc.perform(in(team.tenant(), get("/kaya/sessions/" + session + "/events?afterSeq=5"), team.ada().jwt(), null))
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        void events_keepThePayloadFieldsOfTheRecorder() throws Exception {
            Team team = newTeam();
            String session = sessionId();
            Map<String, Object> bash = event(1, "PreToolUse");
            bash.put("tool_name", "Bash");
            bash.put("command", "git status");
            send(team.adaLaptop().token(), batch(team.adaLaptop(), session, remote(), List.of(bash))).andExpect(status().isOk());

            mockMvc.perform(in(team.tenant(), get("/kaya/sessions/" + session + "/events"), team.ada().jwt(), null))
                    .andExpect(jsonPath("$.data[0].tool_name").value("Bash"))
                    .andExpect(jsonPath("$.data[0].command").value("git status"));
        }

        @Test
        void events_negativeAfterSeq_answers422() throws Exception {
            Team team = newTeam();
            String session = ingestSession(team.adaLaptop(), remote(), 1);

            mockMvc.perform(in(team.tenant(), get("/kaya/sessions/" + session + "/events?afterSeq=-1"), team.ada().jwt(), null))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void handoffs_returnContentAndStatusByPath() throws Exception {
            Team team = newTeam();
            String session = ingestSession(team.adaLaptop(), remote(), 1, batch -> withHandoffs(batch,
                    handoff("c-feature/review.md", "# R\n\n**Status:** DONE\n", "2026-10-20T09:20:00Z"),
                    handoff(HANDOFF_PATH, HANDOFF_CONTENT, "2026-10-20T09:10:00Z")));

            mockMvc.perform(in(team.tenant(), get("/kaya/sessions/" + session + "/handoffs"), team.ada().jwt(), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].path").value(HANDOFF_PATH))
                    .andExpect(jsonPath("$.data[0].content").value(HANDOFF_CONTENT))
                    .andExpect(jsonPath("$.data[0].status").value("PARTIAL"))
                    .andExpect(jsonPath("$.data[1].path").value("c-feature/review.md"))
                    .andExpect(jsonPath("$.data[1].status").value("DONE"));
        }

        @Test
        void handoffs_ofASessionWithNone_areEmpty() throws Exception {
            Team team = newTeam();
            String session = ingestSession(team.adaLaptop(), remote(), 1);

            mockMvc.perform(in(team.tenant(), get("/kaya/sessions/" + session + "/handoffs"), team.ada().jwt(), null))
                    .andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    @Nested
    class TenantIsolation {

        @Test
        void sessionOfAnotherOrganization_answers404OnEveryRead() throws Exception {
            Team team = newTeam();
            Team other = newTeam();
            String session = ingestSession(team.adaLaptop(), remote(), 2);

            for (String path : List.of("/kaya/sessions/" + session, "/kaya/sessions/" + session + "/events", "/kaya/sessions/" + session + "/handoffs")) {
                mockMvc.perform(in(other.tenant(), get(path), other.ada().jwt(), null))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.error.code").value("KAY_NF_001"))
                        .andExpect(jsonPath("$.error.errorValues.sessionId").value(session));
            }
        }

        @Test
        void list_neverShowsAnotherOrganizationsSessions() throws Exception {
            Team team = newTeam();
            Team other = newTeam();
            ingestSession(team.adaLaptop(), remote(), 2);
            String others = ingestSession(other.adaLaptop(), remote(), 2);

            assertEquals(List.of(others), sessionsOf(list(other, "")));
            assertEquals(1, sessionsOf(list(team, "")).size());
        }

        @Test
        void filterById_ofAnotherOrganization_matchesNothing() throws Exception {
            Team team = newTeam();
            Team other = newTeam();
            ingestSession(team.adaLaptop(), remote(), 1);

            assertEquals(List.of(), sessionsOf(list(other, "?userId=" + team.ada().userId())));
            assertEquals(List.of(), sessionsOf(list(other, "?machineId=" + team.adaLaptop().machineId())));
        }

        @Test
        void memberOfAnotherOrganization_isRefusedWithThisOrganizationsHeader() throws Exception {
            Team team = newTeam();
            Team other = newTeam();

            mockMvc.perform(in(team.tenant(), get("/kaya/sessions"), other.ada().jwt(), null))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class Authentication {

        @Test
        void requestWithoutJwt_answers401() throws Exception {
            Team team = newTeam();

            mockMvc.perform(get("/kaya/sessions").header("X-Organization-ID", team.tenant().id()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void requestWithMachineToken_answers401() throws Exception {
            Team team = newTeam();

            mockMvc.perform(get("/kaya/sessions")
                            .header("X-Organization-ID", team.tenant().id())
                            .header("Authorization", "Bearer " + team.adaLaptop().token()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void requestWithoutOrganizationHeader_isRefused() throws Exception {
            Team team = newTeam();

            mockMvc.perform(get("/kaya/sessions").with(team.ada().jwt()))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class RemovedPath {

        @Test
        void formerChatsPath_answers404() throws Exception {
            Team team = newTeam();

            mockMvc.perform(in(team.tenant(), get("/kaya/chats"), team.ada().jwt(), null))
                    .andExpect(status().isNotFound());
        }

        @Test
        void formerChatsDetailPath_answers404() throws Exception {
            Team team = newTeam();
            String session = ingestSession(team.adaLaptop(), remote(), 2);

            mockMvc.perform(in(team.tenant(), get("/kaya/chats/" + session), team.ada().jwt(), null))
                    .andExpect(status().isNotFound());
        }
    }
}
