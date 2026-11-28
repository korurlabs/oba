package com.korur.oba.shrine.azureconnection.integration;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.support.ObaIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the constraints and delete rules of the two Azure connection tables against the migrated
 * PostgreSQL schema, with plain SQL so no application code can hide a gap in the schema.
 */
class AzureConnectionMigrationIT extends ObaIntegrationTest {

    private static final String INSERT_CONNECTION = """
            INSERT INTO shrine.azure_connections (id, name, tenant_id, client_id, subject, organization_id, created_by_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_SUBSCRIPTION = """
            INSERT INTO shrine.azure_connection_subscriptions (subscription_id, display_name, state, connection_id)
            VALUES (?, 'Production', 'Enabled', ?)
            """;

    private UUID insertConnection(UUID organizationId, String name, UUID tenantId, UUID clientId, String subject,
                                  UUID createdById) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(INSERT_CONNECTION, id, name, tenantId, clientId, subject, organizationId, createdById);
        return id;
    }

    private UUID insertConnection(UUID organizationId, String name, UUID tenantId, UUID clientId) {
        return insertConnection(organizationId, name, tenantId, clientId, "subject-" + UUID.randomUUID(), null);
    }

    @Test
    void insert_minimalRow_defaultsStatusToPendingAndLeavesResultsEmpty() {
        Organization organization = createOrganization();

        UUID id = insertConnection(organization.getId(), "Production", UUID.randomUUID(), null);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status::text FROM shrine.azure_connections WHERE id = ?", String.class, id))
                .isEqualTo("PENDING");
        assertThat(count("SELECT count(*) FROM shrine.azure_connections WHERE id = ? "
                + "AND last_verified_at IS NULL AND last_error IS NULL", id)).isEqualTo(1L);
    }

    @Test
    void insert_unknownStatus_isRefused() {
        Organization organization = createOrganization();
        UUID id = insertConnection(organization.getId(), "Production", UUID.randomUUID(), null);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE shrine.azure_connections SET status = 'BROKEN' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void insert_sameNameInOneOrganization_violatesNameUniqueness() {
        Organization organization = createOrganization();
        insertConnection(organization.getId(), "Production", UUID.randomUUID(), null);

        assertThatThrownBy(() -> insertConnection(organization.getId(), "Production", UUID.randomUUID(), null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("azure_connections_organization_id_name_uk");
    }

    @Test
    void insert_sameNameInAnotherOrganization_succeeds() {
        insertConnection(createOrganization().getId(), "Production", UUID.randomUUID(), null);

        UUID id = insertConnection(createOrganization().getId(), "Production", UUID.randomUUID(), null);

        assertThat(id).isNotNull();
    }

    @Test
    void insert_sameTenantAndClientInOneOrganization_violatesIdentityUniqueness() {
        Organization organization = createOrganization();
        UUID tenantId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        insertConnection(organization.getId(), "First", tenantId, clientId);

        assertThatThrownBy(() -> insertConnection(organization.getId(), "Second", tenantId, clientId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("azure_connections_organization_id_tenant_id_client_id_uk");
    }

    @Test
    void insert_sameTenantWithoutClientIdTwice_succeedsBecauseNullsAreDistinct() {
        Organization organization = createOrganization();
        UUID tenantId = UUID.randomUUID();
        insertConnection(organization.getId(), "First", tenantId, null);

        insertConnection(organization.getId(), "Second", tenantId, null);

        assertThat(count("SELECT count(*) FROM shrine.azure_connections WHERE organization_id = ? AND tenant_id = ?",
                organization.getId(), tenantId)).isEqualTo(2L);
    }

    @Test
    void insert_sameTenantAndClientInAnotherOrganization_succeeds() {
        UUID tenantId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        insertConnection(createOrganization().getId(), "First", tenantId, clientId);

        UUID id = insertConnection(createOrganization().getId(), "First", tenantId, clientId);

        assertThat(id).isNotNull();
    }

    @Test
    void insert_sameSubject_violatesSubjectUniqueness() {
        String subject = "subject-" + UUID.randomUUID();
        insertConnection(createOrganization().getId(), "First", UUID.randomUUID(), null, subject, null);

        assertThatThrownBy(() -> insertConnection(createOrganization().getId(), "Second", UUID.randomUUID(), null,
                subject, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("azure_connections_subject_uk");
    }

    @Test
    void insert_unknownOrganization_violatesForeignKey() {
        assertThatThrownBy(() -> insertConnection(UUID.randomUUID(), "Production", UUID.randomUUID(), null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("azure_connections_organization_id_fk");
    }

    @Test
    void insertSubscription_sameSubscriptionTwiceForOneConnection_violatesUniqueness() {
        UUID connectionId = insertConnection(createOrganization().getId(), "Production", UUID.randomUUID(), null);
        UUID subscriptionId = UUID.randomUUID();
        jdbcTemplate.update(INSERT_SUBSCRIPTION, subscriptionId, connectionId);

        assertThatThrownBy(() -> jdbcTemplate.update(INSERT_SUBSCRIPTION, subscriptionId, connectionId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("azure_connection_subscriptions_connection_id_subscription_id_uk");
    }

    @Test
    void insertSubscription_sameSubscriptionForTwoConnections_succeeds() {
        UUID organizationId = createOrganization().getId();
        UUID first = insertConnection(organizationId, "First", UUID.randomUUID(), null);
        UUID second = insertConnection(organizationId, "Second", UUID.randomUUID(), null);
        UUID subscriptionId = UUID.randomUUID();

        jdbcTemplate.update(INSERT_SUBSCRIPTION, subscriptionId, first);
        jdbcTemplate.update(INSERT_SUBSCRIPTION, subscriptionId, second);

        assertThat(count("SELECT count(*) FROM shrine.azure_connection_subscriptions WHERE subscription_id = ?",
                subscriptionId)).isEqualTo(2L);
    }

    @Test
    void insertSubscription_unknownConnection_violatesForeignKey() {
        assertThatThrownBy(() -> jdbcTemplate.update(INSERT_SUBSCRIPTION, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("azure_connection_subscriptions_connection_id_fk");
    }

    @Test
    void deleteConnection_removesItsSubscriptionsOnly() {
        UUID organizationId = createOrganization().getId();
        UUID deleted = insertConnection(organizationId, "Deleted", UUID.randomUUID(), null);
        UUID kept = insertConnection(organizationId, "Kept", UUID.randomUUID(), null);
        jdbcTemplate.update(INSERT_SUBSCRIPTION, UUID.randomUUID(), deleted);
        jdbcTemplate.update(INSERT_SUBSCRIPTION, UUID.randomUUID(), kept);

        jdbcTemplate.update("DELETE FROM shrine.azure_connections WHERE id = ?", deleted);

        assertThat(count("SELECT count(*) FROM shrine.azure_connection_subscriptions WHERE connection_id = ?",
                deleted)).isZero();
        assertThat(count("SELECT count(*) FROM shrine.azure_connection_subscriptions WHERE connection_id = ?",
                kept)).isEqualTo(1L);
    }

    @Test
    void deleteOrganization_removesItsConnectionsAndSubscriptions() {
        Organization organization = createOrganization();
        UUID connectionId = insertConnection(organization.getId(), "Production", UUID.randomUUID(), null);
        jdbcTemplate.update(INSERT_SUBSCRIPTION, UUID.randomUUID(), connectionId);

        jdbcTemplate.update("DELETE FROM shrine.organizations WHERE id = ?", organization.getId());

        assertThat(count("SELECT count(*) FROM shrine.azure_connections WHERE id = ?", connectionId)).isZero();
        assertThat(count("SELECT count(*) FROM shrine.azure_connection_subscriptions WHERE connection_id = ?",
                connectionId)).isZero();
    }

    @Test
    void deleteCreator_keepsTheConnectionAndClearsCreatedBy() {
        User creator = createUser();
        UUID connectionId = insertConnection(createOrganization().getId(), "Production", UUID.randomUUID(), null,
                "subject-" + UUID.randomUUID(), creator.getId());

        jdbcTemplate.update("DELETE FROM shrine.users WHERE id = ?", creator.getId());

        assertThat(count("SELECT count(*) FROM shrine.azure_connections WHERE id = ? AND created_by_id IS NULL",
                connectionId)).isEqualTo(1L);
    }
}
