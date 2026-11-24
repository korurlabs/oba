package com.korur.oba.apps.kazan.chat.repository.integration;

// 1. Framework-level
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 2. Third-party
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

// 4. Internal
import com.korur.oba.apps.kazan.chat.repository.ChatChannelRepository;
import com.korur.oba.support.ObaIntegrationTest;

/**
 * Runs the channel slug queries and the initial channel insert against the Flyway-migrated
 * PostgreSQL schema, because the native insert and the partial unique index on
 * the live slugs of an organization cannot be checked on a mock.
 *
 * Each test creates its own organization and removes it in the cleanup, which removes its
 * channels with it, so a database shared with a running backend is not disturbed.
 */
class ChatChannelRepositoryIT extends ObaIntegrationTest {

    @Autowired
    private ChatChannelRepository chatChannelRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<UUID> organizationIds = new ArrayList<>();

    /**
     * Runs a modifying repository call in its own transaction, as the services do.
     */
    private <T> T inTransaction(Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    @AfterEach
    void cleanUp() {
        for (UUID id : organizationIds) {
            jdbc.update("DELETE FROM shrine.organizations WHERE id = ?", id);
        }
    }

    private UUID insertOrganization() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO shrine.organizations (id, name, status) VALUES (?, ?, 'ACTIVE')",
                id, "itest " + id);
        organizationIds.add(id);
        return id;
    }

    private void insertChannel(UUID organizationId, String name, String slug, boolean deleted) {
        jdbc.update("""
                INSERT INTO kazan.chat_channels (id, name, slug, type, purpose, visibility, organization_id, deleted)
                VALUES (?, ?, ?, 'TEXT', 'GENERAL', 'PUBLIC', ?, ?)
                """, UUID.randomUUID(), name, slug, organizationId, deleted);
    }

    @Test
    void insertInitialChannelsIfAbsent_emptyOrganization_createsGeneralUpdatesAndPrWithTheirSlugs() {
        UUID organizationId = insertOrganization();

        int created = inTransaction(() -> chatChannelRepository.insertInitialChannelsIfAbsent(organizationId));

        assertEquals(3, created);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT name, slug, purpose::text AS purpose, visibility::text AS visibility
                FROM kazan.chat_channels WHERE organization_id = ? ORDER BY created_at
                """, organizationId);
        assertEquals(List.of("General", "Updates", "PR Share"), rows.stream().map(row -> row.get("name")).toList());
        assertEquals(List.of("general", "updates", "pr"), rows.stream().map(row -> row.get("slug")).toList());
        assertEquals(List.of("GENERAL", "DAILY_UPDATES", "PULL_REQUESTS"),
                rows.stream().map(row -> row.get("purpose")).toList());
        assertTrue(rows.stream().allMatch(row -> "PUBLIC".equals(row.get("visibility"))));
    }

    @Test
    void insertInitialChannelsIfAbsent_calledTwice_createsNothingTheSecondTime() {
        UUID organizationId = insertOrganization();
        inTransaction(() -> chatChannelRepository.insertInitialChannelsIfAbsent(organizationId));

        int second = inTransaction(() -> chatChannelRepository.insertInitialChannelsIfAbsent(organizationId));

        assertEquals(0, second);
    }

    @Test
    void insertInitialChannelsIfAbsent_organizationWithAnyChannel_createsNothing() {
        UUID organizationId = insertOrganization();
        insertChannel(organizationId, "Custom", "custom", false);

        int created = inTransaction(() -> chatChannelRepository.insertInitialChannelsIfAbsent(organizationId));

        assertEquals(0, created);
    }

    @Test
    void existsByOrganizationIdAndSlug_liveChannel_isTrueOnlyInItsOrganization() {
        UUID organizationId = insertOrganization();
        UUID otherOrganizationId = insertOrganization();
        insertChannel(organizationId, "Release", "release", false);

        assertTrue(chatChannelRepository.existsByOrganizationIdAndSlug(organizationId, "release"));
        assertFalse(chatChannelRepository.existsByOrganizationIdAndSlug(otherOrganizationId, "release"));
    }

    @Test
    void existsByOrganizationIdAndSlug_deletedChannel_isFalse() {
        UUID organizationId = insertOrganization();
        insertChannel(organizationId, "Release", "release", true);

        assertFalse(chatChannelRepository.existsByOrganizationIdAndSlug(organizationId, "release"));
    }

    @Test
    void existsByOrganizationIdAndSlugAndIdNot_ownSlug_isFalse() {
        UUID organizationId = insertOrganization();
        UUID channelId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO kazan.chat_channels (id, name, slug, type, purpose, visibility, organization_id)
                VALUES (?, 'Release', 'release', 'TEXT', 'GENERAL', 'PUBLIC', ?)
                """, channelId, organizationId);

        assertFalse(chatChannelRepository.existsByOrganizationIdAndSlugAndIdNot(organizationId, "release", channelId));
        assertTrue(chatChannelRepository.existsByOrganizationIdAndSlugAndIdNot(organizationId, "release",
                UUID.randomUUID()));
    }

    @Test
    void slugUniqueIndex_secondLiveChannelWithTheSlug_isRefusedButADeletedOneIsNot() {
        UUID organizationId = insertOrganization();
        insertChannel(organizationId, "Release", "release", true);
        insertChannel(organizationId, "Release", "release", false);

        assertThrows(org.springframework.dao.DuplicateKeyException.class,
                () -> insertChannel(organizationId, "Release again", "release", false));
    }
}
