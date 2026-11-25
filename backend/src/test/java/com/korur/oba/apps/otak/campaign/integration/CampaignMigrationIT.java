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
 * The campaign schema refuses rows the application must never write: a campaign whose end
 * date lies before its start date, a duplicate name inside one organization, a scheduled
 * content without a planned moment, a draft or scheduled content with a published moment, an
 * unknown channel or status, a content of another organization's campaign or legal entity,
 * and a relation linked to the campaign of another organization. Deleting a campaign keeps
 * the relations that pointed at it and removes its contents; a legal entity that authored a
 * content cannot be deleted. The tests use SQL only, so they hold independently of the
 * campaign entities.
 */
class CampaignMigrationIT extends AbstractTenantIT {

    private Tenant tenantA;
    private Tenant tenantB;

    @BeforeEach
    void setUpTenants() {
        tenantA = newTenant();
        tenantB = newTenant();
    }

    private UUID campaign(Tenant tenant, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO otak.campaigns (id, name, organization_id) VALUES (?, ?, ?)", id, name,
                tenant.organization().getId());
        return id;
    }

    private UUID legalEntity(Tenant tenant) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO shrine.legal_entities (id, organization_id, short_name, legal_name, country) "
                + "VALUES (?, ?, ?, ?, 'NLD')", id, tenant.organization().getId(), "Korur " + uniqueSuffix(),
                "Korur B.V.");
        return id;
    }

    private UUID content(Tenant tenant, UUID campaignId, UUID legalEntityId, String status, String plannedAt,
                         String publishedAt) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO otak.campaign_contents (id, campaign_id, organization_id, legal_entity_id, "
                + "channel, language, status, text, planned_at, published_at) VALUES (?, ?, ?, ?, 'LINKEDIN', "
                + "'NL'::shrine_document_language_enum, ?::otak_campaign_status_enum, 'Tekst', ?::timestamptz, "
                + "?::timestamptz)", id, campaignId, tenant.organization().getId(), legalEntityId, status, plannedAt,
                publishedAt);
        return id;
    }

    private UUID draftContent(Tenant tenant, UUID campaignId, UUID legalEntityId) {
        return content(tenant, campaignId, legalEntityId, "DRAFT", null, null);
    }

    private UUID lead(Tenant tenant, UUID campaignId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO otak.relations (id, name, type, stage, campaign_id, organization_id) "
                + "VALUES (?, ?, 'LEAD', 'NEW', ?, ?)", id, "Relation " + uniqueSuffix(), campaignId,
                tenant.organization().getId());
        return id;
    }

    @Test
    void insertCampaign_endDateBeforeStartDate_isRejectedByTheCheckConstraint() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO otak.campaigns (id, name, start_date, end_date, organization_id) "
                        + "VALUES (?, ?, '2026-12-31', '2026-12-01', ?)",
                UUID.randomUUID(), "Backwards " + uniqueSuffix(), tenantA.organization().getId()));
    }

    @Test
    void insertCampaign_sameDayOrOpenRange_isAccepted() {
        jdbcTemplate.update("INSERT INTO otak.campaigns (id, name, start_date, end_date, organization_id) "
                + "VALUES (?, ?, '2026-12-01', '2026-12-01', ?)", UUID.randomUUID(), "One day " + uniqueSuffix(),
                tenantA.organization().getId());
        jdbcTemplate.update("INSERT INTO otak.campaigns (id, name, start_date, organization_id) "
                + "VALUES (?, ?, '2026-12-01', ?)", UUID.randomUUID(), "Open " + uniqueSuffix(),
                tenantA.organization().getId());

        assertEquals(2L, count("SELECT count(*) FROM otak.campaigns WHERE organization_id = ?",
                tenantA.organization().getId()));
    }

    @Test
    void insertCampaign_sameNameDifferentCaseInOneOrganization_isRejected() {
        String name = "Launch " + uniqueSuffix();
        campaign(tenantA, name);

        assertThrows(DataIntegrityViolationException.class, () -> campaign(tenantA, name.toUpperCase()));
        assertEquals(1L, count("SELECT count(*) FROM otak.campaigns WHERE organization_id = ?",
                tenantA.organization().getId()));
    }

    @Test
    void insertCampaign_sameNameInTwoOrganizations_isAccepted() {
        String name = "Launch " + uniqueSuffix();

        campaign(tenantA, name);
        campaign(tenantB, name);

        assertEquals(1L, count("SELECT count(*) FROM otak.campaigns WHERE organization_id = ?",
                tenantB.organization().getId()));
    }

    @Test
    void insertContent_scheduledWithoutPlannedAt_isRejectedByTheCheckConstraint() {
        UUID campaignId = campaign(tenantA, "Scheduled " + uniqueSuffix());
        UUID legalEntityId = legalEntity(tenantA);

        assertThrows(DataIntegrityViolationException.class,
                () -> content(tenantA, campaignId, legalEntityId, "SCHEDULED", null, null));
        content(tenantA, campaignId, legalEntityId, "SCHEDULED", "2026-12-01T09:00:00Z", null);
        assertEquals(1L, count("SELECT count(*) FROM otak.campaign_contents WHERE campaign_id = ?", campaignId));
    }

    @Test
    void insertContent_draftOrScheduledWithPublishedAt_isRejectedByTheCheckConstraint() {
        UUID campaignId = campaign(tenantA, "Published " + uniqueSuffix());
        UUID legalEntityId = legalEntity(tenantA);

        assertThrows(DataIntegrityViolationException.class,
                () -> content(tenantA, campaignId, legalEntityId, "DRAFT", null, "2026-11-01T09:00:00Z"));
        assertThrows(DataIntegrityViolationException.class, () -> content(tenantA, campaignId, legalEntityId,
                "SCHEDULED", "2026-12-01T09:00:00Z", "2026-11-01T09:00:00Z"));
        content(tenantA, campaignId, legalEntityId, "PUBLISHED", null, "2026-11-01T09:00:00Z");
        content(tenantA, campaignId, legalEntityId, "ARCHIVED", null, null);
        assertEquals(2L, count("SELECT count(*) FROM otak.campaign_contents WHERE campaign_id = ?", campaignId));
    }

    @Test
    void insertContent_unknownChannelOrStatus_isRejectedByTheEnum() {
        UUID campaignId = campaign(tenantA, "Odd " + uniqueSuffix());
        UUID legalEntityId = legalEntity(tenantA);

        assertThrows(Exception.class, () -> jdbcTemplate.update(
                "INSERT INTO otak.campaign_contents (id, campaign_id, organization_id, legal_entity_id, channel, "
                        + "language, text) VALUES (?, ?, ?, ?, 'FAX'::otak_campaign_channel_enum, 'NL', 'Tekst')",
                UUID.randomUUID(), campaignId, tenantA.organization().getId(), legalEntityId));
        assertThrows(Exception.class, () -> content(tenantA, campaignId, legalEntityId, "LIVE", null, null));
    }

    @Test
    void insertContent_withoutStatus_defaultsToDraftAndTwoInOneLanguageAreAccepted() {
        UUID campaignId = campaign(tenantA, "Default " + uniqueSuffix());
        UUID legalEntityId = legalEntity(tenantA);
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO otak.campaign_contents (id, campaign_id, organization_id, legal_entity_id, "
                + "channel, language, text) VALUES (?, ?, ?, ?, 'X', 'NL', 'Eerste')", id, campaignId,
                tenantA.organization().getId(), legalEntityId);
        draftContent(tenantA, campaignId, legalEntityId);

        assertEquals(1L, count("SELECT count(*) FROM otak.campaign_contents WHERE id = ? AND status::text = 'DRAFT'",
                id));
        assertEquals(2L, count("SELECT count(*) FROM otak.campaign_contents WHERE campaign_id = ?", campaignId));
    }

    @Test
    void insertContent_campaignOrLegalEntityOfAnotherOrganization_isRejectedByTheCompositeForeignKeys() {
        UUID campaignOfA = campaign(tenantA, "Of A " + uniqueSuffix());
        UUID legalEntityOfA = legalEntity(tenantA);
        UUID campaignOfB = campaign(tenantB, "Of B " + uniqueSuffix());
        UUID legalEntityOfB = legalEntity(tenantB);

        assertThrows(DataIntegrityViolationException.class, () -> draftContent(tenantB, campaignOfA, legalEntityOfB));
        assertThrows(DataIntegrityViolationException.class, () -> draftContent(tenantB, campaignOfB, legalEntityOfA));
        assertEquals(0L, count("SELECT count(*) FROM otak.campaign_contents WHERE organization_id = ?",
                tenantB.organization().getId()));
    }

    @Test
    void deleteCampaign_withContentsAndLinkedRelation_removesContentsClearsTheLinkAndKeepsTheRelation() {
        UUID campaignId = campaign(tenantA, "Linked " + uniqueSuffix());
        UUID legalEntityId = legalEntity(tenantA);
        draftContent(tenantA, campaignId, legalEntityId);
        draftContent(tenantA, campaignId, legalEntityId);
        UUID relation = lead(tenantA, campaignId);

        jdbcTemplate.update("DELETE FROM otak.campaigns WHERE id = ?", campaignId);

        assertEquals(0L, count("SELECT count(*) FROM otak.campaign_contents WHERE campaign_id = ?", campaignId));
        assertEquals(1L, count("SELECT count(*) FROM otak.relations WHERE id = ? AND campaign_id IS NULL "
                + "AND organization_id = ?", relation, tenantA.organization().getId()));
    }

    @Test
    void deleteLegalEntity_thatAuthoredAContent_isRejected() {
        UUID campaignId = campaign(tenantA, "Author " + uniqueSuffix());
        UUID legalEntityId = legalEntity(tenantA);
        draftContent(tenantA, campaignId, legalEntityId);

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("DELETE FROM shrine.legal_entities WHERE id = ?", legalEntityId));
    }

    @Test
    void deleteEvent_withLinkedCampaign_clearsOnlyEventIdAndKeepsTheCampaign() {
        UUID event = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO otak.events (id, name, type, start_date, organization_id) "
                + "VALUES (?, ?, 'EXPO', '2026-11-10', ?)", event, "Expo " + uniqueSuffix(),
                tenantA.organization().getId());
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO otak.campaigns (id, name, event_id, organization_id) VALUES (?, ?, ?, ?)",
                id, "Event " + uniqueSuffix(), event, tenantA.organization().getId());

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
                "INSERT INTO otak.campaigns (id, name, event_id, organization_id) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), "Cross " + uniqueSuffix(), eventOfA, tenantB.organization().getId()));
    }

    @Test
    void insertRelation_linkedToCampaignOfAnotherOrganization_isRejectedByTheCompositeForeignKey() {
        UUID campaignOfA = campaign(tenantA, "Of A " + uniqueSuffix());

        assertThrows(DataIntegrityViolationException.class, () -> lead(tenantB, campaignOfA));
        assertEquals(0L, count("SELECT count(*) FROM otak.relations WHERE organization_id = ?",
                tenantB.organization().getId()));
    }
}
