package com.korur.oba.apps.kaya.session.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractIngestIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The release a session started on, as the recorder reports it. A report counts only when its
 * number and manifest hash name a release of the session's own repository; anything else is kept as
 * UNVERIFIED so the session still records, and a batch without a report leaves the last one in place.
 */
class SessionReleaseIT extends AbstractIngestIT {

    private static final String MANIFEST_SHA = "a".repeat(64);
    private static final String SIGNATURE = "c2lnbmF0dXJl";

    private Tenant tenant;
    private Person ada;
    private Machine machine;
    private String session;
    private String remote;
    private UUID repositoryId;
    private UUID releaseId;

    @BeforeEach
    void setUp() throws Exception {
        tenant = newTenant();
        ada = signIn(tenant, "ada");
        machine = registerMachine(tenant, ada, "m-" + uniqueSuffix());
        session = sessionId();
        remote = remote();
        send(machine.token(), batch(machine, session, remote, events(1, 1))).andExpect(status().isOk());
        repositoryId = jdbcTemplate.queryForObject("SELECT repository_id FROM kaya.sessions WHERE session_id = ?",
                UUID.class, session);
        releaseId = insertRelease(repositoryId, 3, MANIFEST_SHA);
    }

    private UUID insertRelease(UUID repository, int number, String manifestSha256) {
        UUID set = jdbcTemplate.queryForObject("""
                INSERT INTO kaya.packs (organization_id, name) VALUES (?, ?) RETURNING id
                """, UUID.class, tenant.organization().getId(), "pack-" + uniqueSuffix());
        UUID version = jdbcTemplate.queryForObject("""
                INSERT INTO kaya.versions (organization_id, pack_id, number, status, proposed_by, source)
                VALUES (?, ?, 1, 'APPROVED'::version_version_status_enum, ?, 'SEED'::version_version_source_enum)
                RETURNING id
                """, UUID.class, tenant.organization().getId(), set, ada.user().getId());
        return jdbcTemplate.queryForObject("""
                INSERT INTO kaya.releases (repository_id, number, cause_version_id, manifest, manifest_sha256, signature, key_id)
                VALUES (?, ?, ?, '[]'::jsonb, ?, ?, 'test-key') RETURNING id
                """, UUID.class, repository, number, version, manifestSha256, SIGNATURE);
    }

    private void report(Map<String, Object> configRelease) throws Exception {
        Map<String, Object> batch = batch(machine, session, remote, events(2, 2));
        if (configRelease != null) {
            section(batch, "summary").put("configRelease", configRelease);
        }
        send(machine.token(), batch).andExpect(status().isOk());
    }

    private static Map<String, Object> configRelease(Integer number, String sha, String state, String deliveredBy) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("number", number);
        report.put("manifestSha256", sha);
        report.put("state", state);
        report.put("deliveredBy", deliveredBy);
        return report;
    }

    private Map<String, Object> sessionRowOf() {
        return jdbcTemplate.queryForMap("""
                SELECT release_id, release_state::text AS state, delivered_by::text AS delivered_by
                FROM kaya.sessions WHERE session_id = ?
                """, session);
    }

    @Test
    void knownRelease_isRecordedAndShownOnTheSession() throws Exception {
        report(configRelease(3, MANIFEST_SHA.toUpperCase(), "FRESH", "LAUNCHER"));

        Map<String, Object> row = sessionRowOf();
        assertEquals(releaseId, row.get("release_id"));
        assertEquals("FRESH", row.get("state"));
        assertEquals("LAUNCHER", row.get("delivered_by"));
        mockMvc.perform(in(tenant, get("/kaya/sessions/" + session), ada.jwt(), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.releaseId").value(releaseId.toString()))
                .andExpect(jsonPath("$.data.releaseNumber").value(3))
                .andExpect(jsonPath("$.data.releaseState").value("FRESH"))
                .andExpect(jsonPath("$.data.deliveredBy").value("LAUNCHER"));
    }

    @Test
    void staleFromTheHook_keepsTheReleaseWithItsState() throws Exception {
        report(configRelease(3, MANIFEST_SHA, "STALE", "HOOK"));

        Map<String, Object> row = sessionRowOf();
        assertEquals(releaseId, row.get("release_id"));
        assertEquals("STALE", row.get("state"));
        assertEquals("HOOK", row.get("delivered_by"));
    }

    @Test
    void unknownNumber_isUnverifiedWithoutRelease() throws Exception {
        report(configRelease(9, MANIFEST_SHA, "FRESH", "LAUNCHER"));

        assertUnverified();
    }

    @Test
    void hashOfAnotherManifest_isUnverifiedWithoutRelease() throws Exception {
        report(configRelease(3, "b".repeat(64), "CACHED", "LAUNCHER"));

        assertUnverified();
    }

    @Test
    void releaseOfAnotherRepository_isUnverifiedWithoutRelease() throws Exception {
        String otherSession = sessionId();
        send(machine.token(), batch(machine, otherSession, remote(), events(1, 1))).andExpect(status().isOk());
        UUID otherRepository = jdbcTemplate.queryForObject("SELECT repository_id FROM kaya.sessions WHERE session_id = ?",
                UUID.class, otherSession);
        String otherSha = "c".repeat(64);
        insertRelease(otherRepository, 5, otherSha);

        report(configRelease(5, otherSha, "FRESH", "LAUNCHER"));

        assertUnverified();
    }

    @Test
    void missingState_isUnverified() throws Exception {
        report(configRelease(3, MANIFEST_SHA, null, "LAUNCHER"));

        assertUnverified();
    }

    @Test
    void none_recordsNoRelease() throws Exception {
        report(configRelease(null, null, "NONE", "LAUNCHER"));

        Map<String, Object> row = sessionRowOf();
        assertNull(row.get("release_id"));
        assertEquals("NONE", row.get("state"));
        mockMvc.perform(in(tenant, get("/kaya/sessions/" + session), ada.jwt(), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.releaseNumber").isEmpty())
                .andExpect(jsonPath("$.data.releaseState").value("NONE"));
    }

    @Test
    void batchWithoutReport_keepsTheLastReport() throws Exception {
        report(configRelease(3, MANIFEST_SHA, "FRESH", "LAUNCHER"));

        report(null);

        assertEquals(releaseId, sessionRowOf().get("release_id"));
        assertEquals("FRESH", sessionRowOf().get("state"));
    }

    @Test
    void laterReport_replacesTheEarlierOne() throws Exception {
        report(configRelease(3, MANIFEST_SHA, "FRESH", "LAUNCHER"));

        report(configRelease(3, MANIFEST_SHA, "STALE", "HOOK"));

        assertEquals("STALE", sessionRowOf().get("state"));
        assertEquals("HOOK", sessionRowOf().get("delivered_by"));
    }

    @Test
    void unknownState_isUnverifiedAndTheBatchIsStored() throws Exception {
        report(configRelease(3, MANIFEST_SHA, "WARM", "LAUNCHER"));

        assertUnverified();
        assertEquals(2L, eventCount(session));
    }

    @Test
    void unknownDeliveredBy_leavesItUnsetAndKeepsTheRelease() throws Exception {
        report(configRelease(3, MANIFEST_SHA, "FRESH", "DAEMON"));

        Map<String, Object> row = sessionRowOf();
        assertEquals(releaseId, row.get("release_id"));
        assertEquals("FRESH", row.get("state"));
        assertNull(row.get("delivered_by"));
        assertEquals(2L, eventCount(session));
    }

    private void assertUnverified() {
        Map<String, Object> row = sessionRowOf();
        assertNull(row.get("release_id"));
        assertEquals("UNVERIFIED", row.get("state"));
        assertEquals("LAUNCHER", row.get("delivered_by"));
    }
}
