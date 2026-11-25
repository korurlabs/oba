package com.korur.oba.apps.otak.campaign.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractTenantIT;

/**
 * The campaign schema refuses rows the application must never write: a scheduled campaign
 * without a planned date, a draft or scheduled campaign with a published date, a duplicate
 * name inside one organization, an unknown channel or status, a text over 20000 characters, a
 * second text in one language, and a relation linked to the campaign of another organization;
 * and deleting a campaign keeps the relations that pointed at it and removes its texts.
 * The tests use SQL only, so they hold independently of the campaign entity.
 */
class CampaignMigrationIT extends AbstractTenantIT {

    private Tenant tenantA;
    private Tenant tenantB;

    @BeforeEach
    void setUpTenants() {
        tenantA = newTenant();
        tenantB = newTenant();
    }

    private UUID campaign(Tenant tenant, String name, String status, String plannedAt, String publishedAt) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO otak.campaigns (id, name, channel, status, planned_at, published_at, "
                + "organization_id) VALUES (?, ?, 'LINKEDIN', ?::otak_campaign_status_enum, ?::timestamptz, "
                + "?::timestamptz, ?)", id, name, status, plannedAt, publishedAt, tenant.organization().getId());
        return id;
    }

    private UUID draft(Tenant tenant, String name) {
        return campaign(tenant, name, "DRAFT", null, null);
    }

    private void content(UUID campaignId, String language, String text) {
        jdbcTemplate.update("INSERT INTO otak.campaign_contents (id, campaign_id, language, text) "
                + "VALUES (?, ?, ?::shrine_document_language_enum, ?)", UUID.randomUUID(), campaignId, language, text);
    }

    private UUID lead(Tenant tenant, UUID campaignId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO otak.relations (id, name, type, stage, campaign_id, organization_id) "
                + "VALUES (?, ?, 'LEAD', 'NEW', ?, ?)", id, "Relation " + uniqueSuffix(), campaignId,
                tenant.organization().getId());
        return id;
    }

    @Test
    void insertCampaign_scheduledWithoutPlannedAt_isRejectedByTheCheckConstraint() {
        assertThrows(DataIntegrityViolationException.class,
                () -> campaign(tenantA, "Scheduled " + uniqueSuffix(), "SCHEDULED", null, null));
    }

    @Test
    void insertCampaign_scheduledWithPlannedAt_isAccepted() {
        campaign(tenantA, "Scheduled " + uniqueSuffix(), "SCHEDULED", "2026-12-01T09:00:00Z", null);

        assertEquals(1L, count("SELECT count(*) FROM otak.campaigns WHERE organization_id = ?",
                tenantA.organization().getId()));
    }

    @Test
    void insertCampaign_draftOrScheduledWithPublishedAt_isRejectedByTheCheckConstraint() {
        assertThrows(DataIntegrityViolationException.class,
                () -> campaign(tenantA, "Draft " + uniqueSuffix(), "DRAFT", null, "2026-11-01T09:00:00Z"));
        assertThrows(DataIntegrityViolationException.class, () -> campaign(tenantA, "Sched " + uniqueSuffix(),
                "SCHEDULED", "2026-12-01T09:00:00Z", "2026-11-01T09:00:00Z"));
    }

    @Test
    void insertCampaign_publishedOrArchivedWithPublishedAtOrNone_isAccepted() {
        campaign(tenantA, "Published " + uniqueSuffix(), "PUBLISHED", null, "2026-11-01T09:00:00Z");
        campaign(tenantA, "Archived " + uniqueSuffix(), "ARCHIVED", null, null);

        assertEquals(2L, count("SELECT count(*) FROM otak.campaigns WHERE organization_id = ?",
                tenantA.organization().getId()));
    }

    @Test
    void insertCampaign_unknownChannelOrStatus_isRejectedByTheEnum() {
        assertThrows(Exception.class, () -> jdbcTemplate.update(
                "INSERT INTO otak.campaigns (id, name, channel, organization_id) "
                        + "VALUES (?, ?, 'FAX'::otak_campaign_channel_enum, ?)",
                UUID.randomUUID(), "Fax " + uniqueSuffix(), tenantA.organization().getId()));
        assertThrows(Exception.class, () -> campaign(tenantA, "Odd " + uniqueSuffix(), "LIVE", null, null));
    }

    @Test
    void insertCampaign_withoutStatus_defaultsToDraft() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO otak.campaigns (id, name, channel, organization_id) "
                + "VALUES (?, ?, 'X', ?)", id, "Default " + uniqueSuffix(), tenantA.organization().getId());

        assertEquals(1L, count("SELECT count(*) FROM otak.campaigns WHERE id = ? AND status::text = 'DRAFT'", id));
    }

    @Test
    void insertCampaign_sameNameDifferentCaseInOneOrganization_isRejected() {
        String name = "Launch " + uniqueSuffix();
        draft(tenantA, name);

        assertThrows(DataIntegrityViolationException.class, () -> draft(tenantA, name.toUpperCase()));
        assertEquals(1L, count("SELECT count(*) FROM otak.campaigns WHERE organization_id = ?",
                tenantA.organization().getId()));
    }

    @Test
    void insertCampaign_sameNameInTwoOrganizations_isAccepted() {
        String name = "Launch " + uniqueSuffix();

        draft(tenantA, name);
        draft(tenantB, name);

        assertEquals(1L, count("SELECT count(*) FROM otak.campaigns WHERE organization_id = ?",
                tenantB.organization().getId()));
    }

    @Test
    void insertContent_textOver20000Characters_isRejectedByTheCheckConstraint() {
        UUID id = draft(tenantA, "Long " + uniqueSuffix());

        content(id, "NL", "x".repeat(20000));
        assertThrows(DataIntegrityViolationException.class, () -> content(id, "EN", "x".repeat(20001)));
    }

    @Test
    void insertContent_secondTextInOneLanguage_isRejectedByTheUniqueIndex() {
        UUID id = draft(tenantA, "Twice " + uniqueSuffix());
        content(id, "NL", "Eerste");

        assertThrows(DataIntegrityViolationException.class, () -> content(id, "NL", "Tweede"));
        content(id, "EN", "Second");
        assertEquals(2L, count("SELECT count(*) FROM otak.campaign_contents WHERE campaign_id = ?", id));
    }

    @Test
    void deleteCampaign_withTextsAndLinkedRelation_removesTextsClearsTheLinkAndKeepsTheRelation() {
        UUID id = draft(tenantA, "Linked " + uniqueSuffix());
        content(id, "NL", "Tekst");
        content(id, "TR", "Metin");
        UUID relation = lead(tenantA, id);

        jdbcTemplate.update("DELETE FROM otak.campaigns WHERE id = ?", id);

        assertEquals(0L, count("SELECT count(*) FROM otak.campaign_contents WHERE campaign_id = ?", id));
        assertEquals(1L, count("SELECT count(*) FROM otak.relations WHERE id = ? AND campaign_id IS NULL "
                + "AND organization_id = ?", relation, tenantA.organization().getId()));
    }

    @Test
    void deleteEvent_withLinkedCampaign_clearsOnlyEventIdAndKeepsTheCampaign() {
        UUID event = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO otak.events (id, name, type, start_date, organization_id) "
                + "VALUES (?, ?, 'EXPO', '2026-11-10', ?)", event, "Expo " + uniqueSuffix(),
                tenantA.organization().getId());
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO otak.campaigns (id, name, channel, event_id, organization_id) "
                + "VALUES (?, ?, 'X', ?, ?)", id, "Event " + uniqueSuffix(), event, tenantA.organization().getId());

        jdbcTemplate.update("DELETE FROM otak.events WHERE id = ?", event);

        assertEquals(1L, count("SELECT count(*) FROM otak.campaigns WHERE id = ? AND event_id IS NULL "
                + "AND organization_id = ?", id, tenantA.organization().getId()));
    }

    @Test
    void insertCampaign_linkedToEventOfAnotherOrganization_isRejectedByTheCompositeForeignKey() {
        UUID eventOfA = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO otak.events (id, name, type, start_date, organization_id) "
                + "VALUES (?, ?, 'EXPO', '2026-11-10', ?)", eventOfA, "Expo " + uniqueSuffix(),
                tenantA.organization().getId());

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO otak.campaigns (id, name, channel, event_id, organization_id) VALUES (?, ?, 'X', ?, ?)",
                UUID.randomUUID(), "Cross " + uniqueSuffix(), eventOfA, tenantB.organization().getId()));
    }

    @Test
    void insertRelation_linkedToCampaignOfAnotherOrganization_isRejectedByTheCompositeForeignKey() {
        UUID campaignOfA = draft(tenantA, "Of A " + uniqueSuffix());

        assertThrows(DataIntegrityViolationException.class, () -> lead(tenantB, campaignOfA));
        assertEquals(0L, count("SELECT count(*) FROM otak.relations WHERE organization_id = ?",
                tenantB.organization().getId()));
    }
}
