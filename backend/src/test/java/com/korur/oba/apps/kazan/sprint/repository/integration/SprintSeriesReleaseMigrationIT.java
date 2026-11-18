package com.korur.oba.apps.kazan.sprint.repository.integration;

// 1. Framework-level
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// 3. Java SDK
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 2. Third-party
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

// 4. Internal
import com.korur.oba.support.ObaIntegrationTest;
import com.korur.oba.support.ScratchDatabases;

/**
 * Proves the repair of V1_2_108 on a scratch database migrated to 1.2.107: a
 * series left switched on without a running or planned sprint is switched off,
 * and a series that V1_2_95 switched off in its favour gets its boards back and
 * runs again, unless a series that is still on holds one of those boards.
 */
class SprintSeriesReleaseMigrationIT extends ObaIntegrationTest {

    private static final String BASELINE = "1.2.107";
    private static final String TARGET = "1.2.108";
    private static final String NO_LIVE_SPRINT = "no running or planned sprint left";

    private static ScratchDatabases databases;

    private String scratchName;
    private JdbcTemplate jdbc;
    private UUID organization;

    @BeforeAll
    static void createTemplate(@Autowired DataSourceProperties dataSource) throws SQLException {
        databases = ScratchDatabases.migratedTo(dataSource, BASELINE);
    }

    @AfterAll
    static void dropTemplate() throws SQLException {
        if (databases != null) {
            databases.close();
        }
    }

    @BeforeEach
    void createScratchDatabase() throws SQLException {
        scratchName = databases.createCopy();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                databases.urlOf(scratchName), databases.username(), databases.password()));
        organization = UUID.randomUUID();
        jdbc.update("INSERT INTO shrine.organizations (id, name, status, deleted, created_at) "
                + "VALUES (?, ?, 'ACTIVE', FALSE, CURRENT_TIMESTAMP)", organization, "org " + organization);
    }

    @AfterEach
    void dropScratchDatabase() throws SQLException {
        if (scratchName != null) {
            databases.drop(scratchName);
        }
    }

    private UUID board(String name) {
        UUID id = UUID.randomUUID();
        String suffix = id.toString().substring(0, 8);
        jdbc.update("INSERT INTO kaizen.boards (id, name, prefix, slug, organization_id) VALUES (?, ?, ?, ?, ?)",
                id, name, "B" + suffix, "board-" + suffix, organization);
        return id;
    }

    private UUID series(String name, boolean enabled, String lastRunMessage) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO kaizen.sprint_series
                    (id, name, next_round, enabled, length_days, next_creation_at, last_run_status, last_run_message)
                VALUES (?, ?, 3, ?, 14, ?::timestamptz, ?::kaizen_sprint_recurrence_run_status_enum, ?)
                """, id, name, enabled,
                enabled ? "2026-11-18T00:00:00Z" : null,
                lastRunMessage == null ? null : (enabled ? "OK" : "FAILED"),
                lastRunMessage);
        return id;
    }

    private UUID sprint(UUID series, int round, String status, String start, String end, UUID... boards) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO kaizen.sprints (id, name, series_id, round, status, start_date, end_date)
                VALUES (?, 'S', ?, ?, ?::kaizen_sprint_status_enum, ?::timestamptz, ?::timestamptz)
                """, id, series, round, status, start, end);
        for (UUID board : boards) {
            jdbc.update("INSERT INTO kaizen.board_sprints (id, board_id, sprint_id, slug) VALUES (?, ?, ?, ?)",
                    UUID.randomUUID(), board, id, "s-" + UUID.randomUUID().toString().substring(0, 8));
        }
        return id;
    }

    private void seriesBoard(UUID series, UUID board, int position) {
        jdbc.update("INSERT INTO kaizen.sprint_series_boards (id, series_id, board_id, position) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), series, board, position);
    }

    private List<UUID> boardsOf(UUID series) {
        return jdbc.queryForList(
                "SELECT board_id FROM kaizen.sprint_series_boards WHERE series_id = ? ORDER BY position",
                UUID.class, series);
    }

    private Map<String, Object> seriesRow(UUID id) {
        return jdbc.queryForMap("SELECT * FROM kaizen.sprint_series WHERE id = ?", id);
    }

    @Test
    void migrate_finishedSeriesHoldsTheBoardOfASwitchedOffLiveSeries_switchesTheFinishedOneOffAndRestoresTheLiveOne() {
        UUID ata = board("Ata");
        UUID business = board("Business");
        UUID finished = series("Sprint", true, null);
        sprint(finished, 2, "COMPLETED", "2026-10-21T00:00:00Z", "2026-11-04T07:35:00Z", business);
        seriesBoard(finished, business, 0);
        UUID live = series("Atasprint", false, "Switched off: board Business belongs to series Sprint");
        jdbc.update("UPDATE kaizen.sprint_series SET creation_mode = 'DAYS_BEFORE', create_days_before = 2 WHERE id = ?",
                live);
        sprint(live, 1, "ACTIVE", "2026-11-02T00:00:00Z", "2026-11-16T00:00:00Z", ata, business);
        seriesBoard(live, ata, 0);

        databases.migrate(scratchName, TARGET);

        Map<String, Object> off = seriesRow(finished);
        assertEquals(false, off.get("enabled"));
        assertNull(off.get("next_creation_at"));
        assertEquals("FAILED", off.get("last_run_status").toString());
        assertEquals(NO_LIVE_SPRINT, off.get("last_run_message"));
        assertEquals(List.of(), boardsOf(finished));

        Map<String, Object> restored = seriesRow(live);
        assertEquals(true, restored.get("enabled"));
        assertNull(restored.get("last_run_status"));
        assertNull(restored.get("last_run_message"));
        assertEquals(Instant.parse("2026-11-14T00:00:00Z"),
                ((Timestamp) restored.get("next_creation_at")).toInstant());
        assertEquals(List.of(ata, business), boardsOf(live));
    }

    @Test
    void migrate_enabledSeriesWithARunningSprint_isUnchanged() {
        UUID board = board("Platform");
        UUID running = series("Platform sprint", true, "keep me");
        sprint(running, 1, "COMPLETED", "2026-10-19T00:00:00Z", "2026-11-02T00:00:00Z", board);
        sprint(running, 2, "ACTIVE", "2026-11-02T00:00:00Z", "2026-11-16T00:00:00Z", board);
        seriesBoard(running, board, 0);

        databases.migrate(scratchName, TARGET);

        Map<String, Object> row = seriesRow(running);
        assertEquals(true, row.get("enabled"));
        assertEquals("keep me", row.get("last_run_message"));
        assertEquals(Instant.parse("2026-11-18T00:00:00Z"), ((Timestamp) row.get("next_creation_at")).toInstant());
        assertEquals(List.of(board), boardsOf(running));
    }

    @Test
    void migrate_boardHeldByASeriesThatStillRuns_leavesTheSwitchedOffSeriesOff() {
        UUID shared = board("Shared");
        UUID owner = series("Owner", true, null);
        sprint(owner, 1, "ACTIVE", "2026-11-02T00:00:00Z", "2026-11-16T00:00:00Z", shared);
        seriesBoard(owner, shared, 0);
        String message = "Switched off: board Shared belongs to series Owner";
        UUID loser = series("Loser", false, message);
        sprint(loser, 1, "PLANNED", "2026-11-16T00:00:00Z", "2026-11-30T00:00:00Z", shared);

        databases.migrate(scratchName, TARGET);

        assertEquals(true, seriesRow(owner).get("enabled"));
        assertEquals(List.of(shared), boardsOf(owner));
        Map<String, Object> row = seriesRow(loser);
        assertEquals(false, row.get("enabled"));
        assertEquals(message, row.get("last_run_message"));
        assertEquals(List.of(), boardsOf(loser));
    }

    @Test
    void migrate_twoSwitchedOffSeriesWantTheSameBoard_leavesBothOff() {
        UUID shared = board("Contested");
        UUID ghost = series("Ghost", true, null);
        seriesBoard(ghost, shared, 0);
        UUID first = series("First", false, "Switched off: board Contested belongs to series Ghost");
        sprint(first, 1, "ACTIVE", "2026-11-02T00:00:00Z", "2026-11-16T00:00:00Z", shared);
        UUID second = series("Second", false, "Switched off: board Contested belongs to series Ghost");
        sprint(second, 1, "ACTIVE", "2026-11-03T00:00:00Z", "2026-11-17T00:00:00Z", shared);

        databases.migrate(scratchName, TARGET);

        assertEquals(false, seriesRow(ghost).get("enabled"));
        assertEquals(false, seriesRow(first).get("enabled"));
        assertEquals(false, seriesRow(second).get("enabled"));
        assertEquals(List.of(shared), boardsOf(ghost));
    }
}
