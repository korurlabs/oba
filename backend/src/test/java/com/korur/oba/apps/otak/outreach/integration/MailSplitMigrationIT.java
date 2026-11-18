package com.korur.oba.apps.otak.outreach.integration;

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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

// 3. Java SDK
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.support.ObaIntegrationTest;
import com.korur.oba.support.ScratchDatabases;

/**
 * Proves the split of V1_2_122 and the channels of V1_2_123 on a scratch database: the schema is
 * migrated to 1.2.121, mails stored as MAIL documents and MAIL document templates are seeded, and
 * the migrations are applied. Every mail must survive as a mail or mail template with its id,
 * content and timestamps, attachments must follow their outreach, and the document tables must
 * lose MAIL, subject and body.
 */
class MailSplitMigrationIT extends ObaIntegrationTest {

    private static final String BASELINE = "1.2.121";
    private static final String CREATED = "2026-10-01T08:00:00Z";
    private static final String UPDATED = "2026-10-02T09:30:00Z";

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

    private UUID organization() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO shrine.organizations (id, name, status) "
                + "VALUES (?, ?, 'ACTIVE'::shrine_organization_status_enum)", id, "Org " + id);
        return id;
    }

    private UUID relation(UUID organization) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO otak.relations (id, name, type, stage, organization_id) "
                + "VALUES (?, ?, 'CUSTOMER'::otak_relation_type_enum, 'DIRECT'::otak_pipeline_stage_enum, ?)",
                id, "Rel " + id, organization);
        return id;
    }

    private UUID mailTemplate(UUID organization, String name, String language, String subject, String body) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO otak.document_templates (id, name, type, language, subject, body, organization_id, created_at, updated_at) "
                + "VALUES (?, ?, 'MAIL'::otak_document_type_enum, ?::otak_document_language_enum, ?, ?, ?, ?::timestamptz, ?::timestamptz)",
                id, name, language, subject, body, organization, CREATED, UPDATED);
        return id;
    }

    private UUID fileTemplate(UUID organization, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO otak.document_templates (id, name, type, language, html_blob_url, pdf_blob_url, organization_id) "
                + "VALUES (?, ?, 'NDA'::otak_document_type_enum, 'EN'::otak_document_language_enum, 'h', 'p', ?)",
                id, name, organization);
        return id;
    }

    private UUID mailDocument(UUID organization, UUID relation, UUID template, String subject, String body, String language) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO otak.documents (id, name, type, language, subject, body, template_id, organization_id, relation_id, created_at, updated_at) "
                + "VALUES (?, ?, 'MAIL'::otak_document_type_enum, ?::otak_document_language_enum, ?, ?, ?, ?, ?, ?::timestamptz, ?::timestamptz)",
                id, "Mail " + id, language, subject, body, template, organization, relation, CREATED, UPDATED);
        return id;
    }

    private UUID fileDocument(UUID organization, UUID relation, UUID template) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO otak.documents (id, name, type, language, html_blob_url, pdf_blob_url, template_id, organization_id, relation_id) "
                + "VALUES (?, ?, 'NDA'::otak_document_type_enum, 'EN'::otak_document_language_enum, 'h', 'p', ?, ?, ?)",
                id, "Nda " + id, template, organization, relation);
        return id;
    }

    private UUID outreach(UUID organization, UUID relation, UUID mailDocument, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO otak.outreaches (id, title, type, status, sent_to, sent_at, organization_id, relation_id, mail_document_id) "
                + "VALUES (?, 'Title', 'MAIL'::otak_outreach_type_enum, ?::otak_outreach_status_enum, "
                + "CASE WHEN ? = 'SENT' THEN 'a@example.com' END, CASE WHEN ? = 'SENT' THEN CURRENT_TIMESTAMP END, ?, ?, ?)",
                id, status, status, status, organization, relation, mailDocument);
        return id;
    }

    private void attach(UUID outreach, UUID document, UUID relation) {
        jdbc.update("INSERT INTO otak.outreach_attachments (outreach_id, document_id, relation_id) VALUES (?, ?, ?)",
                outreach, document, relation);
    }

    private long scratchCount(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private void migrateSplit() {
        databases.migrate(scratchName, "1.2.122");
    }

    private void migrateChannels() {
        databases.migrate(scratchName, "1.2.123");
    }

    @Test
    void migrate_mailDocumentTemplates_becomeMailTemplatesWithTheSameIdAndContent() {
        UUID orgA = organization();
        UUID orgB = organization();
        UUID first = mailTemplate(orgA, "Intro", "NL", "Hello", "Body one");
        UUID second = mailTemplate(orgB, "Intro", "EN", "Hi", "Body two");
        UUID file = fileTemplate(orgA, "Nda");

        migrateSplit();

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, name, language::text AS language, subject, body, organization_id, "
                        + "(created_at = ?::timestamptz) AS created_kept, (updated_at = ?::timestamptz) AS updated_kept "
                        + "FROM otak.mail_templates WHERE id = ?", CREATED, UPDATED, first);
        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        assertEquals("Intro", row.get("name"));
        assertEquals("NL", row.get("language"));
        assertEquals("Hello", row.get("subject"));
        assertEquals("Body one", row.get("body"));
        assertEquals(orgA, row.get("organization_id"));
        assertEquals(true, row.get("created_kept"));
        assertEquals(true, row.get("updated_kept"));
        assertEquals(orgB, jdbc.queryForObject("SELECT organization_id FROM otak.mail_templates WHERE id = ?", UUID.class, second));
        assertEquals(2L, scratchCount("SELECT count(*) FROM otak.mail_templates"));
        assertEquals(0L, scratchCount("SELECT count(*) FROM otak.mail_templates WHERE id = ?", file));
    }

    @Test
    void migrate_mailDocumentTemplates_leaveTheDocumentTemplatesWithTheFileTemplatesOnly() {
        UUID org = organization();
        mailTemplate(org, "Intro", "NL", "Hello", "Body");
        UUID file = fileTemplate(org, "Nda");

        migrateSplit();

        assertEquals(1L, scratchCount("SELECT count(*) FROM otak.document_templates"));
        assertEquals(1L, scratchCount("SELECT count(*) FROM otak.document_templates WHERE id = ?", file));
    }

    @Test
    void migrate_mailDocumentsUsedByOutreaches_becomeMailsWithTheSameIdAndContent() {
        UUID org = organization();
        UUID relation = relation(org);
        UUID mail = mailDocument(org, relation, null, "Subject", "Body", "TR");
        UUID outreach = outreach(org, relation, mail, "DRAFT");

        migrateSplit();

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, subject, body, language::text AS language, organization_id, relation_id, mail_template_id, "
                        + "(created_at = ?::timestamptz) AS created_kept, (updated_at = ?::timestamptz) AS updated_kept "
                        + "FROM otak.mails WHERE id = ?", CREATED, UPDATED, mail);
        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        assertEquals("Subject", row.get("subject"));
        assertEquals("Body", row.get("body"));
        assertEquals("TR", row.get("language"));
        assertEquals(org, row.get("organization_id"));
        assertEquals(relation, row.get("relation_id"));
        assertNull(row.get("mail_template_id"));
        assertEquals(true, row.get("created_kept"));
        assertEquals(true, row.get("updated_kept"));
        assertEquals(mail, jdbc.queryForObject("SELECT mail_id FROM otak.outreaches WHERE id = ?", UUID.class, outreach));
    }

    @Test
    void migrate_mailCopiedFromAMailTemplate_keepsTheTemplateLink() {
        UUID org = organization();
        UUID relation = relation(org);
        UUID template = mailTemplate(org, "Intro", "NL", "Hello", "Body");
        UUID mail = mailDocument(org, relation, template, "Hello", "Body", "NL");
        outreach(org, relation, mail, "DRAFT");

        migrateSplit();

        assertEquals(template, jdbc.queryForObject("SELECT mail_template_id FROM otak.mails WHERE id = ?", UUID.class, mail));
    }

    @Test
    void migrate_mailWhoseTemplateWasAFileTemplate_hasNoTemplateLink() {
        UUID org = organization();
        UUID relation = relation(org);
        UUID file = fileTemplate(org, "Nda");
        UUID mail = mailDocument(org, relation, file, "Hello", "Body", "NL");
        outreach(org, relation, mail, "DRAFT");

        migrateSplit();

        assertNull(jdbc.queryForObject("SELECT mail_template_id FROM otak.mails WHERE id = ?", UUID.class, mail));
    }

    @Test
    void migrate_attachments_followTheOutreachToItsMail() {
        UUID org = organization();
        UUID relation = relation(org);
        UUID mail = mailDocument(org, relation, null, "S", "B", "NL");
        UUID outreach = outreach(org, relation, mail, "DRAFT");
        UUID first = fileDocument(org, relation, null);
        UUID second = fileDocument(org, relation, null);
        attach(outreach, first, relation);
        attach(outreach, second, relation);

        migrateSplit();

        assertEquals(2L, scratchCount("SELECT count(*) FROM otak.mail_attachments WHERE mail_id = ? AND relation_id = ?", mail, relation));
        assertEquals(1L, scratchCount("SELECT count(*) FROM otak.mail_attachments WHERE mail_id = ? AND document_id = ?", mail, first));
        assertEquals(0L, scratchCount("SELECT count(*) FROM information_schema.tables "
                + "WHERE table_schema = 'otak' AND table_name = 'outreach_attachments'"));
    }

    @Test
    void migrate_mailDocumentsNoOutreachUses_areDeletedAndFileDocumentsKept() {
        UUID org = organization();
        UUID relation = relation(org);
        UUID unused = mailDocument(org, relation, null, "S", "B", "NL");
        UUID file = fileDocument(org, relation, null);

        migrateSplit();

        assertEquals(0L, scratchCount("SELECT count(*) FROM otak.mails WHERE id = ?", unused));
        assertEquals(0L, scratchCount("SELECT count(*) FROM otak.documents WHERE id = ?", unused));
        assertEquals(1L, scratchCount("SELECT count(*) FROM otak.documents WHERE id = ?", file));
    }

    @Test
    void migrate_outreachStatusAndSentFields_areKept() {
        UUID org = organization();
        UUID relation = relation(org);
        UUID outreach = outreach(org, relation, mailDocument(org, relation, null, "S", "B", "NL"), "SENT");

        migrateSplit();

        assertEquals(1L, scratchCount("SELECT count(*) FROM otak.outreaches WHERE id = ? AND status = 'SENT' "
                + "AND sent_to = 'a@example.com' AND sent_at IS NOT NULL", outreach));
    }

    @Test
    void migrate_documentTables_loseSubjectBodyAndTheMailType() {
        migrateSplit();

        assertEquals(0L, scratchCount("SELECT count(*) FROM information_schema.columns WHERE table_schema = 'otak' "
                + "AND table_name IN ('documents', 'document_templates') AND column_name IN ('subject', 'body')"));
        assertEquals(0L, scratchCount("SELECT count(*) FROM pg_enum e JOIN pg_type t ON t.oid = e.enumtypid "
                + "WHERE t.typname = 'otak_document_type_enum' AND e.enumlabel = 'MAIL'"));
        assertEquals(7L, scratchCount("SELECT count(*) FROM pg_enum e JOIN pg_type t ON t.oid = e.enumtypid "
                + "WHERE t.typname = 'otak_document_type_enum'"));
        assertEquals(0L, scratchCount("SELECT count(*) FROM pg_type WHERE typname = 'otak_document_type_enum_old'"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO otak.document_templates (name, type, language, organization_id) "
                        + "VALUES ('M', 'MAIL'::otak_document_type_enum, 'NL'::otak_document_language_enum, ?)", organization()));
    }

    @Test
    void migrate_outreachMailColumn_isRenamedAndOldConstraintsAreGone() {
        migrateSplit();

        assertEquals(1L, scratchCount("SELECT count(*) FROM information_schema.columns WHERE table_schema = 'otak' "
                + "AND table_name = 'outreaches' AND column_name = 'mail_id'"));
        assertEquals(0L, scratchCount("SELECT count(*) FROM information_schema.columns WHERE table_schema = 'otak' "
                + "AND table_name = 'outreaches' AND column_name = 'mail_document_id'"));
        assertEquals(0L, scratchCount("SELECT count(*) FROM pg_constraint WHERE conname IN "
                + "('outreaches_mail_document_id_uk', 'outreaches_mail_document_id_relation_id_fk')"));
    }

    @Test
    void migrate_emptyDatabase_succeeds() {
        migrateSplit();

        assertEquals(0L, scratchCount("SELECT count(*) FROM otak.mails"));
        assertEquals(0L, scratchCount("SELECT count(*) FROM otak.mail_templates"));
    }

    @Test
    void channels_migrateKeepsMailOutreachesAndAddsTheTypes() {
        UUID org = organization();
        UUID relation = relation(org);
        UUID outreach = outreach(org, relation, mailDocument(org, relation, null, "S", "B", "NL"), "DRAFT");

        migrateChannels();

        assertEquals(1L, scratchCount("SELECT count(*) FROM otak.outreaches WHERE id = ? AND type = 'MAIL' AND mail_id IS NOT NULL", outreach));
        assertEquals(3L, scratchCount("SELECT count(*) FROM pg_enum e JOIN pg_type t ON t.oid = e.enumtypid "
                + "WHERE t.typname = 'otak_outreach_type_enum'"));
    }

    private void insertChannelOutreach(UUID org, UUID relation, String type, UUID mail, UUID sender) {
        jdbc.update("INSERT INTO otak.outreaches (title, type, organization_id, relation_id, mail_id, mail_sender_id) "
                + "VALUES ('T', ?::otak_outreach_type_enum, ?, ?, ?, ?)", type, org, relation, mail, sender);
    }

    private UUID mailSender(UUID org) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO otak.mail_senders (id, mail_address, added_by, type, organization_id) "
                + "VALUES (?, ?, ?, 'PERSONAL'::otak_email_type_enum, ?)", id, "s" + id + "@example.com", UUID.randomUUID(), org);
        return id;
    }

    private UUID newMail(UUID org, UUID relation) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO otak.mails (id, subject, body, language, organization_id, relation_id) "
                + "VALUES (?, 'S', 'B', 'NL'::otak_document_language_enum, ?, ?)", id, org, relation);
        return id;
    }

    @Test
    void channels_mailOutreachWithoutMail_isRejected() {
        migrateChannels();
        UUID org = organization();
        UUID relation = relation(org);

        assertThrows(DataIntegrityViolationException.class, () -> insertChannelOutreach(org, relation, "MAIL", null, null));
    }

    @Test
    void channels_callWithAMail_isRejected() {
        migrateChannels();
        UUID org = organization();
        UUID relation = relation(org);
        UUID mail = newMail(org, relation);

        assertThrows(DataIntegrityViolationException.class, () -> insertChannelOutreach(org, relation, "CALL", mail, null));
    }

    @Test
    void channels_whatsappWithAMailSender_isRejected() {
        migrateChannels();
        UUID org = organization();
        UUID relation = relation(org);
        UUID sender = mailSender(org);

        assertThrows(DataIntegrityViolationException.class, () -> insertChannelOutreach(org, relation, "WHATSAPP", null, sender));
    }

    @Test
    void channels_callAndWhatsappWithoutMailOrSender_areAccepted() {
        migrateChannels();
        UUID org = organization();
        UUID relation = relation(org);

        insertChannelOutreach(org, relation, "CALL", null, null);
        insertChannelOutreach(org, relation, "CALL", null, null);
        insertChannelOutreach(org, relation, "WHATSAPP", null, null);

        assertEquals(3L, scratchCount("SELECT count(*) FROM otak.outreaches WHERE relation_id = ? AND mail_id IS NULL", relation));
    }

    @Test
    void channels_mailOutreachWithMailAndSender_isAccepted() {
        migrateChannels();
        UUID org = organization();
        UUID relation = relation(org);

        insertChannelOutreach(org, relation, "MAIL", newMail(org, relation), mailSender(org));

        assertEquals(1L, scratchCount("SELECT count(*) FROM otak.outreaches WHERE relation_id = ? AND type = 'MAIL'", relation));
    }
}
