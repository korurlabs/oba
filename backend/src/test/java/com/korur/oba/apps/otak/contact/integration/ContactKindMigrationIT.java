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
 * Proves V1_2_120 on a scratch database: the schema is migrated to 1.2.118, contacts are seeded
 * with the main contact flag of V1_2_117 and the migration is applied. Every existing contact must
 * become a PERSON and keep its flag as main person contact, no contact may be a main business
 * contact, the two check constraints must refuse a flag on the wrong kind, and the partial unique
 * indexes must refuse a second main contact of the same flag on one relation.
 */
class ContactKindMigrationIT extends ObaIntegrationTest {

    private static final String BASELINE = "1.2.118";
    private static final String TARGET = "1.2.120";

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

    /** Inserts a contact on the pre-migration schema, where the flag is still called main_contact. */
    private UUID legacyContact(UUID relation, boolean main) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO otak.contacts (id, name, relation_id, organization_id, main_contact) "
                + "VALUES (?, ?, ?, ?, ?)", id, "Contact " + id.toString().substring(0, 8), relation,
                organization, main);
        return id;
    }

    /** Inserts a contact on the migrated schema with an explicit kind and both main flags. */
    private void insert(UUID relation, String kind, boolean mainPerson, boolean mainBusiness) {
        jdbc.update("INSERT INTO otak.contacts (id, name, relation_id, organization_id, kind, "
                + "main_person_contact, main_business_contact) "
                + "VALUES (?, 'Contact', ?, ?, ?::otak_contact_kind_enum, ?, ?)", UUID.randomUUID(), relation,
                organization, kind, mainPerson, mainBusiness);
    }

    private List<UUID> mainPersonContactsOf(UUID relation) {
        return jdbc.queryForList("SELECT id FROM otak.contacts WHERE relation_id = ? AND main_person_contact",
                UUID.class, relation);
    }

    @Test
    void migrate_existingContacts_allBecomePerson() {
        UUID relation = relation();
        legacyContact(relation, true);
        legacyContact(relation, false);
        legacyContact(relation, false);

        migrate();

        assertEquals(3L, jdbc.queryForObject(
                "SELECT count(*) FROM otak.contacts WHERE kind = 'PERSON'", Long.class));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT count(*) FROM otak.contacts WHERE kind <> 'PERSON'", Long.class));
    }

    @Test
    void migrate_mainContact_becomesMainPersonContactAndNoBusinessIsMain() {
        UUID first = relation();
        UUID second = relation();
        UUID firstMain = legacyContact(first, true);
        legacyContact(first, false);
        UUID secondMain = legacyContact(second, true);

        migrate();

        assertEquals(List.of(firstMain), mainPersonContactsOf(first));
        assertEquals(List.of(secondMain), mainPersonContactsOf(second));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT count(*) FROM otak.contacts WHERE main_business_contact", Long.class));
    }

    @Test
    void insert_mainPersonFlagOnABusinessContact_isRejectedByTheCheckConstraint() {
        UUID relation = relation();
        migrate();

        assertThrows(DataIntegrityViolationException.class, () -> insert(relation, "BUSINESS", true, false));
    }

    @Test
    void insert_mainBusinessFlagOnAPersonContact_isRejectedByTheCheckConstraint() {
        UUID relation = relation();
        migrate();

        assertThrows(DataIntegrityViolationException.class, () -> insert(relation, "PERSON", false, true));
    }

    @Test
    void insert_secondMainBusinessContactOnOneRelation_isRejectedByThePartialUniqueIndex() {
        UUID relation = relation();
        migrate();
        insert(relation, "BUSINESS", false, true);

        assertThrows(DataIntegrityViolationException.class, () -> insert(relation, "BUSINESS", false, true));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT count(*) FROM otak.contacts WHERE relation_id = ? AND main_business_contact",
                Long.class, relation));
    }

    @Test
    void insert_secondMainPersonContactOnOneRelation_isRejectedByTheRenamedUniqueIndex() {
        UUID relation = relation();
        legacyContact(relation, true);
        migrate();

        assertThrows(DataIntegrityViolationException.class, () -> insert(relation, "PERSON", true, false));
        assertEquals(1, mainPersonContactsOf(relation).size());
    }

    @Test
    void insert_oneMainPersonAndOneMainBusinessPerRelationAndOtherRelations_areAccepted() {
        UUID first = relation();
        UUID second = relation();
        migrate();

        insert(first, "PERSON", true, false);
        insert(first, "BUSINESS", false, true);
        insert(first, "PERSON", false, false);
        insert(first, "BUSINESS", false, false);
        insert(second, "PERSON", true, false);
        insert(second, "BUSINESS", false, true);

        assertEquals(1, mainPersonContactsOf(first).size());
        assertEquals(1, mainPersonContactsOf(second).size());
        assertEquals(2L, jdbc.queryForObject(
                "SELECT count(*) FROM otak.contacts WHERE main_business_contact", Long.class));
    }
}
