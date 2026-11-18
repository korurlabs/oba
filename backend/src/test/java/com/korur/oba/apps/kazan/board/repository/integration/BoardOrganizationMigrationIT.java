package com.korur.oba.apps.kazan.board.repository.integration;

// 1. Framework-level
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

// 3. Java SDK
import java.sql.SQLException;
import java.util.UUID;

// 4. Internal
import com.korur.oba.support.ObaIntegrationTest;
import com.korur.oba.support.ScratchDatabases;

/**
 * Proves the data conversion of V1_2_96 (boards belong to an organization) and
 * V1_2_97 (alert routes belong to an organization) on a scratch database: the
 * schema is migrated to 1.2.95, rows as they existed before the organization
 * column are seeded, and the two migrations are applied.
 *
 * The schema at 1.2.95 is built once for the class, and every test seeds and
 * migrates its own copy of it on the server of the application's database (see
 * ScratchDatabases), so the database the application runs on is never touched.
 */
class BoardOrganizationMigrationIT extends ObaIntegrationTest {

    private static final String BEFORE = "1.2.95";
    private static final String AFTER = "1.2.97";

    private static ScratchDatabases databases;

    private String scratchName;
    private JdbcTemplate jdbc;
    private int clock;

    @BeforeAll
    static void createTemplate(@Autowired DataSourceProperties dataSource) throws SQLException {
        databases = ScratchDatabases.migratedTo(dataSource, BEFORE);
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

    // ---- seeding helpers (the schema as of 1.2.95) ----

    /** Organizations are created oldest first: each call is one day later than the previous. */
    private UUID organization(boolean deleted) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO shrine.organizations (id, name, status, deleted, created_at)
                VALUES (?, ?, 'ACTIVE', ?, TIMESTAMPTZ '2025-01-01 00:00:00+00' + (? * INTERVAL '1 day'))
                """, id, "org " + id, deleted, ++clock);
        return id;
    }

    private UUID user() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO shrine.users (id, email) VALUES (?, ?)", id, id + "@example.test");
        return id;
    }

    private UUID userIn(UUID organization) {
        UUID user = user();
        jdbc.update("""
                INSERT INTO shrine.organization_members (id, organization_id, user_id, deleted)
                VALUES (?, ?, ?, false)
                """, UUID.randomUUID(), organization, user);
        return user;
    }

    private UUID removedMemberOf(UUID organization) {
        UUID user = user();
        jdbc.update("""
                INSERT INTO shrine.organization_members (id, organization_id, user_id, deleted)
                VALUES (?, ?, ?, true)
                """, UUID.randomUUID(), organization, user);
        return user;
    }

    private UUID board(UUID parent, boolean deleted) {
        UUID id = UUID.randomUUID();
        String suffix = id.toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO kaizen.boards (id, name, prefix, slug, parent_board_id, deleted, created_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP + (? * INTERVAL '1 second'))
                """, id, "board " + suffix, "B" + suffix, "board-" + suffix, parent, deleted, ++clock);
        return id;
    }

    private UUID rootBoard() {
        return board(null, false);
    }

    private void boardMember(UUID board, UUID user, String role, boolean deleted) {
        jdbc.update("""
                INSERT INTO kaizen.board_members (id, role, board_id, user_id, deleted)
                VALUES (?, ?::kaizen_board_member_role_enum, ?, ?, ?)
                """, UUID.randomUUID(), role, board, user, deleted);
    }

    private void settingsSecurityBoard(UUID organization, UUID board) {
        jdbc.update("INSERT INTO kaizen.organization_settings (organization_id, security_board_id) VALUES (?, ?)",
                organization, board);
    }

    private void organizationSecurityBoard(UUID organization, UUID board) {
        jdbc.update("UPDATE shrine.organizations SET security_board_id = ? WHERE id = ?", board, organization);
    }

    private UUID route(String systemKey, UUID board, boolean isDefault) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO kaizen.alert_routes (id, system_key, board_id, is_default) VALUES (?, ?, ?, ?)",
                id, systemKey, board, isDefault);
        return id;
    }

    private UUID organizationOfBoard(UUID board) {
        return jdbc.queryForObject("SELECT organization_id FROM kaizen.boards WHERE id = ?", UUID.class, board);
    }

    private UUID organizationOfRoute(UUID route) {
        return jdbc.queryForObject("SELECT organization_id FROM kaizen.alert_routes WHERE id = ?", UUID.class, route);
    }

    // ---- V1_2_96 backfill rules ----

    @Test
    void migrate_securityBoardInOrganizationSettings_goesToThatOrganizationEvenWithMembersElsewhere() {
        UUID orgA = organization(false);
        UUID orgB = organization(false);
        UUID board = rootBoard();
        boardMember(board, userIn(orgB), "OWNER", false);
        boardMember(board, userIn(orgB), "MEMBER", false);
        settingsSecurityBoard(orgA, board);

        migrateTo(AFTER);

        assertEquals(orgA, organizationOfBoard(board));
    }

    @Test
    void migrate_securityBoardOnTheOrganizationRow_goesToThatOrganization() {
        UUID orgA = organization(false);
        UUID orgB = organization(false);
        UUID board = rootBoard();
        boardMember(board, userIn(orgA), "OWNER", false);
        organizationSecurityBoard(orgB, board);

        migrateTo(AFTER);

        assertEquals(orgB, organizationOfBoard(board));
    }

    @Test
    void migrate_securityBoardOfTwoOrganizations_goesToTheOldest() {
        UUID orgA = organization(false);
        UUID orgB = organization(false);
        UUID board = rootBoard();
        settingsSecurityBoard(orgB, board);
        organizationSecurityBoard(orgA, board);

        migrateTo(AFTER);

        assertEquals(orgA, organizationOfBoard(board));
    }

    @Test
    void migrate_membersInTwoOrganizations_goesToTheOrganizationWithMostLiveMembers() {
        UUID orgA = organization(false);
        UUID orgB = organization(false);
        UUID board = rootBoard();
        boardMember(board, userIn(orgA), "OWNER", false);
        boardMember(board, userIn(orgB), "MEMBER", false);
        boardMember(board, userIn(orgB), "MEMBER", false);

        migrateTo(AFTER);

        assertEquals(orgB, organizationOfBoard(board));
    }

    @Test
    void migrate_removedMembersAndDeletedOrganizations_doNotCountForTheMajority() {
        UUID orgA = organization(false);
        UUID orgB = organization(false);
        UUID deletedOrg = organization(true);
        UUID board = rootBoard();
        boardMember(board, userIn(orgA), "MEMBER", false);
        boardMember(board, removedMemberOf(orgB), "MEMBER", false);
        boardMember(board, userIn(orgB), "MEMBER", true);
        boardMember(board, userIn(deletedOrg), "MEMBER", false);
        boardMember(board, userIn(deletedOrg), "MEMBER", false);

        migrateTo(AFTER);

        assertEquals(orgA, organizationOfBoard(board));
    }

    @Test
    void migrate_tieOfMembers_goesToTheOrganizationOfTheOwner() {
        UUID orgA = organization(false);
        UUID orgB = organization(false);
        UUID board = rootBoard();
        boardMember(board, userIn(orgA), "MEMBER", false);
        boardMember(board, userIn(orgB), "OWNER", false);

        migrateTo(AFTER);

        assertEquals(orgB, organizationOfBoard(board));
    }

    @Test
    void migrate_tieOfMembersWithoutOwner_goesToTheOldestOrganization() {
        UUID orgA = organization(false);
        UUID orgB = organization(false);
        UUID board = rootBoard();
        boardMember(board, userIn(orgB), "MEMBER", false);
        boardMember(board, userIn(orgA), "MEMBER", false);

        migrateTo(AFTER);

        assertEquals(orgA, organizationOfBoard(board));
    }

    @Test
    void migrate_boardWithoutAnyMatch_goesToTheOldestLiveOrganizationAndSoftDeletedRootsAreAssigned() {
        UUID deletedOldest = organization(true);
        UUID liveOldest = organization(false);
        UUID liveNewer = organization(false);
        UUID orphan = rootBoard();
        UUID softDeletedRoot = board(null, true);

        migrateTo(AFTER);

        assertEquals(liveOldest, organizationOfBoard(orphan));
        assertEquals(liveOldest, organizationOfBoard(softDeletedRoot));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM kaizen.boards WHERE organization_id IN (?, ?)",
                Integer.class, deletedOldest, liveNewer));
    }

    @Test
    void migrate_onlySoftDeletedOrganizationsExist_assignsTheOldestOne() {
        UUID onlyDeleted = organization(true);
        UUID board = rootBoard();

        migrateTo(AFTER);

        assertEquals(onlyDeleted, organizationOfBoard(board));
    }

    @Test
    void migrate_boardsButNoOrganization_failsTheMigration() {
        rootBoard();

        assertThrows(FlywayException.class, () -> migrateTo(AFTER));
    }

    @Test
    void migrate_childBoardsOverTwoLevels_takeTheOrganizationOfTheirRoot() {
        UUID orgA = organization(false);
        UUID orgB = organization(false);
        UUID root = rootBoard();
        UUID child = board(root, false);
        UUID grandChild = board(child, false);
        UUID softDeletedChild = board(root, true);
        boardMember(root, userIn(orgB), "OWNER", false);
        boardMember(child, userIn(orgA), "OWNER", false);
        boardMember(child, userIn(orgA), "MEMBER", false);

        migrateTo(AFTER);

        assertEquals(orgB, organizationOfBoard(root));
        assertEquals(orgB, organizationOfBoard(child));
        assertEquals(orgB, organizationOfBoard(grandChild));
        assertEquals(orgB, organizationOfBoard(softDeletedChild));
    }

    @Test
    void migrate_securityBoardsOfAnotherOrganization_areClearedAndOwnOnesKept() {
        UUID orgA = organization(false);
        UUID orgB = organization(false);
        UUID boardOfA = rootBoard();
        UUID boardOfB = rootBoard();
        settingsSecurityBoard(orgA, boardOfA);
        organizationSecurityBoard(orgA, boardOfA);
        // orgB points at orgA's board from both places, and at its own board from none.
        settingsSecurityBoard(orgB, boardOfA);
        organizationSecurityBoard(orgB, boardOfA);
        settingsSecurityBoard(organization(false), boardOfB);

        migrateTo(AFTER);

        assertEquals(boardOfA, jdbc.queryForObject(
                "SELECT security_board_id FROM kaizen.organization_settings WHERE organization_id = ?", UUID.class, orgA));
        assertEquals(boardOfA, jdbc.queryForObject(
                "SELECT security_board_id FROM shrine.organizations WHERE id = ?", UUID.class, orgA));
        assertNull(jdbc.queryForObject(
                "SELECT security_board_id FROM kaizen.organization_settings WHERE organization_id = ?", UUID.class, orgB));
        assertNull(jdbc.queryForObject(
                "SELECT security_board_id FROM shrine.organizations WHERE id = ?", UUID.class, orgB));
    }

    // ---- V1_2_96 constraints ----

    @Test
    void migrate_afterBackfill_organizationIsRequiredAndChildStaysInTheParentsOrganization() {
        UUID orgA = organization(false);
        UUID orgB = organization(false);
        UUID root = rootBoard();
        boardMember(root, userIn(orgA), "OWNER", false);
        migrateTo(AFTER);

        assertThrowsDatabase(() -> jdbc.update(
                "INSERT INTO kaizen.boards (id, name, prefix, slug) VALUES (?, 'x', 'XX1', 'x-1')", UUID.randomUUID()));
        assertThrowsDatabase(() -> jdbc.update("""
                INSERT INTO kaizen.boards (id, name, prefix, slug, parent_board_id, organization_id)
                VALUES (?, 'x', 'XX2', 'x-2', ?, ?)
                """, UUID.randomUUID(), root, orgB));
        jdbc.update("""
                INSERT INTO kaizen.boards (id, name, prefix, slug, parent_board_id, organization_id)
                VALUES (?, 'x', 'XX3', 'x-3', ?, ?)
                """, UUID.randomUUID(), root, orgA);
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM kaizen.boards WHERE organization_id = ?", Integer.class, orgA));
    }

    // ---- V1_2_97 ----

    @Test
    void migrate_routes_takeTheOrganizationOfTheirBoard() {
        UUID orgA = organization(false);
        UUID orgB = organization(false);
        UUID boardOfA = rootBoard();
        UUID boardOfB = rootBoard();
        UUID softDeletedBoardOfB = board(null, true);
        settingsSecurityBoard(orgA, boardOfA);
        settingsSecurityBoard(orgB, boardOfB);
        organizationSecurityBoard(orgB, softDeletedBoardOfB);
        UUID routeOfA = route("api", boardOfA, true);
        UUID routeOfB = route("web", boardOfB, false);
        UUID routeToDeletedBoard = route("batch", softDeletedBoardOfB, false);

        migrateTo(AFTER);

        assertEquals(orgA, organizationOfRoute(routeOfA));
        assertEquals(orgB, organizationOfRoute(routeOfB));
        assertEquals(orgB, organizationOfRoute(routeToDeletedBoard));
    }

    @Test
    void migrate_afterBackfill_systemKeyAndDefaultAreUniquePerOrganization() {
        UUID orgA = organization(false);
        UUID orgB = organization(false);
        UUID boardOfA = rootBoard();
        UUID boardOfB = rootBoard();
        UUID secondBoardOfA = rootBoard();
        settingsSecurityBoard(orgA, boardOfA);
        settingsSecurityBoard(orgB, boardOfB);
        organizationSecurityBoard(orgA, secondBoardOfA);
        route("api", boardOfA, true);
        migrateTo(AFTER);

        jdbc.update("""
                INSERT INTO kaizen.alert_routes (id, system_key, board_id, is_default, organization_id)
                VALUES (?, 'api', ?, true, ?)
                """, UUID.randomUUID(), boardOfB, orgB);

        assertThrowsDatabase(() -> jdbc.update("""
                INSERT INTO kaizen.alert_routes (id, system_key, board_id, is_default, organization_id)
                VALUES (?, 'api', ?, false, ?)
                """, UUID.randomUUID(), secondBoardOfA, orgA));
        assertThrowsDatabase(() -> jdbc.update("""
                INSERT INTO kaizen.alert_routes (id, system_key, board_id, is_default, organization_id)
                VALUES (?, 'web', ?, true, ?)
                """, UUID.randomUUID(), secondBoardOfA, orgA));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM kaizen.alert_routes WHERE organization_id = ? AND is_default", Integer.class, orgA));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM kaizen.alert_routes WHERE organization_id = ? AND is_default", Integer.class, orgB));
    }

    @Test
    void migrate_afterBackfill_routeToABoardOfAnotherOrganizationIsRefused() {
        UUID orgA = organization(false);
        UUID orgB = organization(false);
        UUID boardOfA = rootBoard();
        UUID boardOfB = rootBoard();
        settingsSecurityBoard(orgA, boardOfA);
        settingsSecurityBoard(orgB, boardOfB);
        migrateTo(AFTER);

        assertThrowsDatabase(() -> jdbc.update("""
                INSERT INTO kaizen.alert_routes (id, system_key, board_id, organization_id)
                VALUES (?, 'api', ?, ?)
                """, UUID.randomUUID(), boardOfB, orgA));
        assertThrowsDatabase(() -> jdbc.update("""
                INSERT INTO kaizen.alert_routes (id, system_key, board_id)
                VALUES (?, 'api', ?)
                """, UUID.randomUUID(), boardOfA));
    }

    private static void assertThrowsDatabase(Runnable statement) {
        assertThrows(DataIntegrityViolationException.class, statement::run);
    }
}
