package com.korur.oba.shrine.storage.repository.integration;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

// 3. Java SDK
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.support.ObaIntegrationTest;
import com.korur.oba.support.ScratchDatabases;

/**
 * Proves the data conversion of V1_2_128 (attachments belong to an organization) on a scratch
 * database: the schema is migrated to 1.2.127, attachments of every owner type are seeded as
 * they existed before the organization column, and the migration is applied.
 *
 * Every owner type must give its rows the organization of the owner, a row whose owner no longer
 * exists must be removed, and a row of an owner type that is removed must abort the migration and
 * leave the schema as it was. The schema at 1.2.127 is built once for the class, and every test
 * seeds and migrates its own copy of it (see ScratchDatabases), so the database the application
 * runs on is never touched.
 */
class AttachmentOrganizationMigrationIT extends ObaIntegrationTest {

    private static final String BEFORE = "1.2.127";
    private static final String AFTER = "1.2.128";

    private static ScratchDatabases databases;

    private String scratchName;
    private JdbcTemplate jdbc;
    private UUID organizationA;
    private UUID organizationB;
    private UUID user;

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
        organizationA = organization();
        organizationB = organization();
        user = UUID.randomUUID();
        jdbc.update("INSERT INTO shrine.users (id, email) VALUES (?, ?)", user, user + "@example.test");
    }

    @AfterEach
    void dropScratchDatabase() throws SQLException {
        if (scratchName != null) {
            databases.drop(scratchName);
        }
    }

    private void migrate() {
        databases.migrate(scratchName, AFTER);
    }

    // ---- seeding helpers (the schema as of 1.2.127) ----

    private UUID organization() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO shrine.organizations (id, name, status) VALUES (?, ?, 'ACTIVE')",
                id, "org " + id);
        return id;
    }

    private UUID ticketIn(UUID organization) {
        UUID boardId = UUID.randomUUID();
        String prefix = "T" + boardId.toString().replace("-", "").substring(0, 8).toUpperCase();
        jdbc.update("INSERT INTO kazan.boards (id, name, prefix, slug, organization_id) VALUES (?, ?, ?, ?, ?)",
                boardId, "board " + prefix, prefix, "board-" + boardId, organization);
        UUID ticketId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO kazan.tickets (id, title, key, type, mission_rank, board_id, created_by_id)
                VALUES (?, 'ticket', ?, 'TASK'::kazan_ticket_type_enum, 'M'::kazan_mission_rank_enum, ?, ?)
                """, ticketId, "KEY-" + ticketId, boardId, user);
        return ticketId;
    }

    private UUID chatMessageIn(UUID organization) {
        UUID channelId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO kazan.chat_channels (id, name, slug, type, purpose, visibility, organization_id)
                VALUES (?, 'general', ?, 'TEXT', 'PULL_REQUESTS', 'PUBLIC', ?)
                """, channelId, "general-" + channelId, organization);
        UUID messageId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO kazan.chat_messages (id, channel_id, organization_id, author_id, content)
                VALUES (?, ?, ?, ?, 'hello')
                """, messageId, channelId, organization, user);
        return messageId;
    }

    private UUID kalkanPageIn(UUID organization) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO kalkan.pages (id, organization_id, slug, position, created_by_id, updated_by_id)
                VALUES (?, ?, ?, 0, ?, ?)
                """, id, organization, "page-" + id.toString().substring(0, 8), user, user);
        return id;
    }

    private UUID relationIn(UUID organization) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO otak.relations (id, name, type, organization_id) VALUES (?, ?, 'SUPPLIER', ?)",
                id, "relation " + id, organization);
        return id;
    }

    private UUID mailIn(UUID organization) {
        UUID relationId = relationIn(organization);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO otak.mails (id, subject, body, language, organization_id, relation_id)
                VALUES (?, 'subject', 'body', 'EN'::otak_document_language_enum, ?, ?)
                """, id, organization, relationId);
        return id;
    }

    private UUID attachment(String entityType, UUID entityId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO shrine.attachments (id, url, file_name, entity_type, entity_id)
                VALUES (?, ?, 'file.pdf', ?::shrine_storage_entity_type_enum, ?)
                """, id, "https://blob.example.test/" + id, entityType, entityId);
        return id;
    }

    private UUID organizationOf(UUID attachment) {
        return jdbc.queryForObject(
                "SELECT organization_id FROM shrine.attachments WHERE id = ?", UUID.class, attachment);
    }

    private int attachmentCount(UUID attachment) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM shrine.attachments WHERE id = ?", Integer.class, attachment);
    }

    private boolean hasOrganizationColumn() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'shrine' AND table_name = 'attachments' AND column_name = 'organization_id'
                """, Integer.class) > 0;
    }

    // ---- backfill per owner type ----

    @Test
    void migrate_rowsOfEveryOwnerType_takeTheOrganizationOfTheirOwner() {
        UUID ticket = attachment("TICKET", ticketIn(organizationA));
        UUID chatMessage = attachment("CHAT_MESSAGE", chatMessageIn(organizationB));
        UUID kalkanPage = attachment("KALKAN_PAGE", kalkanPageIn(organizationA));
        UUID mail = attachment("MAIL", mailIn(organizationB));
        UUID relation = attachment("RELATION", relationIn(organizationA));

        migrate();

        assertEquals(organizationA, organizationOf(ticket));
        assertEquals(organizationB, organizationOf(chatMessage));
        assertEquals(organizationA, organizationOf(kalkanPage));
        assertEquals(organizationB, organizationOf(mail));
        assertEquals(organizationA, organizationOf(relation));
    }

    @Test
    void migrate_sameOwnerTypeInTwoOrganizations_eachRowTakesItsOwnOrganization() {
        UUID ofA = attachment("TICKET", ticketIn(organizationA));
        UUID ofB = attachment("TICKET", ticketIn(organizationB));

        migrate();

        assertEquals(organizationA, organizationOf(ofA));
        assertEquals(organizationB, organizationOf(ofB));
    }

    // ---- orphans ----

    @Test
    void migrate_rowsWhoseOwnerNoLongerExists_areRemovedAndOthersKept() {
        UUID kept = attachment("RELATION", relationIn(organizationA));
        List<UUID> orphans = List.of(
                attachment("TICKET", UUID.randomUUID()),
                attachment("CHAT_MESSAGE", UUID.randomUUID()),
                attachment("KALKAN_PAGE", UUID.randomUUID()),
                attachment("MAIL", UUID.randomUUID()),
                attachment("RELATION", UUID.randomUUID()));

        migrate();

        assertEquals(1, attachmentCount(kept));
        assertEquals(organizationA, organizationOf(kept));
        for (UUID orphan : orphans) {
            assertEquals(0, attachmentCount(orphan));
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM shrine.attachments", Integer.class));
    }

    @Test
    void migrate_emptyAttachmentsTable_succeeds() {
        migrate();

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM shrine.attachments", Integer.class));
    }

    // ---- removed owner types ----

    @Test
    void migrate_rowOfRemovedOwnerType_abortsAndLeavesTheSchemaAndRowsUnchanged() {
        for (String removed : List.of("ORGANIZATION", "USER", "INVOICE")) {
            UUID goodRow = attachment("RELATION", relationIn(organizationA));
            UUID removedRow = attachment(removed, UUID.randomUUID());
            UUID orphanRow = attachment("TICKET", UUID.randomUUID());

            assertThrows(FlywayException.class, this::migrate, removed);

            assertFalse(hasOrganizationColumn(), removed);
            assertEquals(1, attachmentCount(goodRow), removed);
            assertEquals(1, attachmentCount(removedRow), removed);
            assertEquals(1, attachmentCount(orphanRow), removed);
            jdbc.update("DELETE FROM shrine.attachments");
        }
    }

    // ---- resulting schema ----

    @Test
    void migrate_afterBackfill_enumHasOnlyTheFiveOwnerTypesAndRemovedOnesAreRefused() {
        migrate();

        List<String> labels = jdbc.queryForList("""
                SELECT enumlabel FROM pg_enum e JOIN pg_type t ON t.oid = e.enumtypid
                WHERE t.typname = 'shrine_storage_entity_type_enum' ORDER BY e.enumsortorder
                """, String.class);
        assertEquals(List.of("TICKET", "CHAT_MESSAGE", "KALKAN_PAGE", "MAIL", "RELATION"), labels);
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_type WHERE typname = 'shrine_storage_entity_type_enum_old'
                """, Integer.class));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO shrine.attachments (url, file_name, entity_type, entity_id, organization_id)
                VALUES ('https://blob.example.test/x', 'x.pdf', 'USER'::shrine_storage_entity_type_enum, ?, ?)
                """, UUID.randomUUID(), organizationA));
    }

    @Test
    void migrate_afterBackfill_organizationIsRequiredAndAnExistingOneIsEnforced() {
        migrate();

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO shrine.attachments (url, file_name, entity_type, entity_id)
                VALUES ('https://blob.example.test/y', 'y.pdf', 'MAIL'::shrine_storage_entity_type_enum, ?)
                """, UUID.randomUUID()));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO shrine.attachments (url, file_name, entity_type, entity_id, organization_id)
                VALUES ('https://blob.example.test/z', 'z.pdf', 'MAIL'::shrine_storage_entity_type_enum, ?, ?)
                """, UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void migrate_afterBackfill_deletingTheOrganizationDeletesItsAttachmentsOnly() {
        UUID ofA = attachment("RELATION", relationIn(organizationA));
        UUID ofB = attachment("RELATION", relationIn(organizationB));
        migrate();

        jdbc.update("DELETE FROM shrine.organizations WHERE id = ?", organizationA);

        assertEquals(0, attachmentCount(ofA));
        assertEquals(1, attachmentCount(ofB));
    }

    @Test
    void migrate_afterBackfill_organizationIndexExists() {
        migrate();

        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'shrine' AND tablename = 'attachments'
                  AND indexname = 'attachments_organization_id_idx'
                """, Integer.class));
    }
}
