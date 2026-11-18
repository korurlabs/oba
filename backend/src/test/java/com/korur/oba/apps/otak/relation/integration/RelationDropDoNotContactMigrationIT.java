package com.korur.oba.apps.otak.relation.integration;

// 1. Framework-level
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

// 3. Java SDK
import java.sql.SQLException;
import java.util.UUID;

// 4. Internal
import com.korur.oba.support.ObaIntegrationTest;
import com.korur.oba.support.ScratchDatabases;

/**
 * Proves V1_2_121 on a scratch database: the schema is migrated to 1.2.120, relations are seeded
 * with both values of the do-not-contact flag and the migration is applied. The column must be gone
 * and every relation must survive with its other fields, whatever its flag was.
 */
class RelationDropDoNotContactMigrationIT extends ObaIntegrationTest {

    private static final String BASELINE = "1.2.120";
    private static final String TARGET = "1.2.121";

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
        jdbc.update("INSERT INTO shrine.organizations (id, name, status) VALUES (?, ?, 'ACTIVE')",
                organization, "Org " + organization.toString().substring(0, 8));
    }

    @AfterEach
    void dropScratchDatabase() throws SQLException {
        if (scratchName != null) {
            databases.drop(scratchName);
        }
    }

    private void migrate() {
        databases.migrate(scratchName, TARGET);
    }

    /** Inserts a relation on the pre-migration schema, where the flag still exists. */
    private UUID legacyRelation(String name, boolean doNotContact) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO otak.relations (id, name, type, organization_id, do_not_contact) "
                + "VALUES (?, ?, 'SUPPLIER', ?, ?)", id, name, organization, doNotContact);
        return id;
    }

    private long doNotContactColumns() {
        return jdbc.queryForObject("SELECT count(*) FROM information_schema.columns "
                + "WHERE table_schema = 'otak' AND table_name = 'relations' AND column_name = 'do_not_contact'",
                Long.class);
    }

    @Test
    void migrate_doNotContactColumn_isDropped() {
        assertEquals(1L, doNotContactColumns());

        migrate();

        assertEquals(0L, doNotContactColumns());
    }

    @Test
    void migrate_relationsWithEitherFlag_surviveWithTheirName() {
        UUID flagged = legacyRelation("Flagged", true);
        UUID unflagged = legacyRelation("Unflagged", false);

        migrate();

        assertEquals("Flagged", jdbc.queryForObject(
                "SELECT name FROM otak.relations WHERE id = ?", String.class, flagged));
        assertEquals("Unflagged", jdbc.queryForObject(
                "SELECT name FROM otak.relations WHERE id = ?", String.class, unflagged));
    }
}
