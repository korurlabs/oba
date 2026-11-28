package com.korur.oba.apps.otak.campaign.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.support.AbstractOtakDocumentsIT;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * Proves, through the real security chain and the migrated schema, the content endpoints of a
 * campaign: a MEMBER writes and deletes contents, a GUEST and another organization get nothing,
 * a legal entity or campaign of another organization is refused, the status decides which
 * moments a content may carry, the date range of a campaign is checked, and the files of a
 * content are reached through the CAMPAIGN_CONTENT attachment type under the same rules.
 */
class CampaignContentIT extends AbstractOtakDocumentsIT {

    private static final String CAMPAIGNS_PATH = "/otak/campaigns";
    private static final String ATTACHMENTS_PATH = "/storage/attachments";
    private static final String ORGANIZATION_HEADER = "X-Organization-ID";
    private static final String CAMPAIGN_NOT_FOUND = "OTK_NOT_012";
    private static final String CONTENT_NOT_FOUND = "OTK_NOT_013";
    private static final String LEGAL_ENTITY_NOT_FOUND = "LGE_NOT_001";
    private static final String STATUS_DATES = "OTK_VAL_016";
    private static final String DATE_RANGE = "OTK_VAL_017";
    private static final String ORGANIZATION_MISMATCH = "APP_SEC_002";

    private Tenant tenant;
    private Tenant otherTenant;
    private Person member;
    private Person admin;
    private Person guest;
    private Person otherAdmin;
    private String campaignId;
    private String legalEntityId;
    private String otherLegalEntityId;

    @BeforeEach
    void setUp() throws Exception {
        tenant = newTenant();
        otherTenant = newTenant();
        member = signInAs(tenant, OrganizationMemberRole.MEMBER, "contmember");
        admin = signInAs(tenant, OrganizationMemberRole.ADMIN, "contadmin");
        guest = signInAs(tenant, OrganizationMemberRole.GUEST, "contguest");
        otherAdmin = signInAs(otherTenant, OrganizationMemberRole.ADMIN, "contother");
        campaignId = read(mockMvc.perform(in(tenant, post(CAMPAIGNS_PATH), member.jwt(),
                CampaignAccessIT.body("Campaign " + uniqueSuffix()))).andExpect(status().isCreated()).andReturn(),
                "$.data.id");
        legalEntityId = createLegalEntity(tenant, admin, "Korur " + uniqueSuffix().substring(0, 6));
        otherLegalEntityId = createLegalEntity(otherTenant, otherAdmin, "Other " + uniqueSuffix().substring(0, 6));
    }

    private String contentsPath(String campaign) {
        return CAMPAIGNS_PATH + "/" + campaign + "/contents";
    }

    private String body(String legalEntity, String status, String plannedAt, String publishedAt) {
        StringBuilder json = new StringBuilder("{\"legalEntityId\":\"").append(legalEntity)
                .append("\",\"channel\":\"LINKEDIN\",\"language\":\"NL\",\"text\":\"Tekst ")
                .append(uniqueSuffix()).append("\"");
        if (status != null) {
            json.append(",\"status\":\"").append(status).append("\"");
        }
        if (plannedAt != null) {
            json.append(",\"plannedAt\":\"").append(plannedAt).append("\"");
        }
        if (publishedAt != null) {
            json.append(",\"publishedAt\":\"").append(publishedAt).append("\"");
        }
        return json.append("}").toString();
    }

    private ResultActions create(Tenant header, Person person, String campaign, String json) throws Exception {
        return mockMvc.perform(in(header, post(contentsPath(campaign)), person.jwt(), json));
    }

    private String createContent() throws Exception {
        return read(create(tenant, member, campaignId, body(legalEntityId, null, null, null))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
    }

    private long contents() {
        return count("SELECT count(*) FROM otak.campaign_contents WHERE campaign_id = ?::uuid", campaignId);
    }

    private long attachmentsOf(String entityId) {
        return count("SELECT count(*) FROM shrine.attachments WHERE entity_id = ?::uuid", entityId);
    }

    private ResultActions upload(Tenant header, Person person, String entityId, String content) throws Exception {
        MockMultipartFile metadata = new MockMultipartFile("metadata", "", MediaType.APPLICATION_JSON_VALUE,
                ("[{\"fileName\":\"post.png\",\"entityType\":\"CAMPAIGN_CONTENT\",\"entityId\":\"" + entityId
                        + "\"}]").getBytes());
        MockMultipartFile file = new MockMultipartFile("files", "post.png", MediaType.IMAGE_PNG_VALUE,
                content.getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(multipart(ATTACHMENTS_PATH).file(metadata).file(file).with(person.jwt())
                .header(ORGANIZATION_HEADER, header.id()));
    }

    private ResultActions listFiles(Tenant header, Person person, String entityId) throws Exception {
        return mockMvc.perform(get(ATTACHMENTS_PATH).param("entityType", "CAMPAIGN_CONTENT")
                .param("entityId", entityId).with(person.jwt()).header(ORGANIZATION_HEADER, header.id()));
    }

    // ---- CRUD per role ----

    @Test
    void member_createListUpdateDelete_succeed() throws Exception {
        String contentId = createContent();

        mockMvc.perform(in(tenant, get(contentsPath(campaignId)), member.jwt(), null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(contentId))
                .andExpect(jsonPath("$.data[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.data[0].legalEntityId").value(legalEntityId))
                .andExpect(jsonPath("$.data[0].legalEntityShortName").isNotEmpty());
        mockMvc.perform(in(tenant, put(contentsPath(campaignId) + "/" + contentId), member.jwt(),
                body(legalEntityId, "SCHEDULED", "2030-01-01T09:00:00Z", null))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.plannedAt").value("2030-01-01T09:00:00Z"));
        mockMvc.perform(in(tenant, get(CAMPAIGNS_PATH), member.jwt(), null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].contentCount").value(1));
        mockMvc.perform(in(tenant, delete(contentsPath(campaignId) + "/" + contentId), member.jwt(), null))
                .andExpect(status().isOk());

        assertThat(contents()).isZero();
    }

    @Test
    void admin_createAndDelete_succeed() throws Exception {
        String contentId = read(create(tenant, admin, campaignId, body(legalEntityId, null, null, null))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");

        mockMvc.perform(in(tenant, delete(contentsPath(campaignId) + "/" + contentId), admin.jwt(), null))
                .andExpect(status().isOk());

        assertThat(contents()).isZero();
    }

    @Test
    void guest_everyContentEndpoint_answers403AndChangesNothing() throws Exception {
        String contentId = createContent();
        String path = contentsPath(campaignId);

        mockMvc.perform(in(tenant, get(path), guest.jwt(), null)).andExpect(status().isForbidden());
        create(tenant, guest, campaignId, body(legalEntityId, null, null, null)).andExpect(status().isForbidden());
        mockMvc.perform(in(tenant, put(path + "/" + contentId), guest.jwt(),
                body(legalEntityId, "DRAFT", null, null))).andExpect(status().isForbidden());
        mockMvc.perform(in(tenant, delete(path + "/" + contentId), guest.jwt(), null))
                .andExpect(status().isForbidden());

        assertThat(contents()).isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM otak.campaign_contents WHERE id = ?::uuid AND status = 'DRAFT'",
                contentId)).isEqualTo(1L);
    }

    @Test
    void otherOrganization_everyContentEndpoint_answers404AndChangesNothing() throws Exception {
        String contentId = createContent();
        String path = contentsPath(campaignId);

        mockMvc.perform(in(otherTenant, get(path), otherAdmin.jwt(), null)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(CAMPAIGN_NOT_FOUND));
        create(otherTenant, otherAdmin, campaignId, body(otherLegalEntityId, null, null, null))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value(CAMPAIGN_NOT_FOUND));
        mockMvc.perform(in(otherTenant, put(path + "/" + contentId), otherAdmin.jwt(),
                body(otherLegalEntityId, "DRAFT", null, null))).andExpect(status().isNotFound());
        mockMvc.perform(in(otherTenant, delete(path + "/" + contentId), otherAdmin.jwt(), null))
                .andExpect(status().isNotFound());

        assertThat(contents()).isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM otak.campaign_contents WHERE id = ?::uuid AND legal_entity_id = "
                + "?::uuid", contentId, legalEntityId)).isEqualTo(1L);
    }

    @Test
    void contentOfAnotherCampaign_underThisCampaignPath_answers404ContentNotFound() throws Exception {
        String otherCampaignId = read(mockMvc.perform(in(tenant, post(CAMPAIGNS_PATH), member.jwt(),
                CampaignAccessIT.body("Second " + uniqueSuffix()))).andExpect(status().isCreated()).andReturn(),
                "$.data.id");
        String contentId = createContent();

        mockMvc.perform(in(tenant, put(contentsPath(otherCampaignId) + "/" + contentId), member.jwt(),
                body(legalEntityId, "DRAFT", null, null))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(CONTENT_NOT_FOUND));
        mockMvc.perform(in(tenant, delete(contentsPath(otherCampaignId) + "/" + contentId), member.jwt(), null))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value(CONTENT_NOT_FOUND));

        assertThat(contents()).isEqualTo(1L);
    }

    @Test
    void create_legalEntityOfAnotherOrganization_answers404AndStoresNothing() throws Exception {
        create(tenant, member, campaignId, body(otherLegalEntityId, null, null, null))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value(LEGAL_ENTITY_NOT_FOUND));

        assertThat(contents()).isZero();
    }

    @Test
    void update_legalEntityOfAnotherOrganization_answers404AndKeepsTheContent() throws Exception {
        String contentId = createContent();

        mockMvc.perform(in(tenant, put(contentsPath(campaignId) + "/" + contentId), member.jwt(),
                body(otherLegalEntityId, "DRAFT", null, null))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(LEGAL_ENTITY_NOT_FOUND));

        assertThat(count("SELECT count(*) FROM otak.campaign_contents WHERE id = ?::uuid AND legal_entity_id = "
                + "?::uuid", contentId, legalEntityId)).isEqualTo(1L);
    }

    // ---- status and dates ----

    @Test
    void create_scheduledWithoutPlannedAt_answersStatusDatesError() throws Exception {
        create(tenant, member, campaignId, body(legalEntityId, "SCHEDULED", null, null))
                .andExpect(status().is4xxClientError()).andExpect(jsonPath("$.error.code").value(STATUS_DATES));

        assertThat(contents()).isZero();
    }

    @Test
    void create_draftOrScheduledWithPublishedAt_answersStatusDatesError() throws Exception {
        create(tenant, member, campaignId, body(legalEntityId, "DRAFT", null, "2020-01-01T09:00:00Z"))
                .andExpect(status().is4xxClientError()).andExpect(jsonPath("$.error.code").value(STATUS_DATES));
        create(tenant, member, campaignId, body(legalEntityId, "SCHEDULED", "2030-01-01T09:00:00Z",
                "2020-01-01T09:00:00Z")).andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.error.code").value(STATUS_DATES));

        assertThat(contents()).isZero();
    }

    @Test
    void create_publishedInTheFuture_answersStatusDatesError() throws Exception {
        String future = Instant.now().plus(2, ChronoUnit.DAYS).toString();

        create(tenant, member, campaignId, body(legalEntityId, "PUBLISHED", null, future))
                .andExpect(status().is4xxClientError()).andExpect(jsonPath("$.error.code").value(STATUS_DATES));

        assertThat(contents()).isZero();
    }

    @Test
    void create_publishedWithoutPublishedAt_storesTheServersNow() throws Exception {
        Instant before = Instant.now().minusSeconds(5);

        String contentId = read(create(tenant, member, campaignId, body(legalEntityId, "PUBLISHED", null, null))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.publishedAt").isNotEmpty())
                .andReturn(), "$.data.id");

        Instant stored = jdbcTemplate.queryForObject(
                "SELECT published_at FROM otak.campaign_contents WHERE id = ?::uuid", java.sql.Timestamp.class,
                contentId).toInstant();
        assertThat(stored).isBetween(before, Instant.now().plusSeconds(5));
    }

    @Test
    void create_publishedInThePast_keepsTheGivenMoment() throws Exception {
        create(tenant, member, campaignId, body(legalEntityId, "PUBLISHED", null, "2026-01-02T10:00:00Z"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.publishedAt").value("2026-01-02T10:00:00Z"));
    }

    @Test
    void update_withoutStatus_answers422OnTheStatusField() throws Exception {
        String contentId = createContent();

        mockMvc.perform(in(tenant, put(contentsPath(campaignId) + "/" + contentId), member.jwt(),
                body(legalEntityId, null, null, null))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));
    }

    @Test
    void update_toScheduledWithoutPlannedAt_answersStatusDatesErrorAndKeepsTheRow() throws Exception {
        String contentId = createContent();

        mockMvc.perform(in(tenant, put(contentsPath(campaignId) + "/" + contentId), member.jwt(),
                body(legalEntityId, "SCHEDULED", null, null))).andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.error.code").value(STATUS_DATES));

        assertThat(count("SELECT count(*) FROM otak.campaign_contents WHERE id = ?::uuid AND status = 'DRAFT'",
                contentId)).isEqualTo(1L);
    }

    // ---- campaign date range ----

    @Test
    void campaign_endDateBeforeStartDate_answersDateRangeErrorOnCreateAndUpdate() throws Exception {
        mockMvc.perform(in(tenant, post(CAMPAIGNS_PATH), member.jwt(),
                "{\"name\":\"Range " + uniqueSuffix() + "\",\"startDate\":\"2026-12-31\",\"endDate\":\"2026-12-01\"}"))
                .andExpect(status().is4xxClientError()).andExpect(jsonPath("$.error.code").value(DATE_RANGE));
        mockMvc.perform(in(tenant, put(CAMPAIGNS_PATH + "/" + campaignId), member.jwt(),
                "{\"name\":\"Renamed\",\"startDate\":\"2026-12-31\",\"endDate\":\"2026-12-01\"}"))
                .andExpect(status().is4xxClientError()).andExpect(jsonPath("$.error.code").value(DATE_RANGE));

        assertThat(count("SELECT count(*) FROM otak.campaigns WHERE organization_id = ?::uuid", tenant.id()))
                .isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM otak.campaigns WHERE id = ?::uuid AND name <> 'Renamed'",
                campaignId)).isEqualTo(1L);
    }

    @Test
    void campaign_sameDayRange_isAccepted() throws Exception {
        mockMvc.perform(in(tenant, put(CAMPAIGNS_PATH + "/" + campaignId), member.jwt(),
                "{\"name\":\"Same day\",\"startDate\":\"2026-12-01\",\"endDate\":\"2026-12-01\"}"))
                .andExpect(status().isOk());
    }

    // ---- legal entity RESTRICT ----

    @Test
    void deleteLegalEntity_thatAuthoredAContent_isRefusedAndKeepsBothRows() throws Exception {
        String contentId = createContent();

        int httpStatus = mockMvc.perform(in(tenant, delete("/legal-entities/" + legalEntityId), admin.jwt(), null))
                .andReturn().getResponse().getStatus();

        assertThat(httpStatus).as("a legal entity with a content is refused with a client error")
                .isBetween(400, 499);
        assertThat(count("SELECT count(*) FROM shrine.legal_entities WHERE id = ?::uuid", legalEntityId))
                .isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM otak.campaign_contents WHERE id = ?::uuid", contentId))
                .isEqualTo(1L);
    }

    // ---- images of a content ----

    @Test
    void deleteContent_removesItsImagesAndKeepsThoseOfOtherContents() throws Exception {
        String contentId = createContent();
        String keptContentId = createContent();
        upload(tenant, member, contentId, "png a " + uniqueSuffix()).andExpect(status().isCreated());
        upload(tenant, member, keptContentId, "png b " + uniqueSuffix()).andExpect(status().isCreated());

        mockMvc.perform(in(tenant, delete(contentsPath(campaignId) + "/" + contentId), member.jwt(), null))
                .andExpect(status().isOk());

        assertThat(attachmentsOf(contentId)).isZero();
        assertThat(attachmentsOf(keptContentId)).isEqualTo(1L);
    }

    @Test
    void attachment_memberUploadsListsAndDeletesOnAContent() throws Exception {
        String contentId = createContent();

        String attachmentId = read(upload(tenant, member, contentId, "png " + uniqueSuffix())
                .andExpect(status().isCreated()).andReturn(), "$.data[0].id");
        listFiles(tenant, member, contentId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(delete(ATTACHMENTS_PATH + "/" + attachmentId).with(member.jwt())
                .header(ORGANIZATION_HEADER, tenant.id())).andExpect(status().isOk());

        assertThat(attachmentsOf(contentId)).isZero();
    }

    @Test
    void attachment_guestReadAndWriteAnswer403() throws Exception {
        String contentId = createContent();

        listFiles(tenant, guest, contentId).andExpect(status().isForbidden());
        upload(tenant, guest, contentId, "png " + uniqueSuffix()).andExpect(status().isForbidden());

        assertThat(attachmentsOf(contentId)).isZero();
    }

    @Test
    void attachment_unknownContentAnswers404ContentNotFound() throws Exception {
        upload(tenant, member, UUID.randomUUID().toString(), "png none").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(CONTENT_NOT_FOUND));
    }

    @Test
    void attachment_contentOfAnotherOrganizationAnswers403OrganizationMismatchAndStoresNothing() throws Exception {
        String contentId = createContent();

        listFiles(otherTenant, otherAdmin, contentId).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(ORGANIZATION_MISMATCH));
        upload(otherTenant, otherAdmin, contentId, "png cross " + uniqueSuffix()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(ORGANIZATION_MISMATCH));

        assertThat(attachmentsOf(contentId)).isZero();
    }
}
