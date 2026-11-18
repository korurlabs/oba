package com.korur.oba.apps.otak.outreach.integration;

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
import static org.junit.jupiter.api.Assertions.assertNull;

// 3. Java SDK
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.support.ObaIntegrationTest;
import com.korur.oba.support.ScratchDatabases;

/**
 * Proves V1_2_124 and V1_2_125 on a scratch database: the schema is migrated to 1.2.123, documents
 * with their mail links are seeded, and the migrations are applied. Every PDF and HTML file of a
 * document must become a RELATION attachment of its relation, every link to a document with a PDF
 * a MAIL attachment of its mail, and the two document tables must be gone while the templates stay.
 */
class OtakDocumentsToAttachmentsMigrationIT extends ObaIntegrationTest {

    private static final String BASELINE = "1.2.123";
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

    private UUID document(UUID organization, UUID relation, String name, String htmlBlob, String pdfBlob) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO otak.documents (id, name, type, language, html_blob_url, pdf_blob_url, organization_id, relation_id, created_at, updated_at) "
                + "VALUES (?, ?, 'NDA'::otak_document_type_enum, 'EN'::otak_document_language_enum, ?, ?, ?, ?, ?::timestamptz, ?::timestamptz)",
                id, name, htmlBlob, pdfBlob, organization, relation, CREATED, UPDATED);
        return id;
    }

    private UUID mail(UUID organization, UUID relation) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO otak.mails (id, subject, body, language, organization_id, relation_id) "
                + "VALUES (?, 'Subject', 'Body', 'EN'::otak_document_language_enum, ?, ?)", id, organization, relation);
        return id;
    }

    private void link(UUID mail, UUID document, UUID relation) {
        jdbc.update("INSERT INTO otak.mail_attachments (mail_id, document_id, relation_id) VALUES (?, ?, ?)",
                mail, document, relation);
    }

    private UUID mailTemplate(UUID organization) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO otak.mail_templates (id, name, language, subject, body, organization_id) "
                + "VALUES (?, ?, 'NL'::otak_document_language_enum, 'S', 'B', ?)", id, "Intro " + id, organization);
        return id;
    }

    private UUID documentTemplate(UUID organization) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO otak.document_templates (id, name, type, language, html_blob_url, pdf_blob_url, organization_id) "
                + "VALUES (?, ?, 'NDA'::otak_document_type_enum, 'EN'::otak_document_language_enum, 'h', 'p', ?)",
                id, "Nda " + id, organization);
        return id;
    }

    private long scratchCount(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private void migrate() {
        databases.migrate(scratchName, "1.2.125");
    }

    @Test
    void migrate_documentFiles_becomeRelationAttachmentsOfTheirRelation() {
        UUID org = organization();
        UUID relation = relation(org);
        document(org, relation, "Voorstel", "blob/voorstel.html", "blob/voorstel.pdf");

        migrate();

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT url, file_name, content_type, file_size, file_hash, width, height, "
                        + "(created_at = ?::timestamptz) AS created_kept, (updated_at = ?::timestamptz) AS updated_kept "
                        + "FROM shrine.attachments WHERE entity_type = 'RELATION'::shrine_storage_entity_type_enum "
                        + "AND entity_id = ? ORDER BY file_name", CREATED, UPDATED, relation);
        assertEquals(2, rows.size());
        Map<String, Object> html = rows.get(0);
        assertEquals("Voorstel.html", html.get("file_name"));
        assertEquals("text/html", html.get("content_type"));
        assertEquals("blob/voorstel.html", html.get("url"));
        Map<String, Object> pdf = rows.get(1);
        assertEquals("Voorstel.pdf", pdf.get("file_name"));
        assertEquals("application/pdf", pdf.get("content_type"));
        assertEquals("blob/voorstel.pdf", pdf.get("url"));
        for (Map<String, Object> row : rows) {
            assertNull(row.get("file_size"));
            assertNull(row.get("file_hash"));
            assertNull(row.get("width"));
            assertNull(row.get("height"));
            assertEquals(true, row.get("created_kept"));
            assertEquals(true, row.get("updated_kept"));
        }
    }

    @Test
    void migrate_documentWithOnlyOneFile_becomesOneAttachment() {
        UUID org = organization();
        UUID relation = relation(org);
        document(org, relation, "Alleen", null, "blob/alleen.pdf");
        document(org, relation, "Html", "blob/html.html", null);

        migrate();

        assertEquals(1L, scratchCount("SELECT count(*) FROM shrine.attachments WHERE entity_type = 'RELATION'::shrine_storage_entity_type_enum "
                + "AND entity_id = ? AND file_name = 'Alleen.pdf'", relation));
        assertEquals(0L, scratchCount("SELECT count(*) FROM shrine.attachments WHERE file_name = 'Alleen.html'"));
        assertEquals(1L, scratchCount("SELECT count(*) FROM shrine.attachments WHERE entity_type = 'RELATION'::shrine_storage_entity_type_enum "
                + "AND entity_id = ? AND file_name = 'Html.html'", relation));
        assertEquals(2L, scratchCount("SELECT count(*) FROM shrine.attachments"));
    }

    @Test
    void migrate_mailLinks_becomeMailAttachmentsOnThePdfBlob() {
        UUID org = organization();
        UUID relation = relation(org);
        UUID document = document(org, relation, "Voorstel", "blob/voorstel.html", "blob/voorstel.pdf");
        UUID mail = mail(org, relation);
        link(mail, document, relation);

        migrate();

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT url, file_name, content_type FROM shrine.attachments "
                        + "WHERE entity_type = 'MAIL'::shrine_storage_entity_type_enum AND entity_id = ?", mail);
        assertEquals(1, rows.size());
        assertEquals("blob/voorstel.pdf", rows.get(0).get("url"));
        assertEquals("Voorstel.pdf", rows.get(0).get("file_name"));
        assertEquals("application/pdf", rows.get(0).get("content_type"));
        assertEquals(2L, scratchCount("SELECT count(*) FROM shrine.attachments WHERE entity_type = 'RELATION'::shrine_storage_entity_type_enum AND entity_id = ?", relation));
    }

    @Test
    void migrate_linkOfDocumentWithoutPdf_becomesNoMailAttachment() {
        UUID org = organization();
        UUID relation = relation(org);
        UUID document = document(org, relation, "Html", "blob/html.html", null);
        UUID mail = mail(org, relation);
        link(mail, document, relation);

        migrate();

        assertEquals(0L, scratchCount("SELECT count(*) FROM shrine.attachments WHERE entity_type = 'MAIL'::shrine_storage_entity_type_enum"));
    }

    @Test
    void migrate_documentOnSeveralMails_becomesOneMailAttachmentPerMailSharingTheBlob() {
        UUID org = organization();
        UUID relation = relation(org);
        UUID document = document(org, relation, "Voorstel", null, "blob/voorstel.pdf");
        UUID first = mail(org, relation);
        UUID second = mail(org, relation);
        link(first, document, relation);
        link(second, document, relation);

        migrate();

        assertEquals(1L, scratchCount("SELECT count(*) FROM shrine.attachments WHERE entity_type = 'MAIL'::shrine_storage_entity_type_enum AND entity_id = ?", first));
        assertEquals(1L, scratchCount("SELECT count(*) FROM shrine.attachments WHERE entity_type = 'MAIL'::shrine_storage_entity_type_enum AND entity_id = ?", second));
        assertEquals(3L, scratchCount("SELECT count(*) FROM shrine.attachments WHERE url = 'blob/voorstel.pdf'"));
    }

    @Test
    void migrate_documentsOfOtherRelations_stayWithTheirOwnRelation() {
        UUID org = organization();
        UUID one = relation(org);
        UUID two = relation(org);
        document(org, one, "Een", null, "blob/een.pdf");
        document(org, two, "Twee", null, "blob/twee.pdf");

        migrate();

        assertEquals("Een.pdf", jdbc.queryForObject("SELECT file_name FROM shrine.attachments WHERE entity_id = ?", String.class, one));
        assertEquals("Twee.pdf", jdbc.queryForObject("SELECT file_name FROM shrine.attachments WHERE entity_id = ?", String.class, two));
    }

    @Test
    void migrate_dropsTheDocumentTablesAndKeepsTemplatesAndEnums() {
        UUID org = organization();
        UUID relation = relation(org);
        UUID template = documentTemplate(org);
        UUID mailTemplate = mailTemplate(org);
        document(org, relation, "Voorstel", null, "blob/voorstel.pdf");

        migrate();

        assertEquals(0L, scratchCount("SELECT count(*) FROM information_schema.tables "
                + "WHERE table_schema = 'otak' AND table_name IN ('documents', 'mail_attachments')"));
        assertEquals(1L, scratchCount("SELECT count(*) FROM otak.document_templates WHERE id = ?", template));
        assertEquals(1L, scratchCount("SELECT count(*) FROM otak.mail_templates WHERE id = ?", mailTemplate));
        assertEquals(2L, scratchCount("SELECT count(*) FROM pg_type WHERE typname IN ('otak_document_type_enum', 'otak_document_language_enum')"));
    }

    @Test
    void migrate_storageEntityTypeEnum_gainsMailAndRelation() {
        migrate();

        assertEquals(2L, scratchCount("SELECT count(*) FROM pg_enum e JOIN pg_type t ON t.oid = e.enumtypid "
                + "WHERE t.typname = 'shrine_storage_entity_type_enum' AND e.enumlabel IN ('MAIL', 'RELATION')"));
    }
}
