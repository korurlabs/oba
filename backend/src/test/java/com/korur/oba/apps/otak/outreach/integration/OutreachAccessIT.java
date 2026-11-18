package com.korur.oba.apps.otak.outreach.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

// 4. Internal
import com.korur.oba.apps.otak.documenttemplate.integration.AbstractOtakDocumentsIT;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves, through the real security chain, that a MEMBER reads outreaches but cannot write or
 * send them, that a GUEST reads nothing, that an ADMIN writes them under the validation and
 * lock rules, that every outreach owns its own mail, and that a sent outreach can no longer
 * change.
 */
class OutreachAccessIT extends AbstractOtakDocumentsIT {

    private Tenant tenant;
    private Person member;
    private Person admin;
    private Person guest;
    private String relationId;
    private String contactId;
    private String title;
    private String outreachId;
    private String mailId;

    @BeforeEach
    void setUpTenantAndOutreach() throws Exception {
        tenant = newTenant();
        member = signIn(tenant, "otakmember");
        admin = signInAs(tenant, OrganizationMemberRole.ADMIN, "otakadmin");
        guest = signInAs(tenant, OrganizationMemberRole.GUEST, "otakguest");
        relationId = createRelation(tenant, admin, "Relation " + uniqueSuffix());
        contactId = createContact(tenant, admin, relationId, "jan" + uniqueSuffix() + "@fabrikam.example");
        title = "Outreach " + uniqueSuffix();
        MvcResult created = createOutreachResult(tenant, admin, relationId, contactId, title);
        outreachId = read(created, "$.data.id");
        mailId = read(created, "$.data.mail.id");
    }

    private long outreachesOfTenant() {
        return count("SELECT count(*) FROM otak.outreaches WHERE organization_id = ?::uuid", tenant.id());
    }

    @Test
    void member_listAndDetail_answer200() throws Exception {
        MvcResult list = mockMvc.perform(in(tenant, get(OUTREACHES_PATH), member.jwt(), null))
                .andExpect(status().isOk()).andReturn();
        MvcResult detail = mockMvc.perform(in(tenant, get(OUTREACHES_PATH + "/" + outreachId), member.jwt(), null))
                .andExpect(status().isOk()).andReturn();

        assertThat(list.getResponse().getContentAsString()).contains(outreachId).contains(title);
        assertThat(read(detail, "$.data.title")).isEqualTo(title);
        assertThat(read(detail, "$.data.status")).isEqualTo("DRAFT");
    }

    @Test
    void guest_listAndDetail_answer403() throws Exception {
        mockMvc.perform(in(tenant, get(OUTREACHES_PATH), guest.jwt(), null)).andExpect(status().isForbidden());
        mockMvc.perform(in(tenant, get(OUTREACHES_PATH + "/" + outreachId), guest.jwt(), null))
                .andExpect(status().isForbidden());
    }

    @Test
    void member_writes_answer403AndChangeNothing() throws Exception {
        String body = outreachBody(relationId, contactId, "By member");

        mockMvc.perform(in(tenant, post(OUTREACHES_PATH), member.jwt(), body)).andExpect(status().isForbidden());
        mockMvc.perform(in(tenant, put(OUTREACHES_PATH + "/" + outreachId), member.jwt(),
                outreachUpdateBody(contactId, "Renamed"))).andExpect(status().isForbidden());
        mockMvc.perform(in(tenant, delete(OUTREACHES_PATH + "/" + outreachId), member.jwt(), null))
                .andExpect(status().isForbidden());

        assertThat(outreachesOfTenant()).isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM otak.outreaches WHERE id = ?::uuid AND title = ?",
                outreachId, title)).isEqualTo(1L);
    }

    @Test
    void admin_completeOfACall_recordsItDoneWithThePhoneAndNoMail() throws Exception {
        String phoneContactId = createContactWithPhone(tenant, admin, relationId, " +31 6 1234 5678 ");
        String callId = read(mockMvc.perform(in(tenant, post(OUTREACHES_PATH), admin.jwt(),
                        "{\"relationId\":\"" + relationId + "\",\"contactId\":\"" + phoneContactId
                                + "\",\"type\":\"CALL\",\"title\":\"Call\"}"))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");

        mockMvc.perform(in(tenant, post(OUTREACHES_PATH + "/" + callId + "/complete"), member.jwt(), null))
                .andExpect(status().isForbidden());
        MvcResult done = mockMvc.perform(in(tenant, post(OUTREACHES_PATH + "/" + callId + "/complete"),
                        admin.jwt(), null))
                .andExpect(status().isOk()).andReturn();

        assertThat(read(done, "$.data.status")).isEqualTo("SENT");
        assertThat(read(done, "$.data.sentTo")).isEqualTo("+31 6 1234 5678");
        assertThat(count("SELECT count(*) FROM otak.outreaches WHERE id = ?::uuid AND type::text = 'CALL' "
                + "AND mail_id IS NULL AND mail_sender_id IS NULL AND sent_at IS NOT NULL", callId)).isEqualTo(1L);
    }

    @Test
    void member_send_answers403AndClaimsNothing() throws Exception {
        mockMvc.perform(in(tenant, post(OUTREACHES_PATH + "/" + outreachId + "/send"), member.jwt(),
                "{\"mailSenderId\":\"" + java.util.UUID.randomUUID() + "\"}")).andExpect(status().isForbidden());

        assertThat(count("SELECT count(*) FROM otak.outreaches WHERE id = ?::uuid AND status::text = 'DRAFT'",
                outreachId)).isEqualTo(1L);
    }

    @Test
    void admin_postWithMailTemplate_createsTheOutreachWithACopyOfTheTemplate() throws Exception {
        String templateId = createMailTemplate(tenant, admin, "Template " + uniqueSuffix());

        MvcResult result = mockMvc.perform(in(tenant, post(OUTREACHES_PATH), admin.jwt(),
                "{\"relationId\":\"" + relationId + "\",\"contactId\":\"" + contactId
                        + "\",\"type\":\"MAIL\",\"title\":\"From template\",\"mailTemplateId\":\"" + templateId + "\"}"))
                .andExpect(status().isCreated()).andReturn();

        assertThat(read(result, "$.data.mail.mailTemplateId")).isEqualTo(templateId);
        assertThat(read(result, "$.data.mail.id")).isNotEqualTo(templateId).isNotEqualTo(mailId);
        assertThat(read(result, "$.data.mail.subject")).isEqualTo("Subject");
        assertThat(outreachesOfTenant()).isEqualTo(2L);
    }

    @Test
    void admin_postWithTemplateAndMail_answers422() throws Exception {
        String templateId = createMailTemplate(tenant, admin, "Template " + uniqueSuffix());

        mockMvc.perform(in(tenant, post(OUTREACHES_PATH), admin.jwt(),
                "{\"relationId\":\"" + relationId + "\",\"contactId\":\"" + contactId
                        + "\",\"type\":\"MAIL\",\"title\":\"Both\",\"mailTemplateId\":\"" + templateId
                        + "\",\"mail\":{\"language\":\"NL\",\"subject\":\"S\",\"body\":\"B\"}}"))
                .andExpect(status().isUnprocessableEntity());

        assertThat(outreachesOfTenant()).isEqualTo(1L);
    }

    @Test
    void admin_postWithNeitherTemplateNorMail_answers422() throws Exception {
        mockMvc.perform(in(tenant, post(OUTREACHES_PATH), admin.jwt(),
                "{\"relationId\":\"" + relationId + "\",\"contactId\":\"" + contactId + "\",\"type\":\"MAIL\",\"title\":\"None\"}"))
                .andExpect(status().isUnprocessableEntity());

        assertThat(outreachesOfTenant()).isEqualTo(1L);
    }

    @Test
    void admin_postWithBlankTitle_answers422AndStoresNothing() throws Exception {
        mockMvc.perform(in(tenant, post(OUTREACHES_PATH), admin.jwt(), outreachBody(relationId, contactId, " ")))
                .andExpect(status().isUnprocessableEntity());

        assertThat(outreachesOfTenant()).isEqualTo(1L);
    }

    @Test
    void admin_putOfADraft_answers200AndChangesTheTitleAndTheMail() throws Exception {
        MvcResult result = mockMvc.perform(in(tenant, put(OUTREACHES_PATH + "/" + outreachId), admin.jwt(),
                "{\"contactId\":\"" + contactId + "\",\"title\":\"Renamed\",\"mail\":{\"language\":\"EN\","
                        + "\"subject\":\"New subject\",\"body\":\"New body\"}}"))
                .andExpect(status().isOk()).andReturn();

        assertThat(read(result, "$.data.title")).isEqualTo("Renamed");
        assertThat(read(result, "$.data.mail.id")).isEqualTo(mailId);
        assertThat(read(result, "$.data.mail.subject")).isEqualTo("New subject");
        assertThat(count("SELECT count(*) FROM otak.mails WHERE id = ?::uuid AND subject = 'New subject' "
                + "AND body = 'New body' AND language::text = 'EN'", mailId)).isEqualTo(1L);
    }

    @Test
    void admin_postWithADocumentTemplateWithoutFiles_answers422AndStoresNothing() throws Exception {
        String templateId = createFileTemplate(tenant, admin, "Proposal " + uniqueSuffix());
        String body = outreachBody(relationId, contactId, "With template");
        body = body.substring(0, body.length() - 1) + ",\"documentTemplateIds\":[\"" + templateId + "\"]}";

        MvcResult result = mockMvc.perform(in(tenant, post(OUTREACHES_PATH), admin.jwt(), body))
                .andExpect(status().isUnprocessableEntity()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("OTK_VAL_012");
        assertThat(outreachesOfTenant()).isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM otak.mails WHERE organization_id = ?::uuid", tenant.id()))
                .isEqualTo(1L);
    }

    @Test
    void admin_deleteOfADraft_removesTheOutreachAndItsMail() throws Exception {
        mockMvc.perform(in(tenant, delete(OUTREACHES_PATH + "/" + outreachId), admin.jwt(), null))
                .andExpect(status().isOk());

        assertThat(outreachesOfTenant()).isZero();
        assertThat(count("SELECT count(*) FROM otak.mails WHERE id = ?::uuid", mailId)).isZero();
    }

    @Test
    void admin_sendOfASentOutreach_answers409AndKeepsTheSentState() throws Exception {
        markSent(outreachId);

        MvcResult result = mockMvc.perform(in(tenant, post(OUTREACHES_PATH + "/" + outreachId + "/send"),
                admin.jwt(), "{\"mailSenderId\":\"" + java.util.UUID.randomUUID() + "\"}"))
                .andExpect(status().isConflict()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("OTK_STA_011");
        assertThat(count("SELECT count(*) FROM otak.outreaches WHERE id = ?::uuid AND status::text = 'SENT'",
                outreachId)).isEqualTo(1L);
    }

    @Test
    void admin_sendWithoutSender_answers422() throws Exception {
        mockMvc.perform(in(tenant, post(OUTREACHES_PATH + "/" + outreachId + "/send"), admin.jwt(), "{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void admin_editsOfASentOutreach_answer409() throws Exception {
        markSent(outreachId);

        MvcResult update = mockMvc.perform(in(tenant, put(OUTREACHES_PATH + "/" + outreachId), admin.jwt(),
                outreachUpdateBody(contactId, "Late")))
                .andExpect(status().isConflict()).andReturn();
        mockMvc.perform(in(tenant, delete(OUTREACHES_PATH + "/" + outreachId), admin.jwt(), null))
                .andExpect(status().isConflict());

        assertThat(update.getResponse().getContentAsString()).contains("OTK_STA_011");
        assertThat(count("SELECT count(*) FROM otak.outreaches WHERE id = ?::uuid AND title = ?",
                outreachId, title)).isEqualTo(1L);
    }
}
