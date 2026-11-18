package com.korur.oba.apps.kaizen.sprint.repository.integration;

// 1. Framework-level
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 3. Java SDK
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 2. Third-party
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

// 4. Internal
import com.korur.oba.support.ObaIntegrationTest;
import com.korur.oba.support.ScratchDatabases;

/**
 * Proves the data conversion of V1_2_90 and the drops of V1_2_91 on a scratch
 * database: the schema is migrated to 1.2.89, old-style recurrence rows are
 * seeded, and the two migrations are applied.
 *
 * The schema at 1.2.89 is built once for the class, and every test seeds and
 * migrates its own copy of it on the server of the application's database (see
 * ScratchDatabases), so the database the application runs on is never touched.
 */
class SprintSeriesMigrationIT extends ObaIntegrationTest {

    /** The oldest version a test starts from; the template is built to it once. */
    private static final String BASELINE = "1.2.89";

    private static ScratchDatabases databases;

    private String scratchName;
    private JdbcTemplate jdbc;

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
    }

    @AfterEach
    void dropScratchDatabase() throws SQLException {
        if (scratchName != null) {
            databases.drop(scratchName);
        }
    }

    private void migrateTo(String target) {
        databases.migrate(scratchName, target);
    }

    private UUID board() {
        UUID id = UUID.randomUUID();
        String suffix = id.toString().substring(0, 8);
        jdbc.update("INSERT INTO kaizen.boards (id, name, prefix, slug) VALUES (?, ?, ?, ?)",
                id, "board " + suffix, "B" + suffix, "board-" + suffix);
        return id;
    }

    private UUID sprint(String name, String status, String start, String end) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO kaizen.sprints (id, name, number, status, start_date, end_date)
                VALUES (?, ?, 1, ?::kaizen_sprint_status_enum, ?::timestamptz, ?::timestamptz)
                """, id, name, status, start, end);
        return id;
    }

    private void link(UUID sprint, UUID board, int offsetSeconds) {
        jdbc.update("""
                INSERT INTO kaizen.board_sprints (id, board_id, sprint_id, slug, created_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP + (? * INTERVAL '1 second'))
                """, UUID.randomUUID(), board, sprint, "s-" + sprint.toString().substring(0, 8), offsetSeconds);
    }

    private void recurrence(UUID sprint, UUID next, boolean enabled, String action) {
        jdbc.update("""
                INSERT INTO kaizen.sprint_recurrences (id, sprint_id, next_sprint_id, enabled, unfinished_ticket_action)
                VALUES (?, ?, ?, ?, ?::kaizen_sprint_recurrence_ticket_action_enum)
                """, UUID.randomUUID(), sprint, next, enabled, action);
    }

    private Map<String, Object> seriesOf(UUID sprint) {
        return jdbc.queryForMap("""
                SELECT ss.* FROM kaizen.sprint_series ss
                JOIN kaizen.sprints s ON s.series_id = ss.id WHERE s.id = ?
                """, sprint);
    }

    private Integer roundOf(UUID sprint) {
        return jdbc.queryForObject("SELECT round FROM kaizen.sprints WHERE id = ?", Integer.class, sprint);
    }

    @Test
    void migrate_singleEnabledRowOnThreeBoards_becomesSeriesWithBoardsInLinkOrder() {
        migrateTo("1.2.89");
        UUID alpha = board();
        UUID beta = board();
        UUID gamma = board();
        UUID only = sprint("Atasprint 1", "ACTIVE", "2026-11-02T00:00:00Z", "2026-11-16T00:00:00Z");
        link(only, gamma, 3);
        link(only, alpha, 1);
        link(only, beta, 2);
        recurrence(only, null, true, "MOVE_TO_BACKLOG");

        migrateTo("1.2.91");

        Map<String, Object> series = seriesOf(only);
        assertEquals("Atasprint", series.get("name"));
        assertEquals(2, series.get("next_round"));
        assertEquals(14, series.get("length_days"));
        assertEquals(true, series.get("enabled"));
        assertEquals("MOVE_TO_BACKLOG", series.get("unfinished_ticket_action").toString());
        assertEquals(1, roundOf(only));
        List<UUID> boards = jdbc.queryForList("""
                SELECT board_id FROM kaizen.sprint_series_boards WHERE series_id = ? ORDER BY position
                """, UUID.class, series.get("id"));
        assertEquals(List.of(alpha, beta, gamma), boards);
        assertEquals("Atasprint 1", jdbc.queryForObject(
                "SELECT name FROM kaizen.sprints WHERE id = ?", String.class, only));
    }

    @Test
    void migrate_chain_givesConsecutiveRoundsFromTheFirstSprintsName() {
        migrateTo("1.2.89");
        UUID alpha = board();
        UUID seven = sprint("Sprint 7", "COMPLETED", "2026-10-05T00:00:00Z", "2026-10-12T00:00:00Z");
        UUID eight = sprint("Sprint 8", "COMPLETED", "2026-10-12T00:00:00Z", "2026-10-19T00:00:00Z");
        UUID nine = sprint("Sprint 9", "PLANNED", "2026-10-19T00:00:00Z", "2026-10-26T00:00:00Z");
        link(nine, alpha, 0);
        recurrence(seven, eight, true, "MOVE_TO_NEXT_SPRINT");
        recurrence(eight, nine, true, "MOVE_TO_NEXT_SPRINT");
        recurrence(nine, null, true, "MOVE_TO_NEXT_SPRINT");

        migrateTo("1.2.91");

        Map<String, Object> series = seriesOf(seven);
        assertEquals("Sprint", series.get("name"));
        assertEquals(10, series.get("next_round"));
        assertEquals(7, series.get("length_days"));
        assertEquals(7, roundOf(seven));
        assertEquals(8, roundOf(eight));
        assertEquals(9, roundOf(nine));
        assertEquals(series.get("id"), seriesOf(nine).get("id"));
        assertNull(series.get("next_creation_at"));
    }

    @Test
    void migrate_nameWithoutValidRound_keepsWholeNameAndStartsAtRoundOne() {
        migrateTo("1.2.89");
        UUID alpha = board();
        UUID zero = sprint("Atasprint 0", "PLANNED", "2026-11-02T00:00:00Z", "2026-11-09T00:00:00Z");
        UUID huge = sprint("X 1234567", "PLANNED", "2026-11-02T00:00:00Z", "2026-11-09T00:00:00Z");
        link(zero, alpha, 0);
        recurrence(zero, null, true, "MOVE_TO_NEXT_SPRINT");
        recurrence(huge, null, true, "MOVE_TO_NEXT_SPRINT");

        migrateTo("1.2.91");

        assertEquals("Atasprint 0", seriesOf(zero).get("name"));
        assertEquals(1, roundOf(zero));
        assertEquals("X 1234567", seriesOf(huge).get("name"));
        assertEquals(2, seriesOf(huge).get("next_round"));
    }

    @Test
    void migrate_lengthOutsideOneTo365Days_givesDisabledSeriesOfFourteenDaysWithTrimmedName() {
        migrateTo("1.2.89");
        UUID alpha = board();
        UUID long396 = sprint("  Release prep ", "PLANNED", "2026-01-01T00:00:00Z", "2027-02-01T00:00:00Z");
        link(long396, alpha, 0);
        recurrence(long396, null, true, "MOVE_TO_NEXT_SPRINT");

        migrateTo("1.2.91");

        Map<String, Object> series = seriesOf(long396);
        assertEquals("Release prep", series.get("name"));
        assertEquals(14, series.get("length_days"));
        assertEquals(false, series.get("enabled"));
        assertEquals(1, roundOf(long396));
    }

    @Test
    void migrate_disabledRowAndSprintWithoutRow_getNoSeries() {
        migrateTo("1.2.89");
        UUID disabled = sprint("Old 3", "COMPLETED", "2026-10-05T00:00:00Z", "2026-10-12T00:00:00Z");
        UUID plain = sprint("Plain", "PLANNED", "2026-11-02T00:00:00Z", "2026-11-09T00:00:00Z");
        recurrence(disabled, null, false, "MOVE_TO_NEXT_SPRINT");

        migrateTo("1.2.91");

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM kaizen.sprint_series", Integer.class));
        assertNull(roundOf(disabled));
        assertNull(roundOf(plain));
    }

    @Test
    void migrate_afterConversion_oldTableAndNumberColumnAreGoneAndEnumTypesStay() {
        migrateTo("1.2.89");

        migrateTo("1.2.91");

        assertFalse(exists("SELECT to_regclass('kaizen.sprint_recurrences') IS NOT NULL"));
        assertFalse(exists("""
                SELECT EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = 'kaizen' AND table_name = 'sprints' AND column_name = 'number')
                """));
        assertTrue(exists("SELECT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'kaizen_sprint_recurrence_run_status_enum')"));
    }

    @Test
    void migrate_afterConversion_roundRulesAreEnforced() {
        migrateTo("1.2.91");
        UUID series = UUID.randomUUID();
        jdbc.update("INSERT INTO kaizen.sprint_series (id, name, next_round, length_days) VALUES (?, 'S', 2, 7)", series);
        jdbc.update("INSERT INTO kaizen.sprints (id, name, series_id, round) VALUES (?, 'S 1', ?, 1)",
                UUID.randomUUID(), series);

        assertThrowsDatabase(() -> jdbc.update(
                "INSERT INTO kaizen.sprints (id, name, series_id, round) VALUES (?, 'S 1 again', ?, 1)",
                UUID.randomUUID(), series));
        assertThrowsDatabase(() -> jdbc.update(
                "INSERT INTO kaizen.sprints (id, name, series_id, round) VALUES (?, 'S 0', ?, 0)",
                UUID.randomUUID(), series));
        assertThrowsDatabase(() -> jdbc.update(
                "INSERT INTO kaizen.sprints (id, name, series_id) VALUES (?, 'S no round', ?)",
                UUID.randomUUID(), series));
        assertThrowsDatabase(() -> jdbc.update(
                "INSERT INTO kaizen.sprint_series (id, name, next_round, length_days) VALUES (?, 'S', 1, 366)",
                UUID.randomUUID()));
        assertThrowsDatabase(() -> jdbc.update(
                "INSERT INTO kaizen.sprint_series (id, name, next_round, length_days, create_days_before) VALUES (?, 'S', 1, 7, 365)",
                UUID.randomUUID()));
    }

    private boolean exists(String sql) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class));
    }

    private static void assertThrowsDatabase(Runnable statement) {
        assertThrows(DataIntegrityViolationException.class, statement::run);
    }

    @Test
    void migrate_enabledSeriesWithActiveLastSprint_keepsNextCreationAt() {
        migrateTo("1.2.89");
        UUID alpha = board();
        UUID active = sprint("Korur 1", "ACTIVE", "2026-11-02T00:00:00Z", "2026-11-16T00:00:00Z");
        link(active, alpha, 0);
        recurrence(active, null, true, "MOVE_TO_NEXT_SPRINT");
        jdbc.update("UPDATE kaizen.sprint_recurrences SET next_creation_at = ?::timestamptz WHERE sprint_id = ?",
                "2026-11-14T00:00:00Z", active);

        migrateTo("1.2.91");

        Instant kept = ((Timestamp) seriesOf(active).get("next_creation_at")).toInstant();
        assertEquals(Instant.parse("2026-11-14T00:00:00Z"), kept);
    }

    // ---------- V1_2_95: one series per board ----------

    private UUID namedBoard(String name) {
        UUID id = UUID.randomUUID();
        String suffix = id.toString().substring(0, 8);
        jdbc.update("INSERT INTO kaizen.boards (id, name, prefix, slug) VALUES (?, ?, ?, ?)",
                id, name, "B" + suffix, "board-" + suffix);
        return id;
    }

    private UUID series(String name, boolean enabled, String lastRunAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO kaizen.sprint_series
                    (id, name, next_round, enabled, length_days, next_creation_at, last_run_at, last_run_status, last_run_message)
                VALUES (?, ?, 2, ?, 7, ?::timestamptz, ?::timestamptz, ?::kaizen_sprint_recurrence_run_status_enum, ?)
                """, id, name, enabled,
                enabled ? "2030-01-01T00:00:00Z" : null,
                lastRunAt,
                lastRunAt == null ? null : "OK",
                lastRunAt == null ? null : "keep me");
        return id;
    }

    private void seriesSprint(UUID series, int round, String start) {
        jdbc.update("""
                INSERT INTO kaizen.sprints (id, name, series_id, round, start_date)
                VALUES (?, 'S', ?, ?, ?::timestamptz)
                """, UUID.randomUUID(), series, round, start);
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

    private UUID ownerOf(UUID board) {
        return jdbc.queryForObject("SELECT series_id FROM kaizen.sprint_series_boards WHERE board_id = ?",
                UUID.class, board);
    }

    private Map<String, Object> seriesRow(UUID id) {
        return jdbc.queryForMap("SELECT * FROM kaizen.sprint_series WHERE id = ?", id);
    }

    @Test
    void migrateOneSeriesPerBoard_boardInTwoEnabledSeries_keepsTheOneThatRanLastAndSwitchesOffTheOther() {
        migrateTo("1.2.91");
        UUID boardK = namedBoard("K");
        UUID boardL = namedBoard("L");
        UUID a = series("Alpha", true, Instant.now().minusSeconds(86_400).toString());
        UUID b = series("Beta", true, Instant.now().toString());
        seriesBoard(a, boardK, 0);
        seriesBoard(a, boardL, 1);
        seriesBoard(b, boardK, 0);

        migrateTo("1.2.95");

        assertEquals(b, ownerOf(boardK));
        Map<String, Object> lost = seriesRow(a);
        assertEquals(false, lost.get("enabled"));
        assertNull(lost.get("next_creation_at"));
        assertEquals("FAILED", lost.get("last_run_status").toString());
        assertEquals("Switched off: board K belongs to series Beta", lost.get("last_run_message"));
        assertEquals(List.of(boardL), boardsOf(a));
        assertEquals(true, seriesRow(b).get("enabled"));
        assertEquals("keep me", seriesRow(b).get("last_run_message"));
    }

    @Test
    void migrateOneSeriesPerBoard_boardInEnabledAndDisabledSeries_keepsTheEnabledOne() {
        migrateTo("1.2.91");
        UUID boardM = namedBoard("M");
        UUID enabled = series("Gamma", true, Instant.now().minusSeconds(864_000).toString());
        UUID disabled = series("Delta", false, Instant.now().toString());
        seriesBoard(enabled, boardM, 0);
        seriesBoard(disabled, boardM, 0);

        migrateTo("1.2.95");

        assertEquals(enabled, ownerOf(boardM));
        assertEquals(true, seriesRow(enabled).get("enabled"));
        Map<String, Object> untouched = seriesRow(disabled);
        assertEquals(false, untouched.get("enabled"));
        assertEquals("keep me", untouched.get("last_run_message"));
        assertEquals("OK", untouched.get("last_run_status").toString());
        assertEquals(List.of(), boardsOf(disabled));
    }

    @Test
    void migrateOneSeriesPerBoard_boardInTwoDisabledSeries_keepsTheOneThatRanLast() {
        migrateTo("1.2.91");
        UUID boardN = namedBoard("N");
        UUID older = series("Epsilon", false, Instant.now().minusSeconds(864_000).toString());
        UUID newer = series("Zeta", false, Instant.now().minusSeconds(86_400).toString());
        seriesBoard(older, boardN, 0);
        seriesBoard(newer, boardN, 0);

        migrateTo("1.2.95");

        assertEquals(newer, ownerOf(boardN));
    }

    @Test
    void migrateOneSeriesPerBoard_neverRan_keepsTheSeriesWithTheLaterLiveSprintAndIgnoresADeletedOne() {
        migrateTo("1.2.91");
        UUID boardO = namedBoard("O");
        UUID earlier = series("Eta", true, null);
        UUID later = series("Theta", true, null);
        seriesSprint(earlier, 1, "2026-10-01T00:00:00Z");
        seriesSprint(later, 1, "2026-09-01T00:00:00Z");
        seriesSprint(later, 2, "2026-11-20T00:00:00Z");
        jdbc.update("UPDATE kaizen.sprints SET deleted = TRUE WHERE series_id = ? AND round = 2", later);
        seriesBoard(earlier, boardO, 0);
        seriesBoard(later, boardO, 0);

        migrateTo("1.2.95");

        assertEquals(earlier, ownerOf(boardO));
        assertEquals(false, seriesRow(later).get("enabled"));
    }

    @Test
    void migrateOneSeriesPerBoard_noRunsAndNoSprints_keepsTheLowestSeriesId() {
        migrateTo("1.2.91");
        UUID boardQ = namedBoard("Q");
        UUID one = series("Iota", true, null);
        UUID two = series("Kappa", true, null);
        seriesBoard(one, boardQ, 0);
        seriesBoard(two, boardQ, 0);
        UUID expected = one.toString().compareTo(two.toString()) < 0 ? one : two;
        UUID loser = expected.equals(one) ? two : one;

        migrateTo("1.2.95");

        assertEquals(expected, ownerOf(boardQ));
        assertEquals(false, seriesRow(loser).get("enabled"));
    }

    @Test
    void migrateOneSeriesPerBoard_boardInOneSeries_isUnchanged() {
        migrateTo("1.2.91");
        UUID boardP = namedBoard("P");
        UUID boardR = namedBoard("R");
        UUID only = series("Lambda", true, Instant.now().toString());
        seriesBoard(only, boardP, 0);
        seriesBoard(only, boardR, 1);

        migrateTo("1.2.95");

        assertEquals(List.of(boardP, boardR), boardsOf(only));
        assertEquals(true, seriesRow(only).get("enabled"));
        assertEquals("keep me", seriesRow(only).get("last_run_message"));
    }

    @Test
    void migrateOneSeriesPerBoard_longNames_truncateTheMessageTo500Characters() {
        migrateTo("1.2.91");
        UUID board = namedBoard("B".repeat(250));
        UUID kept = series("K".repeat(240), true, Instant.now().toString());
        UUID lost = series("Mu", true, Instant.now().minusSeconds(86_400).toString());
        seriesBoard(kept, board, 0);
        seriesBoard(lost, board, 0);

        migrateTo("1.2.95");

        String message = (String) seriesRow(lost).get("last_run_message");
        assertEquals(500, message.length());
        assertEquals(false, seriesRow(lost).get("enabled"));
    }

    @Test
    void migrateOneSeriesPerBoard_afterwards_boardIdIsUniqueAndTheOldIndexIsGone() {
        migrateTo("1.2.91");
        assertTrue(exists("SELECT to_regclass('kaizen.sprint_series_boards_board_id_idx') IS NOT NULL"));

        migrateTo("1.2.95");

        assertFalse(exists("SELECT to_regclass('kaizen.sprint_series_boards_board_id_idx') IS NOT NULL"));
        assertTrue(exists("""
                SELECT EXISTS (SELECT 1 FROM pg_constraint
                WHERE conname = 'sprint_series_boards_board_id_uk' AND contype = 'u')
                """));
        assertTrue(exists("SELECT to_regclass('kaizen.sprint_series_boards_series_id_board_id_uk') IS NOT NULL"));
        UUID board = namedBoard("Dup");
        UUID one = series("Nu", true, null);
        UUID two = series("Xi", true, null);
        seriesBoard(one, board, 0);
        assertThrowsDatabase(() -> seriesBoard(two, board, 0));
    }
}
