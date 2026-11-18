package com.korur.oba.apps.kaya.ingest.integration;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractIngestIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IngestIdentityLearningIT extends AbstractIngestIT {

    private Map<String, Object> withIdentity(Machine machine, String session, String gitEmail, Map<String, Object> account) {
        Map<String, Object> batch = batch(machine, session, remote(), events(1, 1));
        if (gitEmail != null) {
            section(batch, "summary").put("gitAuthorEmail", gitEmail);
        }
        if (account != null) {
            section(batch, "machine").put("account", account);
        }
        return batch;
    }

    private String conflictUserOf(String session) {
        Object value = jdbcTemplate.queryForMap("SELECT identity_conflict_user_id FROM kaya.sessions WHERE session_id = ?", session)
                .get("identity_conflict_user_id");
        return value == null ? null : value.toString();
    }

    private long auditEntries(String userId, String action) {
        return count("""
                SELECT count(*) FROM kaya.user_audit_entries
                WHERE user_id = ?::uuid AND action::text = ? AND actor_kind::text = 'RECORDER'
                """, userId, action);
    }

    private long gitEmailRows(Tenant tenant, String email) {
        return count("SELECT count(*) FROM kaya.user_git_emails WHERE organization_id = ?::uuid AND email = ?",
                tenant.id(), email);
    }

    @Nested
    class GitEmail {

        @Test
        void unknownGitEmail_answers403AndStoresNothing() throws Exception {
            Tenant tenant = newTenant();
            Machine machine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            String email = "Ada.Git@" + tenant.domain();
            String session = sessionId();
            Map<String, Object> batch = withIdentity(machine, session, email, account("ada.claude@" + tenant.domain()));

            send(machine.token(), batch)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("KAY_SEC_007"))
                    .andExpect(jsonPath("$.error.errorValues.gitAuthorEmail").value(email));

            assertEquals(0L, sessionCount(session));
            assertEquals(0L, eventCount(session));
            assertEquals(0L, gitEmailRows(tenant, email.toLowerCase()));
            assertEquals(0L, count("SELECT count(*) FROM kaya.accounts WHERE organization_id = ?::uuid", tenant.id()));
            assertEquals(0L, count("SELECT count(*) FROM kaya.repositories WHERE organization_id = ?::uuid", tenant.id()));
        }

        @Test
        void unknownGitEmail_isAcceptedOnTheRetryOnceTheUserRegistersIt() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            Machine machine = registerMachine(tenant, ada, "laptop");
            String email = "ada.git@" + tenant.domain();
            String session = sessionId();
            String remote = remote();
            Map<String, Object> batch = batch(machine, session, remote, events(1, 1));
            section(batch, "summary").put("gitAuthorEmail", email);
            send(machine.token(), batch).andExpect(status().isForbidden());

            addGitEmail(tenant, ada.userId(), email);
            send(machine.token(), batch).andExpect(status().isOk());

            assertEquals(1L, sessionCount(session));
            assertEquals(1L, eventCount(session));
            assertNull(conflictUserOf(session));
        }

        @Test
        void registeredGitEmail_isAcceptedAnyCaseAndStoresNoRecorderEmailOrAudit() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            Machine machine = registerMachine(tenant, ada, "laptop");
            String email = "ada.git@" + tenant.domain();
            addGitEmail(tenant, ada.userId(), email);
            String session = sessionId();

            send(machine.token(), withIdentity(machine, session, email.toUpperCase(), null)).andExpect(status().isOk());

            assertNull(conflictUserOf(session));
            assertEquals(1L, gitEmailRows(tenant, email));
            assertEquals(0L, count("SELECT count(*) FROM kaya.user_git_emails WHERE organization_id = ?::uuid AND source::text = 'RECORDER'",
                    tenant.id()));
            assertEquals(0L, auditEntries(ada.userId(), "EMAIL_ADDED"));
        }

        @Test
        void gitEmailOfTheTokensOwnUser_isNotAConflict() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            Machine machine = registerMachine(tenant, ada, "laptop");
            String session = sessionId();

            send(machine.token(), withIdentity(machine, session, ada.email(), null)).andExpect(status().isOk());

            assertNull(conflictUserOf(session));
            assertEquals(0L, gitEmailRows(tenant, ada.email().toLowerCase()), "the sign-in email attributes without a git email row");
        }

        @Test
        void gitEmailOfAnotherUser_marksTheConflictStoresTheBatchAndMovesNothing() throws Exception {
            Tenant tenant = newTenant();
            Machine machine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            Person bo = signIn(tenant, "bo");
            String session = sessionId();

            send(machine.token(), withIdentity(machine, session, bo.email(), null))
                    .andExpect(status().isOk());

            assertEquals(bo.userId(), conflictUserOf(session));
            assertEquals(1L, eventCount(session));
            assertEquals(machine.owner().userId(), jdbcTemplate.queryForObject(
                    "SELECT user_id::text FROM kaya.sessions WHERE session_id = ?", String.class, session));
        }

        @Test
        void conflictStaysOnTheSessionWhenALaterBatchBringsNoEmail() throws Exception {
            Tenant tenant = newTenant();
            Machine machine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            Person bo = signIn(tenant, "bo");
            String session = sessionId();
            String remote = remote();
            Map<String, Object> first = batch(machine, session, remote, events(1, 1));
            section(first, "summary").put("gitAuthorEmail", bo.email());
            send(machine.token(), first).andExpect(status().isOk());

            send(machine.token(), batch(machine, session, remote, events(2, 2))).andExpect(status().isOk());

            assertEquals(bo.userId(), conflictUserOf(session));
        }

        @Test
        void batchWithoutGitEmail_isAcceptedForTheTokensUser() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            Machine machine = registerMachine(tenant, ada, "laptop");
            String session = sessionId();

            send(machine.token(), withIdentity(machine, session, null, null)).andExpect(status().isOk());

            assertNull(conflictUserOf(session));
            assertEquals(ada.userId(), jdbcTemplate.queryForObject(
                    "SELECT user_id::text FROM kaya.sessions WHERE session_id = ?", String.class, session));
        }

        @Test
        void gitEmailWithoutAnAtSign_isIgnored() throws Exception {
            Tenant tenant = newTenant();
            Machine machine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");

            send(machine.token(), withIdentity(machine, sessionId(), "root", null)).andExpect(status().isOk());

            assertEquals(0L, gitEmailRows(tenant, "root"));
        }

        @Test
        void gitEmailOver320Characters_isIgnored() throws Exception {
            Tenant tenant = newTenant();
            Machine machine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            String session = sessionId();

            send(machine.token(), withIdentity(machine, session, "a".repeat(320) + "@" + tenant.domain(), null))
                    .andExpect(status().isOk());

            assertEquals(1L, sessionCount(session));
        }

        @Test
        void gitEmailOnTheSession_isKeptFromTheFirstBatchThatHadOne() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            Machine machine = registerMachine(tenant, ada, "laptop");
            addGitEmail(tenant, ada.userId(), "first@" + tenant.domain());
            addGitEmail(tenant, ada.userId(), "second@" + tenant.domain());
            String session = sessionId();
            String remote = remote();
            send(machine.token(), batch(machine, session, remote, events(1, 1))).andExpect(status().isOk());
            Map<String, Object> second = batch(machine, session, remote, events(2, 2));
            section(second, "summary").put("gitAuthorEmail", "first@" + tenant.domain());
            send(machine.token(), second).andExpect(status().isOk());
            Map<String, Object> third = batch(machine, session, remote, events(3, 3));
            section(third, "summary").put("gitAuthorEmail", "second@" + tenant.domain());
            send(machine.token(), third).andExpect(status().isOk());

            assertEquals("first@" + tenant.domain(),
                    jdbcTemplate.queryForObject("SELECT git_author_email FROM kaya.sessions WHERE session_id = ?", String.class, session));
        }
    }

    @Nested
    class Account {

        @Test
        void unknownAccount_isAddedToTheTokensUserWithOneRecorderAuditEntry() throws Exception {
            Tenant tenant = newTenant();
            Machine machine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            String email = "ada.claude@" + tenant.domain();

            send(machine.token(), withIdentity(machine, sessionId(), null, account(email))).andExpect(status().isOk());

            Map<String, Object> row = jdbcTemplate.queryForMap("""
                    SELECT l.user_id::text AS user_id, a.provider::text AS provider, a.account_organization_uuid,
                           a.account_organization_name, a.billing_type, a.seat_tier, l.source::text AS source
                    FROM kaya.accounts a JOIN kaya.account_users l ON l.account_id = a.id
                    WHERE a.organization_id = ?::uuid AND a.account_email = ?
                    """, tenant.id(), email);
            assertEquals(machine.owner().userId(), row.get("user_id"));
            assertEquals("CLAUDE", row.get("provider"));
            assertEquals(CLAUDE_ORGANIZATION_UUID, row.get("account_organization_uuid"));
            assertEquals("Korur", row.get("account_organization_name"));
            assertEquals("team_premium", row.get("seat_tier"));
            assertEquals("RECORDER", row.get("source"));
            assertEquals(1L, auditEntries(machine.owner().userId(), "ACCOUNT_ADDED"));
            assertEquals(machine.machineId(), jdbcTemplate.queryForObject("""
                    SELECT details ->> 'machineId' FROM kaya.user_audit_entries
                    WHERE user_id = ?::uuid AND action::text = 'ACCOUNT_ADDED'
                    """, String.class, machine.owner().userId()));
        }

        @Test
        void accountEmail_isStoredLowerCased() throws Exception {
            Tenant tenant = newTenant();
            Machine machine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");

            send(machine.token(), withIdentity(machine, sessionId(), null, account("Ada.Claude@" + tenant.domain())))
                    .andExpect(status().isOk());

            assertEquals(1L, count("SELECT count(*) FROM kaya.accounts WHERE organization_id = ?::uuid AND account_email = ?",
                    tenant.id(), "ada.claude@" + tenant.domain()));
        }

        @Test
        void sameAccountAgain_addsNoSecondRowOrEntryAndRefreshesNothingWhenUnchanged() throws Exception {
            Tenant tenant = newTenant();
            Machine machine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            String email = "ada.claude@" + tenant.domain();

            send(machine.token(), withIdentity(machine, sessionId(), null, account(email))).andExpect(status().isOk());
            send(machine.token(), withIdentity(machine, sessionId(), null, account(email))).andExpect(status().isOk());

            assertEquals(1L, count("SELECT count(*) FROM kaya.accounts WHERE organization_id = ?::uuid AND account_email = ?",
                    tenant.id(), email));
            assertEquals(1L, auditEntries(machine.owner().userId(), "ACCOUNT_ADDED"));
        }

        @Test
        void accountOfTheTokensUserWithANewSeat_isRefreshed() throws Exception {
            Tenant tenant = newTenant();
            Machine machine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            String email = "ada.claude@" + tenant.domain();
            send(machine.token(), withIdentity(machine, sessionId(), null, account(email))).andExpect(status().isOk());
            Map<String, Object> upgraded = new LinkedHashMap<>(account(email));
            upgraded.put("seatTier", "max_20x");

            send(machine.token(), withIdentity(machine, sessionId(), null, upgraded)).andExpect(status().isOk());

            assertEquals("max_20x", jdbcTemplate.queryForObject(
                    "SELECT seat_tier FROM kaya.accounts WHERE organization_id = ?::uuid AND account_email = ?",
                    String.class, tenant.id(), email));
            assertEquals(1L, auditEntries(machine.owner().userId(), "ACCOUNT_ADDED"));
        }

        @Test
        void accountOfAnotherUser_linksTheAccountToTheTokensUserToo_withoutConflict() throws Exception {
            Tenant tenant = newTenant();
            Machine machine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            Person bo = signIn(tenant, "bo");
            String email = "bo.claude@" + tenant.domain();
            String accountId = insertAccount(tenant, bo.userId(), "CLAUDE", email, CLAUDE_ORGANIZATION_UUID, null);
            String session = sessionId();

            send(machine.token(), withIdentity(machine, session, null, account(email))).andExpect(status().isOk());

            assertNull(conflictUserOf(session));
            assertEquals(1L, count("SELECT count(*) FROM kaya.accounts WHERE organization_id = ?::uuid AND account_email = ?",
                    tenant.id(), email));
            assertEquals(2L, count("SELECT count(*) FROM kaya.account_users WHERE account_id = ?::uuid", accountId));
            assertEquals(1L, count("""
                    SELECT count(*) FROM kaya.account_users
                    WHERE account_id = ?::uuid AND user_id = ?::uuid AND source = 'RECORDER'
                    """, accountId, machine.owner().userId()));
            assertEquals(1L, auditEntries(machine.owner().userId(), "ACCOUNT_ADDED"));
            assertEquals(0L, auditEntries(bo.userId(), "ACCOUNT_ADDED"));
            assertEquals(accountId, jdbcTemplate.queryForObject("SELECT account_id::text FROM kaya.machines WHERE id = ?::uuid",
                    String.class, machine.machineId()));
        }

        @Test
        void twoUsersReportingTheSameAccount_shareOneAccountWithTwoLinks() throws Exception {
            Tenant tenant = newTenant();
            Machine adaMachine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            Machine boMachine = registerMachine(tenant, signIn(tenant, "bo"), "desktop");
            String email = "team.claude@" + tenant.domain();

            send(adaMachine.token(), withIdentity(adaMachine, sessionId(), null, account(email))).andExpect(status().isOk());
            send(boMachine.token(), withIdentity(boMachine, sessionId(), null, account(email))).andExpect(status().isOk());

            assertEquals(1L, count("SELECT count(*) FROM kaya.accounts WHERE organization_id = ?::uuid AND account_email = ?",
                    tenant.id(), email));
            assertEquals(2L, count("""
                    SELECT count(*) FROM kaya.account_users l JOIN kaya.accounts a ON a.id = l.account_id
                    WHERE a.organization_id = ?::uuid AND a.account_email = ?
                    """, tenant.id(), email));
            assertEquals(1L, auditEntries(adaMachine.owner().userId(), "ACCOUNT_ADDED"));
            assertEquals(1L, auditEntries(boMachine.owner().userId(), "ACCOUNT_ADDED"));
        }

        @Test
        void sameEmailInAnotherProviderOrganization_isAnotherAccount() throws Exception {
            Tenant tenant = newTenant();
            Machine machine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            String email = "ada.claude@" + tenant.domain();
            send(machine.token(), withIdentity(machine, sessionId(), null, account(email))).andExpect(status().isOk());
            Map<String, Object> other = new LinkedHashMap<>(account(email));
            other.put("organizationUuid", "another-organization-uuid");

            send(machine.token(), withIdentity(machine, sessionId(), null, other)).andExpect(status().isOk());

            assertEquals(2L, count("SELECT count(*) FROM kaya.accounts WHERE organization_id = ?::uuid AND account_email = ?",
                    tenant.id(), email));
        }

        @Test
        void gitEmailOfOneUserAndAccountOfAnother_namesTheHolderOfTheGitEmail() throws Exception {
            Tenant tenant = newTenant();
            Machine machine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            Person gitHolder = signIn(tenant, "bo");
            Person accountHolder = signIn(tenant, "cy");
            String accountEmail = "cy.claude@" + tenant.domain();
            insertAccount(tenant, accountHolder.userId(), "CLAUDE", accountEmail, CLAUDE_ORGANIZATION_UUID, null);
            String session = sessionId();

            send(machine.token(), withIdentity(machine, session, gitHolder.email(), account(accountEmail)))
                    .andExpect(status().isOk());

            assertEquals(gitHolder.userId(), conflictUserOf(session));
        }

        @Test
        void accountFieldsOnTheSession_areCopiedWhileItHasNoneAndNeverOverwritten() throws Exception {
            Tenant tenant = newTenant();
            Machine machine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            String session = sessionId();
            String remote = remote();
            send(machine.token(), batch(machine, session, remote, events(1, 1))).andExpect(status().isOk());
            assertNull(jdbcTemplate.queryForMap("SELECT account_email FROM kaya.sessions WHERE session_id = ?", session).get("account_email"));

            Map<String, Object> second = batch(machine, session, remote, events(2, 2));
            section(second, "machine").put("account", account("first.claude@" + tenant.domain()));
            send(machine.token(), second).andExpect(status().isOk());
            Map<String, Object> third = batch(machine, session, remote, events(3, 3));
            Map<String, Object> other = new LinkedHashMap<>(account("second.claude@" + tenant.domain()));
            other.put("seatTier", "max_20x");
            section(third, "machine").put("account", other);
            send(machine.token(), third).andExpect(status().isOk());

            Map<String, Object> sessionRow = jdbcTemplate.queryForMap(
                    "SELECT account_email, seat_tier, billing_type FROM kaya.sessions WHERE session_id = ?", session);
            assertEquals("first.claude@" + tenant.domain(), sessionRow.get("account_email"));
            assertEquals("team_premium", sessionRow.get("seat_tier"));
        }

        @Test
        void accountWithoutAnEmail_learnsNothing() throws Exception {
            Tenant tenant = newTenant();
            Machine machine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");

            send(machine.token(), withIdentity(machine, sessionId(), null, Map.of("organizationName", "Korur", "seatTier", "pro")))
                    .andExpect(status().isOk());

            assertEquals(0L, count("SELECT count(*) FROM kaya.accounts WHERE organization_id = ?::uuid", tenant.id()));
        }
    }

    @Nested
    class MachineBookkeeping {

        @Test
        void batch_recordsTheClaudeCodeVersionTheAccountAndWhenTheMachineWasSeen() throws Exception {
            Tenant tenant = newTenant();
            Machine machine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            Map<String, Object> batch = batch(machine, sessionId(), remote(), events(1, 1));
            section(batch, "machine").put("claudeCodeVersion", "2.3.1");
            section(batch, "machine").put("account", account("ada.claude@" + tenant.domain()));

            send(machine.token(), batch).andExpect(status().isOk());

            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT claude_code_version, account_id, last_seen_at FROM kaya.machines WHERE id = ?::uuid", machine.machineId());
            assertEquals("2.3.1", row.get("claude_code_version"));
            assertNotNull(row.get("account_id"));
            assertNotNull(row.get("last_seen_at"));
        }

        @Test
        void batchWithinAMinute_withTheSameVersion_doesNotMoveLastSeenAt() throws Exception {
            Tenant tenant = newTenant();
            Machine machine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            Map<String, Object> batch = batch(machine, sessionId(), remote(), events(1, 1));
            section(batch, "machine").put("claudeCodeVersion", "2.3.1");
            send(machine.token(), batch).andExpect(status().isOk());
            Timestamp seen = lastSeenAt(machine);
            assertNotNull(seen);

            send(machine.token(), batch).andExpect(status().isOk());

            assertEquals(seen, lastSeenAt(machine));
        }

        @Test
        void batchWithANewVersion_storesItAndMovesLastSeenAt() throws Exception {
            Tenant tenant = newTenant();
            Machine machine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            Map<String, Object> first = batch(machine, sessionId(), remote(), events(1, 1));
            section(first, "machine").put("claudeCodeVersion", "2.3.1");
            send(machine.token(), first).andExpect(status().isOk());
            Timestamp seen = lastSeenAt(machine);

            Map<String, Object> second = batch(machine, sessionId(), remote(), events(1, 1));
            section(second, "machine").put("claudeCodeVersion", "2.4.0");
            send(machine.token(), second).andExpect(status().isOk());

            assertEquals("2.4.0", jdbcTemplate.queryForObject("SELECT claude_code_version FROM kaya.machines WHERE id = ?::uuid",
                    String.class, machine.machineId()));
            assertTrue(lastSeenAt(machine).after(seen));
        }

        private Timestamp lastSeenAt(Machine machine) {
            return jdbcTemplate.queryForObject("SELECT last_seen_at FROM kaya.machines WHERE id = ?::uuid", Timestamp.class,
                    machine.machineId());
        }
    }
}
