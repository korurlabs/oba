package com.korur.oba.apps.kaya.session.integration;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractSessionIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SessionLinkAndDeleteIT extends AbstractSessionIT {

    private static final String PULL_REQUEST = "https://github.com/senseylabs/village/pull/643";

    private record Setup(Tenant tenant, Person ada, Person cy, Machine laptop, String session) {
    }

    private Setup newSetup() throws Exception {
        Tenant tenant = newTenant();
        Person ada = signIn(tenant, "ada");
        Person cy = signIn(tenant, "cy");
        Machine laptop = registerMachine(tenant, ada, "laptop");
        return new Setup(tenant, ada, cy, laptop, ingestSession(laptop, remote(), 3));
    }

    private static String link(String kind, String reference) {
        return "{\"kind\":\"" + kind + "\",\"reference\":\"" + reference + "\"}";
    }

    private MvcResult addLink(Setup setup, Person person, String body) throws Exception {
        return addLink(setup, person.jwt(), body);
    }

    private MvcResult addLink(Setup setup, RequestPostProcessor jwt, String body) throws Exception {
        return mockMvc.perform(in(setup.tenant(), post("/kaya/sessions/" + setup.session() + "/links"), jwt, body)).andReturn();
    }

    private long linkRows(String session) {
        return count("SELECT count(*) FROM kaya.session_links l JOIN kaya.sessions c ON c.id = l.session_id WHERE c.session_id = ?", session);
    }

    @Nested
    class AddLink {

        @Test
        void ticket_isStoredAsManualWithTheMembersId() throws Exception {
            Setup setup = newSetup();

            MvcResult result = addLink(setup, setup.ada(), link("KAZAN_TICKET", "SEN-42"));

            assertEquals(201, result.getResponse().getStatus());
            assertEquals("KAZAN_TICKET", read(result, "$.data.kind"));
            assertEquals("SEN-42", read(result, "$.data.reference"));
            assertEquals("MANUAL", read(result, "$.data.source"));
            assertEquals(setup.ada().user().getId().toString(), read(result, "$.data.createdBy"));
            assertEquals(1L, linkRows(setup.session()));
        }

        @Test
        void ticket_getsItsUrlFromTheRepositorysTemplate() throws Exception {
            Setup setup = newSetup();
            String repositoryId = jdbcTemplate.queryForObject(
                    "SELECT repository_id::text FROM kaya.sessions WHERE session_id = ?", String.class, setup.session());
            mockMvc.perform(patch("/kaya/repositories/" + repositoryId)
                            .with(jwtOf(setup.tenant().admin().getUser()))
                            .header("X-Organization-ID", setup.tenant().id())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ticketUrlTemplate\":\"https://kazan.example/tickets/{key}\"}"))
                    .andExpect(status().isOk());

            MvcResult result = addLink(setup, setup.ada(), link("KAZAN_TICKET", "SEN-42"));

            assertEquals("https://kazan.example/tickets/SEN-42", read(result, "$.data.url"));
        }

        @Test
        void ticket_withoutATemplate_hasNoUrl() throws Exception {
            Setup setup = newSetup();

            MvcResult result = addLink(setup, setup.ada(), link("KAZAN_TICKET", "SEN-42"));

            assertEquals("null", read(result, "$.data.url"));
        }

        @Test
        void pullRequest_usesItsReferenceAsUrl() throws Exception {
            Setup setup = newSetup();

            MvcResult result = addLink(setup, setup.ada(), link("PULL_REQUEST", PULL_REQUEST));

            assertEquals(201, result.getResponse().getStatus());
            assertEquals(PULL_REQUEST, read(result, "$.data.url"));
            assertEquals("MANUAL", read(result, "$.data.source"));
        }

        @Test
        void reference_isStrippedOfSurroundingWhitespace() throws Exception {
            Setup setup = newSetup();

            MvcResult result = addLink(setup, setup.ada(), link("KAZAN_TICKET", "  SEN-42  "));

            assertEquals("SEN-42", read(result, "$.data.reference"));
        }

        @Test
        void sameLinkTwice_returnsTheExistingLinkAndStoresOnce() throws Exception {
            Setup setup = newSetup();

            MvcResult first = addLink(setup, setup.ada(), link("KAZAN_TICKET", "SEN-42"));
            MvcResult second = addLink(setup, setup.cy(), link("KAZAN_TICKET", "SEN-42"));

            assertEquals(201, second.getResponse().getStatus());
            assertEquals(read(first, "$.data.id"), read(second, "$.data.id"));
            assertEquals(1L, linkRows(setup.session()));
        }

        @Test
        void linkTheRecorderAlreadyDerived_isNotDuplicated() throws Exception {
            Setup setup = newSetup();
            String session = ingestSession(setup.laptop(), remote(), 1, batch -> section(batch, "summary").put("ticket", "KAYA-7"));

            MvcResult result = mockMvc.perform(in(setup.tenant(), post("/kaya/sessions/" + session + "/links"), setup.ada().jwt(),
                    link("KAZAN_TICKET", "KAYA-7"))).andReturn();

            assertEquals("RECORDER", read(result, "$.data.source"));
            assertEquals(1L, linkRows(session));
        }

        @Test
        void listAndDetail_showTheManualLink() throws Exception {
            Setup setup = newSetup();
            addLink(setup, setup.ada(), link("PULL_REQUEST", PULL_REQUEST));

            mockMvc.perform(in(setup.tenant(), get("/kaya/sessions/" + setup.session()), setup.ada().jwt(), null))
                    .andExpect(jsonPath("$.data.links.length()").value(1))
                    .andExpect(jsonPath("$.data.links[0].reference").value(PULL_REQUEST));
            mockMvc.perform(in(setup.tenant(), get("/kaya/sessions"), setup.ada().jwt(), null))
                    .andExpect(jsonPath("$.data[0].links.length()").value(1));
        }

        @Test
        void ticketThatBreaksTheKeyPattern_answers422WithTheRule() throws Exception {
            Setup setup = newSetup();

            for (String reference : new String[] {"sen-42", "SEN42", "S-1", "SEN-x", "SEN-42 extra"}) {
                MvcResult result = addLink(setup, setup.ada(), link("KAZAN_TICKET", reference));

                assertEquals(422, result.getResponse().getStatus(), reference);
                assertEquals("KAY_VAL_015", read(result, "$.error.code"));
                assertEquals("KAZAN_TICKET_KEY", read(result, "$.error.errorValues.rule"));
                assertEquals("KAZAN_TICKET", read(result, "$.error.errorValues.kind"));
            }
            assertEquals(0L, linkRows(setup.session()));
        }

        @Test
        void pullRequestThatBreaksTheUrlPattern_answers422WithTheRule() throws Exception {
            Setup setup = newSetup();

            for (String reference : new String[] {"http://github.com/a/b/pull/1", "https://github.com/a/b/issues/1",
                    "https://github.com/a/b/pull/x", "https://example.com/a/b/pull/1", "SEN-42"}) {
                MvcResult result = addLink(setup, setup.ada(), link("PULL_REQUEST", reference));

                assertEquals(422, result.getResponse().getStatus(), reference);
                assertEquals("KAY_VAL_015", read(result, "$.error.code"));
                assertEquals("PULL_REQUEST_URL", read(result, "$.error.errorValues.rule"));
            }
            assertEquals(0L, linkRows(setup.session()));
        }

        @Test
        void malformedBody_answers422() throws Exception {
            Setup setup = newSetup();

            for (String body : new String[] {"{\"kind\":\"KAZAN_TICKET\",\"reference\":\"\"}",
                    "{\"kind\":\"KAZAN_TICKET\",\"reference\":\"   \"}",
                    "{\"kind\":\"KAZAN_TICKET\"}",
                    "{\"reference\":\"SEN-42\"}",
                    link("KAZAN_TICKET", "S".repeat(501))}) {
                assertEquals(422, addLink(setup, setup.ada(), body).getResponse().getStatus(), body);
            }
        }

        @Test
        void unknownKind_isRefused() throws Exception {
            Setup setup = newSetup();

            int httpStatus = addLink(setup, setup.ada(), link("COMMIT", "abc123")).getResponse().getStatus();

            assertEquals(true, httpStatus == 400 || httpStatus == 422, "status " + httpStatus);
            assertEquals(0L, linkRows(setup.session()));
        }

        @Test
        void anyMemberOfTheOrganization_mayAddALink() throws Exception {
            Setup setup = newSetup();

            assertEquals(201, addLink(setup, setup.cy(), link("KAZAN_TICKET", "SEN-1")).getResponse().getStatus());
            assertEquals(201, addLink(setup, jwtOf(setup.tenant().member().getUser()),
                    link("KAZAN_TICKET", "SEN-2")).getResponse().getStatus());
        }

        @Test
        void personOutsideTheOrganization_answers403() throws Exception {
            Setup setup = newSetup();
            Tenant outsiderTenant = newTenant();
            Person outsider = signIn(outsiderTenant, "eve");

            assertEquals(403, addLink(setup, outsider, link("KAZAN_TICKET", "SEN-1")).getResponse().getStatus());
            assertEquals(0L, linkRows(setup.session()));
        }

        @Test
        void sessionOfAnotherOrganization_answers404() throws Exception {
            Setup setup = newSetup();
            Setup other = newSetup();

            MvcResult result = mockMvc.perform(in(other.tenant(), post("/kaya/sessions/" + setup.session() + "/links"),
                    other.ada().jwt(), link("KAZAN_TICKET", "SEN-1"))).andReturn();

            assertEquals(404, result.getResponse().getStatus());
            assertEquals("KAY_NF_001", read(result, "$.error.code"));
            assertEquals(0L, linkRows(setup.session()));
        }
    }

    @Nested
    class RemoveLink {

        @Test
        void link_isRemovedAndTheRestStays() throws Exception {
            Setup setup = newSetup();
            String keep = read(addLink(setup, setup.ada(), link("KAZAN_TICKET", "SEN-1")), "$.data.id");
            String drop = read(addLink(setup, setup.ada(), link("PULL_REQUEST", PULL_REQUEST)), "$.data.id");

            mockMvc.perform(in(setup.tenant(), delete("/kaya/sessions/" + setup.session() + "/links/" + drop), setup.cy().jwt(), null))
                    .andExpect(status().isOk());

            assertEquals(1L, linkRows(setup.session()));
            assertEquals(1L, count("SELECT count(*) FROM kaya.session_links WHERE id = ?::uuid", keep));
        }

        @Test
        void recorderLink_mayBeRemovedToo() throws Exception {
            Setup setup = newSetup();
            String session = ingestSession(setup.laptop(), remote(), 1, batch -> section(batch, "summary").put("ticket", "KAYA-7"));
            String linkId = jdbcTemplate.queryForObject("""
                    SELECT l.id::text FROM kaya.session_links l JOIN kaya.sessions c ON c.id = l.session_id WHERE c.session_id = ?
                    """, String.class, session);

            mockMvc.perform(in(setup.tenant(), delete("/kaya/sessions/" + session + "/links/" + linkId), setup.ada().jwt(), null))
                    .andExpect(status().isOk());

            assertEquals(0L, linkRows(session));
        }

        @Test
        void unknownLink_answers404WithTheLinkId() throws Exception {
            Setup setup = newSetup();
            String linkId = UUID.randomUUID().toString();

            mockMvc.perform(in(setup.tenant(), delete("/kaya/sessions/" + setup.session() + "/links/" + linkId), setup.ada().jwt(), null))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("KAY_NF_001"))
                    .andExpect(jsonPath("$.error.errorValues.linkId").value(linkId));
        }

        @Test
        void linkOfAnotherSession_answers404AndStays() throws Exception {
            Setup setup = newSetup();
            String otherSession = ingestSession(setup.laptop(), remote(), 1);
            String linkId = read(addLink(setup, setup.ada(), link("KAZAN_TICKET", "SEN-1")), "$.data.id");

            mockMvc.perform(in(setup.tenant(), delete("/kaya/sessions/" + otherSession + "/links/" + linkId), setup.ada().jwt(), null))
                    .andExpect(status().isNotFound());

            assertEquals(1L, linkRows(setup.session()));
        }

        @Test
        void sessionOfAnotherOrganization_answers404AndTheLinkStays() throws Exception {
            Setup setup = newSetup();
            Setup other = newSetup();
            String linkId = read(addLink(setup, setup.ada(), link("KAZAN_TICKET", "SEN-1")), "$.data.id");

            mockMvc.perform(in(other.tenant(), delete("/kaya/sessions/" + setup.session() + "/links/" + linkId), other.ada().jwt(), null))
                    .andExpect(status().isNotFound());

            assertEquals(1L, linkRows(setup.session()));
        }
    }

    @Nested
    class DeleteSession {

        @Test
        void ownUser_deletesTheSessionWithEverythingItHolds() throws Exception {
            Setup setup = newSetup();
            String session = ingestSession(setup.laptop(), remote(), 4, batch -> {
                section(batch, "summary").put("ticket", "KAYA-7");
                withHandoffs(batch, handoff("f/planning.md", "# P\n\n**Status:** DONE\n", "2026-10-20T09:10:00Z"));
            });
            String sessionRowId = jdbcTemplate.queryForObject("SELECT id::text FROM kaya.sessions WHERE session_id = ?", String.class, session);

            mockMvc.perform(in(setup.tenant(), delete("/kaya/sessions/" + session), setup.ada().jwt(), null))
                    .andExpect(status().isOk());

            assertEquals(0L, sessionCount(session));
            for (String table : new String[] {"session_events", "session_links", "session_handoff_snapshots", "session_subagents", "session_skill_uses"}) {
                assertEquals(0L, count("SELECT count(*) FROM kaya." + table + " WHERE session_id = ?::uuid", sessionRowId), table);
            }
            mockMvc.perform(in(setup.tenant(), get("/kaya/sessions/" + session), setup.ada().jwt(), null))
                    .andExpect(status().isNotFound());
        }

        @Test
        void organizationAdmin_deletesAnotherUsersSession() throws Exception {
            Setup setup = newSetup();

            mockMvc.perform(in(setup.tenant(), delete("/kaya/sessions/" + setup.session()), jwtOf(setup.tenant().admin().getUser()), null))
                    .andExpect(status().isOk());

            assertEquals(0L, sessionCount(setup.session()));
        }

        @Test
        void otherMember_answers403AndTheSessionStays() throws Exception {
            Setup setup = newSetup();

            mockMvc.perform(in(setup.tenant(), delete("/kaya/sessions/" + setup.session()), setup.cy().jwt(), null))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("GEN_SEC_003"));

            assertEquals(1L, sessionCount(setup.session()));
        }

        @Test
        void unknownSession_answers404() throws Exception {
            Setup setup = newSetup();

            mockMvc.perform(in(setup.tenant(), delete("/kaya/sessions/no-such-session"), setup.ada().jwt(), null))
                    .andExpect(status().isNotFound());
        }

        @Test
        void sessionOfAnotherOrganization_answers404EvenForItsAdmin() throws Exception {
            Setup setup = newSetup();
            Setup other = newSetup();

            mockMvc.perform(in(other.tenant(), delete("/kaya/sessions/" + setup.session()), jwtOf(other.tenant().admin().getUser()), null))
                    .andExpect(status().isNotFound());

            assertEquals(1L, sessionCount(setup.session()));
        }

        @Test
        void deleteTwice_answers404TheSecondTime() throws Exception {
            Setup setup = newSetup();

            mockMvc.perform(in(setup.tenant(), delete("/kaya/sessions/" + setup.session()), setup.ada().jwt(), null)).andExpect(status().isOk());
            mockMvc.perform(in(setup.tenant(), delete("/kaya/sessions/" + setup.session()), setup.ada().jwt(), null)).andExpect(status().isNotFound());
        }

        @Test
        void auditEntriesOfTheUser_surviveTheDelete() throws Exception {
            Setup setup = newSetup();
            long before = count("SELECT count(*) FROM kaya.user_audit_entries WHERE user_id = ?::uuid", setup.ada().userId());

            mockMvc.perform(in(setup.tenant(), delete("/kaya/sessions/" + setup.session()), setup.ada().jwt(), null)).andExpect(status().isOk());

            assertEquals(before, count("SELECT count(*) FROM kaya.user_audit_entries WHERE user_id = ?::uuid", setup.ada().userId()));
        }
    }

    @Nested
    class CountsOnMachines {

        @Test
        void machine_countsItsSessionsAndFollowsDeletes() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            Machine laptop = registerMachine(tenant, ada, "laptop");
            Machine desktop = registerMachine(tenant, ada, "desktop");
            String remote = remote();
            String first = ingestSession(laptop, remote, 2);
            ingestSession(laptop, remote, 3);
            ingestSession(desktop, remote, 4);

            mockMvc.perform(in(tenant, get("/kaya/machines/" + laptop.machineId()), ada.jwt(), null))
                    .andExpect(jsonPath("$.data.sessionCount").value(2));
            mockMvc.perform(in(tenant, get("/kaya/machines/" + desktop.machineId()), ada.jwt(), null))
                    .andExpect(jsonPath("$.data.sessionCount").value(1));

            mockMvc.perform(in(tenant, delete("/kaya/sessions/" + first), ada.jwt(), null)).andExpect(status().isOk());

            mockMvc.perform(in(tenant, get("/kaya/machines/" + laptop.machineId()), ada.jwt(), null))
                    .andExpect(jsonPath("$.data.sessionCount").value(1));
            mockMvc.perform(in(tenant, get("/kaya/machines/" + desktop.machineId()), ada.jwt(), null))
                    .andExpect(jsonPath("$.data.sessionCount").value(1));
        }
    }
}
