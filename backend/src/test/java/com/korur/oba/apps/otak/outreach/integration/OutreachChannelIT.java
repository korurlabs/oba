package com.korur.oba.apps.otak.outreach.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.documenttemplate.integration.AbstractOtakDocumentsIT;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * Proves, through the real security chain and the migrated schema, the WhatsApp and call
 * channels of an outreach: they are created and edited without a mail, refuse every mail
 * field and the send and attachment actions, and are recorded as done by complete, which
 * takes the contact's phone and no mail sender.
 */
class OutreachChannelIT extends AbstractOtakDocumentsIT {

    private static final String PHONE = "+31 6 1234 5678";

    private Tenant tenant;
    private Person member;
    private Person admin;
    private String relationId;
    private String phoneContactId;
    private String mailContactId;

    @BeforeEach
    void setUpTenantAndContacts() throws Exception {
        tenant = newTenant();
        member = signIn(tenant, "otakmember");
        admin = signInAs(tenant, OrganizationMemberRole.ADMIN, "otakadmin");
        relationId = createRelation(tenant, admin, "Relation " + uniqueSuffix());
        phoneContactId = createContactWithPhone(tenant, admin, relationId, " " + PHONE + " ");
        mailContactId = createContact(tenant, admin, relationId, "jan" + uniqueSuffix() + "@fabrikam.example");
    }

    private static String channelBody(String relationId, String contactId, String type, String extra) {
        return "{\"relationId\":\"" + relationId + "\",\"contactId\":\"" + contactId + "\",\"type\":\"" + type
                + "\",\"title\":\"Reach out\"" + extra + "}";
    }

    private String createChannel(String type, String contactId) throws Exception {
        return read(mockMvc.perform(in(tenant, post(OUTREACHES_PATH), admin.jwt(),
                        channelBody(relationId, contactId, type, "")))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
    }

    private long outreachesOfTenant() {
        return count("SELECT count(*) FROM otak.outreaches WHERE organization_id = ?::uuid", tenant.id());
    }

    private long mailsOfTenant() {
        return count("SELECT count(*) FROM otak.mails WHERE organization_id = ?::uuid", tenant.id());
    }

    private String statusOf(String outreachId) {
        return jdbcTemplate.queryForObject("SELECT status::text FROM otak.outreaches WHERE id = ?::uuid",
                String.class, outreachId);
    }

    @Test
    void admin_createWhatsappAndCall_answer201WithoutMail() throws Exception {
        for (String type : new String[] {"WHATSAPP", "CALL"}) {
            MvcResult result = mockMvc.perform(in(tenant, post(OUTREACHES_PATH), admin.jwt(),
                            channelBody(relationId, phoneContactId, type, "")))
                    .andExpect(status().isCreated()).andReturn();

            assertThat(read(result, "$.data.type")).isEqualTo(type);
            assertThat(read(result, "$.data.status")).isEqualTo("DRAFT");
            assertThat(read(result, "$.data.mail")).isEqualTo("null");
            assertThat(result.getResponse().getContentAsString()).doesNotContain("\"attachments\"");
        }

        assertThat(outreachesOfTenant()).isEqualTo(2L);
        assertThat(mailsOfTenant()).isZero();
        assertThat(count("SELECT count(*) FROM otak.outreaches WHERE organization_id = ?::uuid AND mail_id IS NULL",
                tenant.id())).isEqualTo(2L);
    }

    @Test
    void member_listAndDetailOfAChannel_answer200() throws Exception {
        String callId = createChannel("CALL", phoneContactId);

        MvcResult detail = mockMvc.perform(in(tenant, get(OUTREACHES_PATH + "/" + callId), member.jwt(), null))
                .andExpect(status().isOk()).andReturn();

        assertThat(read(detail, "$.data.type")).isEqualTo("CALL");
        assertThat(read(detail, "$.data.mail")).isEqualTo("null");
    }

    @Test
    void admin_createWithoutType_answers422AndStoresNothing() throws Exception {
        mockMvc.perform(in(tenant, post(OUTREACHES_PATH), admin.jwt(),
                        "{\"relationId\":\"" + relationId + "\",\"contactId\":\"" + phoneContactId
                                + "\",\"title\":\"No type\"}"))
                .andExpect(status().isUnprocessableEntity());

        assertThat(outreachesOfTenant()).isZero();
    }

    @Test
    void admin_createChannelWithMail_answers422OnTheMailField() throws Exception {
        for (String type : new String[] {"WHATSAPP", "CALL"}) {
            MvcResult result = mockMvc.perform(in(tenant, post(OUTREACHES_PATH), admin.jwt(),
                            channelBody(relationId, phoneContactId, type,
                                    ",\"mail\":{\"language\":\"NL\",\"subject\":\"S\",\"body\":\"B\"}")))
                    .andExpect(status().isUnprocessableEntity()).andReturn();

            assertThat(result.getResponse().getContentAsString()).contains("GEN_VAL_001").contains("\"mail\"");
        }

        assertThat(outreachesOfTenant()).isZero();
        assertThat(mailsOfTenant()).isZero();
    }

    @Test
    void admin_createChannelWithMailTemplate_answers422OnTheTemplateField() throws Exception {
        String templateId = createMailTemplate(tenant, admin, "Template " + uniqueSuffix());

        MvcResult result = mockMvc.perform(in(tenant, post(OUTREACHES_PATH), admin.jwt(),
                        channelBody(relationId, phoneContactId, "WHATSAPP", ",\"mailTemplateId\":\"" + templateId + "\"")))
                .andExpect(status().isUnprocessableEntity()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("GEN_VAL_001").contains("mailTemplateId");
        assertThat(outreachesOfTenant()).isZero();
        assertThat(mailsOfTenant()).isZero();
    }

    @Test
    void admin_createChannelWithDocumentTemplates_answers422OnTheDocumentTemplateField() throws Exception {
        MvcResult result = mockMvc.perform(in(tenant, post(OUTREACHES_PATH), admin.jwt(),
                        channelBody(relationId, phoneContactId, "CALL",
                                ",\"documentTemplateIds\":[\"" + UUID.randomUUID() + "\"]")))
                .andExpect(status().isUnprocessableEntity()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("GEN_VAL_001").contains("documentTemplateIds");
        assertThat(outreachesOfTenant()).isZero();
    }

    @Test
    void admin_createChannelWithAnEmptyDocumentTemplateList_answers201() throws Exception {
        mockMvc.perform(in(tenant, post(OUTREACHES_PATH), admin.jwt(),
                        channelBody(relationId, phoneContactId, "CALL", ",\"documentTemplateIds\":[]")))
                .andExpect(status().isCreated());
    }

    @Test
    void admin_createMailWithoutMailSource_answers422() throws Exception {
        mockMvc.perform(in(tenant, post(OUTREACHES_PATH), admin.jwt(),
                        channelBody(relationId, mailContactId, "MAIL", "")))
                .andExpect(status().isUnprocessableEntity());

        assertThat(outreachesOfTenant()).isZero();
    }

    @Test
    void admin_updateChannelWithoutMail_changesTheTitleAndNotTheType() throws Exception {
        String callId = createChannel("CALL", phoneContactId);

        MvcResult result = mockMvc.perform(in(tenant, put(OUTREACHES_PATH + "/" + callId), admin.jwt(),
                        "{\"contactId\":\"" + phoneContactId + "\",\"title\":\"Renamed\",\"type\":\"MAIL\"}"))
                .andExpect(status().isOk()).andReturn();

        assertThat(read(result, "$.data.title")).isEqualTo("Renamed");
        assertThat(read(result, "$.data.type")).isEqualTo("CALL");
        assertThat(mailsOfTenant()).isZero();
    }

    @Test
    void admin_updateChannelWithMail_answers422AndChangesNothing() throws Exception {
        String callId = createChannel("WHATSAPP", phoneContactId);

        mockMvc.perform(in(tenant, put(OUTREACHES_PATH + "/" + callId), admin.jwt(),
                        outreachUpdateBody(phoneContactId, "Renamed")))
                .andExpect(status().isUnprocessableEntity());

        assertThat(count("SELECT count(*) FROM otak.outreaches WHERE id = ?::uuid AND title = 'Reach out'", callId))
                .isEqualTo(1L);
        assertThat(mailsOfTenant()).isZero();
    }

    @Test
    void admin_updateMailWithoutMail_answers422() throws Exception {
        String outreachId = read(createOutreachResult(tenant, admin, relationId, mailContactId, "Mail"), "$.data.id");

        mockMvc.perform(in(tenant, put(OUTREACHES_PATH + "/" + outreachId), admin.jwt(),
                        "{\"contactId\":\"" + mailContactId + "\",\"title\":\"Renamed\"}"))
                .andExpect(status().isUnprocessableEntity());

        assertThat(count("SELECT count(*) FROM otak.outreaches WHERE id = ?::uuid AND title = 'Mail'", outreachId))
                .isEqualTo(1L);
    }

    @Test
    void admin_sendOfAChannel_answers422OTK_VAL_014AndChangesNothing() throws Exception {
        String callId = createChannel("CALL", phoneContactId);

        MvcResult send = mockMvc.perform(in(tenant, post(OUTREACHES_PATH + "/" + callId + "/send"), admin.jwt(),
                        "{\"mailSenderId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnprocessableEntity()).andReturn();

        assertThat(send.getResponse().getContentAsString()).contains("OTK_VAL_014");
        assertThat(statusOf(callId)).isEqualTo("DRAFT");
        assertThat(mailsOfTenant()).isZero();
    }

    @Test
    void admin_completeOfACall_recordsItDoneWithTheTrimmedPhoneAndNoMailOrSender() throws Exception {
        String callId = createChannel("CALL", phoneContactId);

        MvcResult done = mockMvc.perform(in(tenant, post(OUTREACHES_PATH + "/" + callId + "/complete"),
                        admin.jwt(), null))
                .andExpect(status().isOk()).andReturn();

        assertThat(read(done, "$.data.status")).isEqualTo("SENT");
        assertThat(read(done, "$.data.sentTo")).isEqualTo(PHONE);
        assertThat(read(done, "$.data.mail")).isEqualTo("null");
        assertThat(count("SELECT count(*) FROM otak.outreaches WHERE id = ?::uuid AND status::text = 'SENT' "
                + "AND sent_at IS NOT NULL AND sent_to = ? AND mail_id IS NULL AND mail_sender_id IS NULL",
                callId, PHONE)).isEqualTo(1L);
    }

    @Test
    void admin_completeOfAWhatsapp_recordsItDone() throws Exception {
        String whatsappId = createChannel("WHATSAPP", phoneContactId);

        MvcResult done = mockMvc.perform(in(tenant, post(OUTREACHES_PATH + "/" + whatsappId + "/complete"),
                        admin.jwt(), null))
                .andExpect(status().isOk()).andReturn();

        assertThat(read(done, "$.data.status")).isEqualTo("SENT");
        assertThat(read(done, "$.data.type")).isEqualTo("WHATSAPP");
    }

    @Test
    void admin_completeWhenTheContactHasNoPhone_answers422OTK_VAL_015AndStaysDraft() throws Exception {
        String callId = createChannel("CALL", mailContactId);

        MvcResult result = mockMvc.perform(in(tenant, post(OUTREACHES_PATH + "/" + callId + "/complete"),
                        admin.jwt(), null))
                .andExpect(status().isUnprocessableEntity()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("OTK_VAL_015");
        assertThat(statusOf(callId)).isEqualTo("DRAFT");
    }

    @Test
    void admin_completeOfAMailOutreach_answers422OTK_VAL_014AndStaysDraft() throws Exception {
        String outreachId = read(createOutreachResult(tenant, admin, relationId, mailContactId, "Mail"), "$.data.id");

        MvcResult result = mockMvc.perform(in(tenant, post(OUTREACHES_PATH + "/" + outreachId + "/complete"),
                        admin.jwt(), null))
                .andExpect(status().isUnprocessableEntity()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("OTK_VAL_014");
        assertThat(statusOf(outreachId)).isEqualTo("DRAFT");
    }

    @Test
    void admin_completeOfASentOutreach_answers409OTK_STA_011() throws Exception {
        String callId = createChannel("CALL", phoneContactId);
        mockMvc.perform(in(tenant, post(OUTREACHES_PATH + "/" + callId + "/complete"), admin.jwt(), null))
                .andExpect(status().isOk());

        MvcResult again = mockMvc.perform(in(tenant, post(OUTREACHES_PATH + "/" + callId + "/complete"),
                        admin.jwt(), null))
                .andExpect(status().isConflict()).andReturn();

        assertThat(again.getResponse().getContentAsString()).contains("OTK_STA_011");
        assertThat(statusOf(callId)).isEqualTo("SENT");
    }

    @Test
    void admin_completeOfAnUnknownOutreach_answers404() throws Exception {
        mockMvc.perform(in(tenant, post(OUTREACHES_PATH + "/" + UUID.randomUUID() + "/complete"), admin.jwt(), null))
                .andExpect(status().isNotFound());
    }

    @Test
    void member_complete_answers403ORG_SEC_003AndChangesNothing() throws Exception {
        String callId = createChannel("CALL", phoneContactId);

        MvcResult result = mockMvc.perform(in(tenant, post(OUTREACHES_PATH + "/" + callId + "/complete"),
                        member.jwt(), null))
                .andExpect(status().isForbidden()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("ORG_SEC_003");
        assertThat(statusOf(callId)).isEqualTo("DRAFT");
    }

    @Test
    void admin_editsAndDeleteOfACompletedChannel_answer409AndDeleteOfADraftChannelWorks() throws Exception {
        String doneId = createChannel("CALL", phoneContactId);
        String draftId = createChannel("WHATSAPP", phoneContactId);
        mockMvc.perform(in(tenant, post(OUTREACHES_PATH + "/" + doneId + "/complete"), admin.jwt(), null))
                .andExpect(status().isOk());

        mockMvc.perform(in(tenant, put(OUTREACHES_PATH + "/" + doneId), admin.jwt(),
                "{\"contactId\":\"" + phoneContactId + "\",\"title\":\"Late\"}")).andExpect(status().isConflict());
        mockMvc.perform(in(tenant, delete(OUTREACHES_PATH + "/" + doneId), admin.jwt(), null))
                .andExpect(status().isConflict());
        mockMvc.perform(in(tenant, delete(OUTREACHES_PATH + "/" + draftId), admin.jwt(), null))
                .andExpect(status().isOk());

        assertThat(outreachesOfTenant()).isEqualTo(1L);
    }
}
