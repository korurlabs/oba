package com.korur.oba.shrine.organization.repository.integration;

// 1. Framework-level
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// 2. Third-party
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.organization.repository.OrganizationMemberRepository;
import com.korur.oba.support.ObaIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs the organization member count queries against the Flyway-migrated
 * PostgreSQL schema, because the binding of the role and status
 * enum parameters to PostgreSQL enum columns cannot be checked on a mock or an
 * in-memory database.
 *
 * Each test creates its own organization, users and memberships, asserts only
 * on those, and removes them in the cleanup, so a database shared with a
 * running backend is not disturbed.
 */
class OrganizationMemberRepositoryIT extends ObaIntegrationTest {

    @Autowired
    private OrganizationMemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> organizationIds = new ArrayList<>();
    private final List<UUID> userIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID id : organizationIds) {
            jdbc.update("DELETE FROM shrine.organization_members WHERE organization_id = ?", id);
        }
        for (UUID id : userIds) {
            jdbc.update("DELETE FROM shrine.users WHERE id = ?", id);
        }
        for (UUID id : organizationIds) {
            jdbc.update("DELETE FROM shrine.organizations WHERE id = ?", id);
        }
    }

    private UUID insertOrganization() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO shrine.organizations (id, name, status)
                VALUES (?, 'itest organization', 'ACTIVE'::shrine_organization_status_enum)
                """, id);
        organizationIds.add(id);
        return id;
    }

    private void insertMember(UUID organizationId, String role, String status, boolean deleted) {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO shrine.users (id, email) VALUES (?, ?)", userId, "itest-" + userId + "@example.invalid");
        userIds.add(userId);
        jdbc.update("""
                INSERT INTO shrine.organization_members (id, organization_id, user_id, role, status, deleted)
                VALUES (?, ?, ?, ?::shrine_organization_member_role_enum, ?::shrine_organization_member_status_enum, ?)
                """, UUID.randomUUID(), organizationId, userId, role, status, deleted);
    }

    @Test
    void countActiveOwners_mixedMemberships_countsOnlyActiveOwners() {
        UUID organization = insertOrganization();
        insertMember(organization, "OWNER", "ACTIVE", false);
        insertMember(organization, "OWNER", "ACTIVE", false);
        insertMember(organization, "OWNER", "DISABLED", false);
        insertMember(organization, "OWNER", "ACTIVE", true);
        insertMember(organization, "ADMIN", "ACTIVE", false);
        insertMember(organization, "MEMBER", "ACTIVE", false);

        assertEquals(2, memberRepository.countActiveOwners(organization));
    }

    @Test
    void countActiveOwners_ownersInOtherOrganization_leavesThemOut() {
        UUID organization = insertOrganization();
        UUID otherOrganization = insertOrganization();
        insertMember(otherOrganization, "OWNER", "ACTIVE", false);

        assertEquals(0, memberRepository.countActiveOwners(organization));
        assertEquals(1, memberRepository.countActiveOwners(otherOrganization));
    }
}
