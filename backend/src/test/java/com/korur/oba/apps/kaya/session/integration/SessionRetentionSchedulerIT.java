package com.korur.oba.apps.kaya.session.integration;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// 4. Internal
import com.korur.oba.apps.kaya.session.scheduler.SessionRetentionScheduler;
import com.korur.oba.apps.kaya.support.AbstractSessionIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The daily cleanup: a session is deleted once its last event is older than its own organization's
 * retention, whatever the retention of the other organizations. The scheduler runs over the
 * database shared by all tests, so every session here is dated relative to now and the other tests'
 * sessions (recent, in organizations with the default 180 days) are never old enough to be touched.
 */
class SessionRetentionSchedulerIT extends AbstractSessionIT {

    @Autowired
    private SessionRetentionScheduler scheduler;

    private void setRetention(Tenant tenant, int days) {
        jdbcTemplate.update("INSERT INTO kaya.organization_settings (organization_id, retention_days) VALUES (?::uuid, ?) "
                + "ON CONFLICT (organization_id) DO UPDATE SET retention_days = EXCLUDED.retention_days", tenant.id(), days);
    }

    private void setAge(String session, int days) {
        jdbcTemplate.update("UPDATE kaya.sessions SET last_event_at = now() - make_interval(days => ?) WHERE session_id = ?",
                days, session);
    }

    private long childRows(String sessionRowId) {
        long rows = 0;
        for (String table : new String[] {"session_events", "session_links", "session_handoff_snapshots"}) {
            rows += count("SELECT count(*) FROM kaya." + table + " WHERE session_id = ?::uuid", sessionRowId);
        }
        return rows;
    }

    @Test
    void run_deletesOnlySessionsPastTheOrganizationsRetention() throws Exception {
        Tenant tenant = newTenant();
        Person ada = signIn(tenant, "ada");
        Machine laptop = registerMachine(tenant, ada, "laptop");
        setRetention(tenant, 30);
        String remote = remote();
        String expired = ingestSession(laptop, remote, 2);
        String justInside = ingestSession(laptop, remote, 2);
        String fresh = ingestSession(laptop, remote, 2);
        setAge(expired, 31);
        setAge(justInside, 29);

        scheduler.deleteExpiredSessions();

        assertEquals(0L, sessionCount(expired));
        assertEquals(1L, sessionCount(justInside));
        assertEquals(1L, sessionCount(fresh));
    }

    @Test
    void run_neverDeletesAnotherOrganizationsSession() throws Exception {
        Tenant strict = newTenant();
        Tenant lenient = newTenant();
        Person ada = signIn(strict, "ada");
        Person bob = signIn(lenient, "bob");
        setRetention(strict, 30);
        setRetention(lenient, 400);
        String strictSession = ingestSession(registerMachine(strict, ada, "laptop"), remote(), 2);
        String lenientSession = ingestSession(registerMachine(lenient, bob, "laptop"), remote(), 2);
        setAge(strictSession, 100);
        setAge(lenientSession, 100);

        scheduler.deleteExpiredSessions();

        assertEquals(0L, sessionCount(strictSession));
        assertEquals(1L, sessionCount(lenientSession));
        mockMvc.perform(in(lenient, get("/kaya/sessions/" + lenientSession), bob.jwt(), null)).andExpect(status().isOk());
    }

    @Test
    void run_countsFromTheLastEventNotFromTheStart() throws Exception {
        Tenant tenant = newTenant();
        Person ada = signIn(tenant, "ada");
        Machine laptop = registerMachine(tenant, ada, "laptop");
        setRetention(tenant, 30);
        String session = ingestSession(laptop, remote(), 2);
        jdbcTemplate.update("UPDATE kaya.sessions SET started_at = now() - interval '90 days', last_event_at = now() - interval '1 day' "
                + "WHERE session_id = ?", session);

        scheduler.deleteExpiredSessions();

        assertEquals(1L, sessionCount(session));
    }

    @Test
    void run_removesTheSessionsEventsLinksAndHandoffsWithIt() throws Exception {
        Tenant tenant = newTenant();
        Person ada = signIn(tenant, "ada");
        Machine laptop = registerMachine(tenant, ada, "laptop");
        setRetention(tenant, 30);
        String session = ingestSession(laptop, remote(), 4, batch -> {
            section(batch, "summary").put("ticket", "KAYA-7");
            withHandoffs(batch, handoff("f/planning.md", "# P\n\n**Status:** DONE\n", "2026-10-20T09:10:00Z"));
        });
        String sessionRowId = jdbcTemplate.queryForObject("SELECT id::text FROM kaya.sessions WHERE session_id = ?", String.class, session);
        assertEquals(true, childRows(sessionRowId) >= 6);
        setAge(session, 45);

        scheduler.deleteExpiredSessions();

        assertEquals(0L, childRows(sessionRowId));
    }

    @Test
    void run_keepsTheUsersAuditEntriesAndTheUser() throws Exception {
        Tenant tenant = newTenant();
        Person ada = signIn(tenant, "ada");
        Machine laptop = registerMachine(tenant, ada, "laptop");
        setRetention(tenant, 30);
        String session = ingestSession(laptop, remote(), 2);
        setAge(session, 45);
        long audits = count("SELECT count(*) FROM kaya.user_audit_entries WHERE user_id = ?::uuid", ada.userId());

        scheduler.deleteExpiredSessions();

        assertEquals(0L, sessionCount(session));
        assertEquals(audits, count("SELECT count(*) FROM kaya.user_audit_entries WHERE user_id = ?::uuid", ada.userId()));
        assertEquals(1L, count("SELECT count(*) FROM shrine.users WHERE id = ?::uuid", ada.userId()));
        assertEquals(1L, count("SELECT count(*) FROM kaya.machines WHERE id = ?::uuid", laptop.machineId()));
    }

    @Test
    void run_withNothingExpired_deletesNothingOfTheOrganization() throws Exception {
        Tenant tenant = newTenant();
        Person ada = signIn(tenant, "ada");
        Machine laptop = registerMachine(tenant, ada, "laptop");
        String session = ingestSession(laptop, remote(), 2);

        scheduler.deleteExpiredSessions();

        assertEquals(1L, sessionCount(session));
    }

    @Test
    void run_judgesASessionByTheRetentionSetAtThatMoment() throws Exception {
        Tenant tenant = newTenant();
        Person ada = signIn(tenant, "ada");
        Machine laptop = registerMachine(tenant, ada, "laptop");
        String session = ingestSession(laptop, remote(), 2);
        setAge(session, 60);

        scheduler.deleteExpiredSessions();
        assertEquals(1L, sessionCount(session));

        setRetention(tenant, 30);
        scheduler.deleteExpiredSessions();
        assertEquals(0L, sessionCount(session));
    }
}
