package com.korur.oba.apps.kaya.session.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

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
 * Every state and deliverer the recorder can report for a session's config release, and the values it
 * should not send: each known state is stored as sent, a sent UNVERIFIED never names a release,
 * and a report that cannot be checked degrades to UNVERIFIED without refusing the batch.
 */
class SessionReleaseStatesIT extends AbstractIngestIT {

    private static final String MANIFEST_SHA = "d".repeat(64);

    private Tenant tenant;
    private Person ada;
    private Machine machine;
    private String session;
    private String remote;
    private UUID releaseId;
    private long nextSeq = 2;

    @BeforeEach
    void setUp() throws Exception {
        tenant = newTenant();
        ada = signIn(tenant, "ada");
        machine = registerMachine(tenant, ada, "m-" + uniqueSuffix());
        session = sessionId();
        remote = remote();
        send(machine.token(), batch(machine, session, remote, events(1, 1))).andExpect(status().isOk());
        UUID repositoryId = jdbcTemplate.queryForObject("SELECT repository_id FROM kaya.sessions WHERE session_id = ?",
                UUID.class, session);
        UUID set = jdbcTemplate.queryForObject("""
                INSERT INTO kaya.packs (organization_id, name) VALUES (?, ?) RETURNING id
                """, UUID.class, tenant.organization().getId(), "pack-" + uniqueSuffix());
        UUID version = jdbcTemplate.queryForObject("""
                INSERT INTO kaya.versions (organization_id, pack_id, number, status, proposed_by, source)
                VALUES (?, ?, 1, 'APPROVED'::version_version_status_enum, ?, 'SEED'::version_version_source_enum)
                RETURNING id
                """, UUID.class, tenant.organization().getId(), set, ada.user().getId());
        releaseId = jdbcTemplate.queryForObject("""
                INSERT INTO kaya.releases (repository_id, number, cause_version_id, manifest, manifest_sha256, signature, key_id)
                VALUES (?, 4, ?, '[]'::jsonb, ?, 'c2ln', 'test-key') RETURNING id
                """, UUID.class, repositoryId, version, MANIFEST_SHA);
    }

    private void report(Integer number, String sha, String state, String deliveredBy) throws Exception {
        Map<String, Object> configRelease = new LinkedHashMap<>();
        configRelease.put("number", number);
        configRelease.put("manifestSha256", sha);
        configRelease.put("state", state);
        configRelease.put("deliveredBy", deliveredBy);
        long seq = nextSeq++;
        Map<String, Object> batch = batch(machine, session, remote, events(seq, seq));
        section(batch, "summary").put("configRelease", configRelease);
        send(machine.token(), batch).andExpect(status().isOk());
    }

    private Map<String, Object> sessionRowOf() {
        return jdbcTemplate.queryForMap("""
                SELECT release_id, release_state::text AS state, delivered_by::text AS delivered_by
                FROM kaya.sessions WHERE session_id = ?
                """, session);
    }

    @ParameterizedTest
    @CsvSource({"FRESH,LAUNCHER", "FRESH,HOOK", "CACHED,LAUNCHER", "CACHED,HOOK", "STALE,LAUNCHER", "STALE,HOOK"})
    void everyDeliveredState_isStoredAsSentWithTheRelease(String state, String deliveredBy) throws Exception {
        report(4, MANIFEST_SHA, state, deliveredBy);

        Map<String, Object> row = sessionRowOf();
        assertEquals(releaseId, row.get("release_id"));
        assertEquals(state, row.get("state"));
        assertEquals(deliveredBy, row.get("delivered_by"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"LAUNCHER", "HOOK"})
    void none_dropsTheReleaseEvenWhenANumberIsSent(String deliveredBy) throws Exception {
        report(4, MANIFEST_SHA, "NONE", deliveredBy);

        Map<String, Object> row = sessionRowOf();
        assertNull(row.get("release_id"));
        assertEquals("NONE", row.get("state"));
        assertEquals(deliveredBy, row.get("delivered_by"));
    }

    @Test
    void sentUnverified_neverNamesARelease() throws Exception {
        report(4, MANIFEST_SHA, "UNVERIFIED", "HOOK");

        Map<String, Object> row = sessionRowOf();
        assertNull(row.get("release_id"));
        assertEquals("UNVERIFIED", row.get("state"));
        assertEquals("HOOK", row.get("delivered_by"));
    }

    @ParameterizedTest
    @CsvSource(value = {"0,", "-1,", "4,NULL_HASH"})
    void numberOrHashThatCannotBeChecked_isUnverified(Integer number, String marker) throws Exception {
        report(number, marker == null ? MANIFEST_SHA : null, "FRESH", "LAUNCHER");

        Map<String, Object> row = sessionRowOf();
        assertNull(row.get("release_id"));
        assertEquals("UNVERIFIED", row.get("state"));
    }

    @Test
    void hashWithSurroundingWhitespace_stillMatches() throws Exception {
        report(4, "  " + MANIFEST_SHA.toUpperCase() + "\n", "CACHED", "LAUNCHER");

        assertEquals(releaseId, sessionRowOf().get("release_id"));
    }

    @Test
    void numberWithoutAnyHash_isUnverified() throws Exception {
        report(4, null, "FRESH", "LAUNCHER");

        assertNull(sessionRowOf().get("release_id"));
        assertEquals("UNVERIFIED", sessionRowOf().get("state"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "OUTDATED", "UNVERIFIED2"})
    void unknownState_isUnverifiedAndKeepsTheBatch(String state) throws Exception {
        report(4, MANIFEST_SHA, state, "LAUNCHER");

        Map<String, Object> row = sessionRowOf();
        assertNull(row.get("release_id"));
        assertEquals("UNVERIFIED", row.get("state"));
        assertEquals(2L, eventCount(session));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLI", ""})
    void unknownDeliveredBy_isStoredUnsetAndTheStateStays(String deliveredBy) throws Exception {
        report(4, MANIFEST_SHA, "STALE", deliveredBy);

        Map<String, Object> row = sessionRowOf();
        assertEquals(releaseId, row.get("release_id"));
        assertEquals("STALE", row.get("state"));
        assertNull(row.get("delivered_by"));
        assertEquals(2L, eventCount(session));
    }

    @Test
    void aReleaseThatIsLaterDeleted_leavesTheSessionWithItsStateButNoRelease() throws Exception {
        report(4, MANIFEST_SHA, "FRESH", "LAUNCHER");

        jdbcTemplate.update("DELETE FROM kaya.releases WHERE id = ?", releaseId);

        Map<String, Object> row = sessionRowOf();
        assertNull(row.get("release_id"));
        assertEquals("FRESH", row.get("state"));
    }

    @Test
    void freshAfterStale_replacesStateAndDeliverer() throws Exception {
        report(4, MANIFEST_SHA, "STALE", "HOOK");
        report(4, MANIFEST_SHA, "FRESH", "LAUNCHER");

        Map<String, Object> row = sessionRowOf();
        assertEquals("FRESH", row.get("state"));
        assertEquals("LAUNCHER", row.get("delivered_by"));
    }

    @Test
    void sessionDetail_showsEveryReportedField() throws Exception {
        report(4, MANIFEST_SHA, "CACHED", "HOOK");

        mockMvc.perform(in(tenant, get("/kaya/sessions/" + session), ada.jwt(), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.releaseId").value(releaseId.toString()))
                .andExpect(jsonPath("$.data.releaseNumber").value(4))
                .andExpect(jsonPath("$.data.releaseState").value("CACHED"))
                .andExpect(jsonPath("$.data.deliveredBy").value("HOOK"));
    }
}
