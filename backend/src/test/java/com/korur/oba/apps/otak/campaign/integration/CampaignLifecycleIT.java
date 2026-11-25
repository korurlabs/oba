package com.korur.oba.apps.otak.campaign.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.support.AbstractOtakDocumentsIT;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * Proves, against the migrated PostgreSQL schema, what a campaign does to the rows around it:
 * deleting it clears campaign_id on outreaches and relations and removes its contents and
 * their images, and a campaign of another organization can be linked to nothing.
 */
class CampaignLifecycleIT extends AbstractOtakDocumentsIT {

    private static final String CAMPAIGNS_PATH = "/otak/campaigns";
    private static final String ATTACHMENTS_PATH = "/storage/attachments";

    private Tenant tenant;
    private Tenant otherTenant;
    private Person admin;
    private Person otherAdmin;
    private String campaignId;
    private String campaignName;

    @BeforeEach
    void setUp() throws Exception {
        tenant = newTenant();
        otherTenant = newTenant();
        admin = signInAs(tenant, OrganizationMemberRole.ADMIN, "lifeadmin");
        otherAdmin = signInAs(otherTenant, OrganizationMemberRole.ADMIN, "lifeother");
        campaignName = "Campaign " + uniqueSuffix();
        campaignId = read(mockMvc.perform(in(tenant, post(CAMPAIGNS_PATH), admin.jwt(),
                CampaignAccessIT.body(campaignName))).andExpect(status().isCreated()).andReturn(), "$.data.id");
    }

    private String content() {
        UUID legalEntityId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO shrine.legal_entities (id, organization_id, short_name, legal_name, country) "
                + "VALUES (?, ?::uuid, ?, 'Korur B.V.', 'NLD')", legalEntityId, tenant.id(), "Korur " + uniqueSuffix());
        UUID contentId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO otak.campaign_contents (id, campaign_id, organization_id, legal_entity_id, "
                + "channel, language, text) VALUES (?, ?::uuid, ?::uuid, ?, 'LINKEDIN', 'NL', 'Tekst')", contentId,
                campaignId, tenant.id(), legalEntityId);
        return contentId.toString();
    }

    private String upload(String contentId) throws Exception {
        MockMultipartFile metadata = new MockMultipartFile("metadata", "", MediaType.APPLICATION_JSON_VALUE,
                ("[{\"fileName\":\"post.png\",\"entityType\":\"CAMPAIGN_CONTENT\",\"entityId\":\"" + contentId
                        + "\"}]")
                        .getBytes());
        MockMultipartFile file = new MockMultipartFile("files", "post.png", MediaType.IMAGE_PNG_VALUE,
                ("png " + uniqueSuffix()).getBytes(StandardCharsets.UTF_8));
        return read(mockMvc.perform(in(tenant, multipart(ATTACHMENTS_PATH).file(metadata).file(file), admin.jwt(),
                null)).andExpect(status().isCreated()).andReturn(), "$.data[0].id");
    }

    @Test
    void delete_withLinkedOutreachRelationContentAndImage_clearsLinksAndRemovesTheRest() throws Exception {
        String relationId = read(mockMvc.perform(in(tenant, post(RELATIONS_PATH), admin.jwt(),
                "{\"name\":\"Lead " + uniqueSuffix() + "\",\"type\":\"LEAD\",\"campaignId\":\"" + campaignId + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.campaignId").value(campaignId))
                .andReturn(), "$.data.id");
        String contactId = createContact(tenant, admin, relationId, "jan" + uniqueSuffix() + "@fabrikam.example");
        String outreachId = read(mockMvc.perform(in(tenant, post(OUTREACHES_PATH), admin.jwt(),
                "{\"relationId\":\"" + relationId + "\",\"contactId\":\"" + contactId
                        + "\",\"type\":\"MAIL\",\"title\":\"Post " + uniqueSuffix() + "\",\"campaignId\":\""
                        + campaignId + "\",\"mail\":{\"language\":\"NL\",\"subject\":\"S\",\"body\":\"B\"}}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.campaignId").value(campaignId))
                .andReturn(), "$.data.id");
        String contentId = content();
        upload(contentId);
        assertThat(count("SELECT count(*) FROM shrine.attachments WHERE entity_id = ?::uuid", contentId))
                .isEqualTo(1L);

        mockMvc.perform(in(tenant, delete(CAMPAIGNS_PATH + "/" + campaignId), admin.jwt(), null))
                .andExpect(status().isOk());

        assertThat(count("SELECT count(*) FROM otak.campaigns WHERE id = ?::uuid", campaignId)).isZero();
        assertThat(count("SELECT count(*) FROM otak.campaign_contents WHERE campaign_id = ?::uuid", campaignId))
                .isZero();
        assertThat(count("SELECT count(*) FROM shrine.attachments WHERE entity_id = ?::uuid", contentId)).isZero();
        assertThat(count("SELECT count(*) FROM otak.relations WHERE id = ?::uuid AND campaign_id IS NULL",
                relationId)).isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM otak.outreaches WHERE id = ?::uuid AND campaign_id IS NULL",
                outreachId)).isEqualTo(1L);
    }

    @Test
    void relationAndOutreachWithCampaignOfAnotherOrganization_answer404AndStoreNothing() throws Exception {
        String foreignCampaignId = read(mockMvc.perform(in(otherTenant, post(CAMPAIGNS_PATH), otherAdmin.jwt(),
                CampaignAccessIT.body("Foreign " + uniqueSuffix()))).andExpect(status().isCreated()).andReturn(),
                "$.data.id");
        String relationId = createRelation(tenant, admin, "Plain " + uniqueSuffix());
        String contactId = createContact(tenant, admin, relationId, "jan" + uniqueSuffix() + "@fabrikam.example");

        mockMvc.perform(in(tenant, post(RELATIONS_PATH), admin.jwt(),
                "{\"name\":\"Linked\",\"type\":\"LEAD\",\"campaignId\":\"" + foreignCampaignId + "\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("OTK_NOT_012"));
        mockMvc.perform(in(tenant, put(RELATIONS_PATH + "/" + relationId), admin.jwt(),
                "{\"name\":\"Plain\",\"campaignId\":\"" + foreignCampaignId + "\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("OTK_NOT_012"));
        mockMvc.perform(in(tenant, post(OUTREACHES_PATH), admin.jwt(),
                "{\"relationId\":\"" + relationId + "\",\"contactId\":\"" + contactId
                        + "\",\"type\":\"MAIL\",\"title\":\"Cross\",\"campaignId\":\"" + foreignCampaignId
                        + "\",\"mail\":{\"language\":\"NL\",\"subject\":\"S\",\"body\":\"B\"}}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("OTK_NOT_012"));

        assertThat(count("SELECT count(*) FROM otak.relations WHERE organization_id = ?::uuid", tenant.id()))
                .isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM otak.relations WHERE campaign_id = ?::uuid", foreignCampaignId))
                .isZero();
        assertThat(count("SELECT count(*) FROM otak.outreaches WHERE organization_id = ?::uuid", tenant.id()))
                .isZero();
    }

    @Test
    void create_eventOfAnotherOrganization_answers404AndStoresNothing() throws Exception {
        String foreignEventId = read(mockMvc.perform(in(otherTenant, post("/otak/events"), otherAdmin.jwt(),
                "{\"name\":\"Expo " + uniqueSuffix() + "\",\"type\":\"EXPO\",\"startDate\":\"2026-11-19\"}"))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");

        mockMvc.perform(in(tenant, post(CAMPAIGNS_PATH), admin.jwt(),
                "{\"name\":\"Cross " + uniqueSuffix() + "\",\"eventId\":\"" + foreignEventId + "\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("OTK_NOT_009"));

        assertThat(count("SELECT count(*) FROM otak.campaigns WHERE organization_id = ?::uuid", tenant.id()))
                .isEqualTo(1L);
    }
}
