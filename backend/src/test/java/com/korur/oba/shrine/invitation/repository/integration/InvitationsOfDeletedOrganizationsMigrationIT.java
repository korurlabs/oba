package com.korur.oba.shrine.invitation.repository.integration;

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
 * Proves the data conversion of V1_2_133 (pending invitations of deleted organizations are revoked)
 * on a scratch database: the schema is migrated to 1.2.132, invitations are seeded in a live and in a
 * soft-deleted organization, and the migration is applied.
 *
 * Only a pending invitation of a deleted organization may change, and it becomes REVOKED, the status
 * deleting an organization gives its pending invitations. A pending invitation of a live organization
 * and a decided invitation of a deleted one keep their status. Every test migrates its own copy of the
 * schema at 1.2.132 (see ScratchDatabases), so the database the application runs on is never touched.
 */
class InvitationsOfDeletedOrganizationsMigrationIT extends ObaIntegrationTest {

    private static final String BEFORE = "1.2.132";
    private static final String AFTER = "1.2.133";

    private static ScratchDatabases databases;

    private String scratchName;
    private JdbcTemplate jdbc;
    private UUID inviter;
    private UUID liveOrganization;
    private UUID deletedOrganization;

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
        inviter = UUID.randomUUID();
        jdbc.update("INSERT INTO shrine.users (id, email) VALUES (?, ?)", inviter, inviter + "@example.test");
        liveOrganization = organization(false);
        deletedOrganization = organization(true);
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

    // ---- seeding helpers (the schema as of 1.2.132) ----

    private UUID organization(boolean deleted) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO shrine.organizations (id, name, status, deleted) VALUES (?, ?, 'ACTIVE', ?)",
                id, "org " + id, deleted);
        return id;
    }

    private UUID invitation(UUID organization, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO shrine.user_invitations (id, organization_id, email, role, status, invited_by_user_id)
                VALUES (?, ?, ?, 'MEMBER'::shrine_organization_member_role_enum,
                        ?::shrine_identity_user_invitation_status_enum, ?)
                """, id, organization, id + "@example.test", status, inviter);
        return id;
    }

    private String statusOf(UUID invitation) {
        return jdbc.queryForObject("SELECT status::text FROM shrine.user_invitations WHERE id = ?",
                String.class, invitation);
    }

    // ---- conversion ----

    @Test
    void migrate_pendingInvitationOfDeletedOrganization_isRevoked() {
        UUID pending = invitation(deletedOrganization, "PENDING");

        migrate();

        assertEquals("REVOKED", statusOf(pending));
    }

    @Test
    void migrate_pendingInvitationOfLiveOrganization_staysPending() {
        UUID pending = invitation(liveOrganization, "PENDING");

        migrate();

        assertEquals("PENDING", statusOf(pending));
    }

    @Test
    void migrate_decidedInvitationsOfDeletedOrganization_keepTheirStatus() {
        UUID accepted = invitation(deletedOrganization, "ACCEPTED");
        UUID declined = invitation(deletedOrganization, "DECLINED");
        UUID expired = invitation(deletedOrganization, "EXPIRED");

        migrate();

        assertEquals("ACCEPTED", statusOf(accepted));
        assertEquals("DECLINED", statusOf(declined));
        assertEquals("EXPIRED", statusOf(expired));
    }
}
