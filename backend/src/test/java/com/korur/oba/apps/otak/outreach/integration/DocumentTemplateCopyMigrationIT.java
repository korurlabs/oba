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

// 3. Java SDK
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.support.ObaIntegrationTest;
import com.korur.oba.support.ScratchDatabases;

/**
 * Proves the copy of V1_2_119 on a scratch database: the schema is migrated to 1.2.118,
 * outreaches of V1_2_110 are seeded, and V1_2_119 is applied. Every outreach must survive as a MAIL template
 * in Dutch with its id, content and timestamps, and the new outreaches table must start empty.
 */
class DocumentTemplateCopyMigrationIT extends ObaIntegrationTest {

    private static final String BASELINE = "1.2.118";

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

    private UUID outreach(UUID organization, String name, String subject, String body, String createdAt, String updatedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO otak.outreaches (id, type, name, subject, body, organization_id, created_at, updated_at) "
                + "VALUES (?, 'MAIL'::otak_outreach_type_enum, ?, ?, ?, ?, ?::timestamptz, ?::timestamptz)",
                id, name, subject, body, organization, createdAt, updatedAt);
        return id;
    }

    @Test
    void migrate_phaseOneOutreaches_becomeDutchMailTemplatesWithTheSameIdAndContent() {
        UUID orgA = organization();
        UUID orgB = organization();
        UUID first = outreach(orgA, "Intro", "Hello", "Body one", "2026-10-01T08:00:00Z", "2026-10-02T09:30:00Z");
        UUID second = outreach(orgB, "Intro", "Hallo", "Body two", "2026-10-03T08:00:00Z", "2026-10-03T08:00:00Z");

        databases.migrate(scratchName, "1.2.119");

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, name, type::text AS type, language::text AS language, subject, body, "
                        + "html_blob_url, pdf_blob_url, organization_id, "
                        + "(created_at = '2026-10-01T08:00:00Z'::timestamptz) AS created_kept, "
                        + "(updated_at = '2026-10-02T09:30:00Z'::timestamptz) AS updated_kept "
                        + "FROM otak.document_templates WHERE id = ?", first);
        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        assertEquals("Intro", row.get("name"));
        assertEquals("MAIL", row.get("type"));
        assertEquals("NL", row.get("language"));
        assertEquals("Hello", row.get("subject"));
        assertEquals("Body one", row.get("body"));
        assertEquals(null, row.get("html_blob_url"));
        assertEquals(null, row.get("pdf_blob_url"));
        assertEquals(orgA, row.get("organization_id"));
        assertEquals(true, row.get("created_kept"));
        assertEquals(true, row.get("updated_kept"));
        assertEquals(orgB, jdbc.queryForObject(
                "SELECT organization_id FROM otak.document_templates WHERE id = ?", UUID.class, second));
        assertEquals(2L, jdbc.queryForObject("SELECT count(*) FROM otak.document_templates", Long.class));
    }

    @Test
    void migrate_phaseOneOutreaches_newOutreachesTableStartsEmptyAndHasTheNewShape() {
        outreach(organization(), "Intro", "Hello", "Body", "2026-10-01T08:00:00Z", "2026-10-01T08:00:00Z");

        databases.migrate(scratchName, "1.2.119");

        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM otak.outreaches", Long.class));
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM information_schema.columns "
                + "WHERE table_schema = 'otak' AND table_name = 'outreaches' AND column_name = 'title'", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM information_schema.columns "
                + "WHERE table_schema = 'otak' AND table_name = 'outreaches' AND column_name = 'name'", Long.class));
    }

    @Test
    void migrate_noOutreaches_leavesNoTemplates() {
        databases.migrate(scratchName, "1.2.119");

        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM otak.document_templates", Long.class));
    }

    @Test
    void migrate_twoOutreachesInOneOrganization_areBothCopiedWithoutNameCollision() {
        UUID org = organization();
        outreach(org, "Intro", "Hello", "Body", "2026-10-01T08:00:00Z", "2026-10-01T08:00:00Z");
        outreach(org, "Follow up", "Hello", "Body", "2026-10-01T08:00:00Z", "2026-10-01T08:00:00Z");

        databases.migrate(scratchName, "1.2.119");

        assertEquals(2L, jdbc.queryForObject(
                "SELECT count(*) FROM otak.document_templates WHERE organization_id = ?", Long.class, org));
    }
}
