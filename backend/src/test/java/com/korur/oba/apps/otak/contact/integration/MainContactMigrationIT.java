package com.korur.oba.apps.otak.contact.integration;

// 1. Framework-level
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
import static org.junit.jupiter.api.Assertions.assertThrows;

// 3. Java SDK
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.support.ObaIntegrationTest;
import com.korur.oba.support.ScratchDatabases;

/**
 * Proves V1_2_117 on a scratch database: the schema is migrated to 1.2.116, contacts are seeded
 * and the migration is applied. The backfill must mark the oldest contact of each relation as
 * main, and the partial unique index must refuse a second main contact on one relation while
 * allowing any number of non-main contacts and one main contact per relation.
 */
class MainContactMigrationIT extends ObaIntegrationTest {

    private static final String BASELINE = "1.2.116";
    private static final String TARGET = "1.2.117";

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

    private UUID relation() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO otak.relations (id, name, type, organization_id) "
                + "VALUES (?, ?, 'SUPPLIER', ?)", id, "Relation " + id.toString().substring(0, 8), organization);
        return id;
    }

    private UUID contact(UUID relation, String createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO otak.contacts (id, name, relation_id, organization_id, created_at) "
                + "VALUES (?, ?, ?, ?, ?::timestamptz)", id, "Contact " + id.toString().substring(0, 8),
                relation, organization, createdAt);
        return id;
    }

    private List<UUID> mainContactsOf(UUID relation) {
        return jdbc.queryForList("SELECT id FROM otak.contacts WHERE relation_id = ? AND main_contact",
                UUID.class, relation);
    }

    @Test
    void migrate_relationWithSeveralContacts_marksTheOldestAsMain() {
        UUID relation = relation();
        UUID newest = contact(relation, "2026-03-01T10:00:00Z");
        UUID oldest = contact(relation, "2026-01-01T10:00:00Z");
        UUID middle = contact(relation, "2026-02-01T10:00:00Z");

        migrate();

        assertEquals(List.of(oldest), mainContactsOf(relation));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT count(*) FROM otak.contacts WHERE id IN (?, ?) AND main_contact", Long.class,
                newest, middle));
    }

    @Test
    void migrate_contactsCreatedAtTheSameInstant_marksExactlyOne() {
        UUID relation = relation();
        contact(relation, "2026-01-01T10:00:00Z");
        contact(relation, "2026-01-01T10:00:00Z");
        contact(relation, "2026-01-01T10:00:00Z");

        migrate();

        assertEquals(1, mainContactsOf(relation).size());
    }

    @Test
    void migrate_twoRelations_eachGetsItsOwnMainAndRelationWithoutContactsIsUntouched() {
        UUID first = relation();
        UUID second = relation();
        UUID empty = relation();
        UUID firstOldest = contact(first, "2026-01-01T10:00:00Z");
        contact(first, "2026-02-01T10:00:00Z");
        UUID secondOnly = contact(second, "2026-05-01T10:00:00Z");

        migrate();

        assertEquals(List.of(firstOldest), mainContactsOf(first));
        assertEquals(List.of(secondOnly), mainContactsOf(second));
        assertEquals(0, mainContactsOf(empty).size());
        assertEquals(2L, jdbc.queryForObject("SELECT count(*) FROM otak.contacts WHERE main_contact", Long.class));
    }

    @Test
    void insert_secondMainContactOnOneRelation_isRejectedByThePartialUniqueIndex() {
        UUID relation = relation();
        contact(relation, "2026-01-01T10:00:00Z");
        migrate();

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO otak.contacts (id, name, relation_id, organization_id, main_contact) "
                        + "VALUES (?, 'Second main', ?, ?, true)", UUID.randomUUID(), relation, organization));
        assertEquals(1, mainContactsOf(relation).size());
    }

    @Test
    void insert_nonMainContactsAndOneMainPerRelation_areAccepted() {
        UUID first = relation();
        UUID second = relation();
        migrate();

        for (int i = 0; i < 3; i++) {
            jdbc.update("INSERT INTO otak.contacts (id, name, relation_id, organization_id) VALUES (?, 'Plain', ?, ?)",
                    UUID.randomUUID(), first, organization);
        }
        jdbc.update("INSERT INTO otak.contacts (id, name, relation_id, organization_id, main_contact) "
                + "VALUES (?, 'Main A', ?, ?, true)", UUID.randomUUID(), first, organization);
        jdbc.update("INSERT INTO otak.contacts (id, name, relation_id, organization_id, main_contact) "
                + "VALUES (?, 'Main B', ?, ?, true)", UUID.randomUUID(), second, organization);

        assertEquals(1, mainContactsOf(first).size());
        assertEquals(1, mainContactsOf(second).size());
    }

    @Test
    void update_secondContactToMainAfterTheFirstIsCleared_isAccepted() {
        UUID relation = relation();
        UUID oldest = contact(relation, "2026-01-01T10:00:00Z");
        UUID other = contact(relation, "2026-02-01T10:00:00Z");
        migrate();

        jdbc.update("UPDATE otak.contacts SET main_contact = false WHERE id = ?", oldest);
        jdbc.update("UPDATE otak.contacts SET main_contact = true WHERE id = ?", other);

        assertEquals(List.of(other), mainContactsOf(relation));
    }
}
