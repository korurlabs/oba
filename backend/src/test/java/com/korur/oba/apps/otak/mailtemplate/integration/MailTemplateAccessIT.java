package com.korur.oba.apps.otak.mailtemplate.integration;

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

// 4. Internal
import com.korur.oba.apps.otak.documenttemplate.integration.AbstractOtakDocumentsIT;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * Proves, through the real security chain, that a MEMBER reads mail templates but cannot write
 * them, that a GUEST reads nothing, that an ADMIN writes them under the field and name rules,
 * and that deleting a template keeps the mails copied from it.
 */
class MailTemplateAccessIT extends AbstractOtakDocumentsIT {

    private Tenant tenant;
    private Person member;
    private Person admin;
    private Person guest;
    private String templateName;
    private String templateId;

    @BeforeEach
    void setUpTenantAndTemplate() throws Exception {
        tenant = newTenant();
        member = signIn(tenant, "otakmember");
        admin = signInAs(tenant, OrganizationMemberRole.ADMIN, "otakadmin");
        guest = signInAs(tenant, OrganizationMemberRole.GUEST, "otakguest");
        templateName = "Mail " + uniqueSuffix();
        templateId = createMailTemplate(tenant, admin, templateName);
    }

    private long templatesOfTenant() {
        return count("SELECT count(*) FROM otak.mail_templates WHERE organization_id = ?::uuid", tenant.id());
    }

    private static String body(String name, String language, String subject, String text) {
        return "{\"name\":\"" + name + "\",\"language\":\"" + language + "\",\"subject\":\"" + subject
                + "\",\"body\":\"" + text + "\"}";
    }

    @Test
    void member_listAndDetail_answer200() throws Exception {
        MvcResult list = mockMvc.perform(in(tenant, get(MAIL_TEMPLATES_PATH), member.jwt(), null))
                .andExpect(status().isOk()).andReturn();
        MvcResult detail = mockMvc.perform(in(tenant, get(MAIL_TEMPLATES_PATH + "/" + templateId), member.jwt(), null))
                .andExpect(status().isOk()).andReturn();

        assertThat(list.getResponse().getContentAsString()).contains(templateId).contains(templateName);
        assertThat(read(detail, "$.data.name")).isEqualTo(templateName);
        assertThat(read(detail, "$.data.subject")).isEqualTo("Subject");
        assertThat(read(detail, "$.data.body")).isEqualTo("Body");
    }

    @Test
    void member_listWithFilters_returnsOnlyTheMatchingTemplates() throws Exception {
        MvcResult matching = mockMvc.perform(in(tenant, get(MAIL_TEMPLATES_PATH)
                        .param("language", "NL").param("search", templateName.toLowerCase()), member.jwt(), null))
                .andExpect(status().isOk()).andReturn();
        MvcResult otherLanguage = mockMvc.perform(in(tenant, get(MAIL_TEMPLATES_PATH).param("language", "EN"),
                member.jwt(), null)).andExpect(status().isOk()).andReturn();
        MvcResult otherName = mockMvc.perform(in(tenant, get(MAIL_TEMPLATES_PATH).param("search", "zzz-no-match"),
                member.jwt(), null)).andExpect(status().isOk()).andReturn();

        assertThat(matching.getResponse().getContentAsString()).contains(templateId);
        assertThat(otherLanguage.getResponse().getContentAsString()).doesNotContain(templateId);
        assertThat(otherName.getResponse().getContentAsString()).doesNotContain(templateId);
    }

    @Test
    void guest_listAndDetail_answer403() throws Exception {
        mockMvc.perform(in(tenant, get(MAIL_TEMPLATES_PATH), guest.jwt(), null)).andExpect(status().isForbidden());
        mockMvc.perform(in(tenant, get(MAIL_TEMPLATES_PATH + "/" + templateId), guest.jwt(), null))
                .andExpect(status().isForbidden());
    }

    @Test
    void member_post_answers403AndStoresNothing() throws Exception {
        mockMvc.perform(in(tenant, post(MAIL_TEMPLATES_PATH), member.jwt(), mailTemplateBody("By member")))
                .andExpect(status().isForbidden());

        assertThat(templatesOfTenant()).isEqualTo(1L);
    }

    @Test
    void member_putAndDelete_answer403AndChangeNothing() throws Exception {
        mockMvc.perform(in(tenant, put(MAIL_TEMPLATES_PATH + "/" + templateId), member.jwt(),
                mailTemplateBody("Renamed"))).andExpect(status().isForbidden());
        mockMvc.perform(in(tenant, delete(MAIL_TEMPLATES_PATH + "/" + templateId), member.jwt(), null))
                .andExpect(status().isForbidden());

        assertThat(count("SELECT count(*) FROM otak.mail_templates WHERE id = ?::uuid AND name = ?",
                templateId, templateName)).isEqualTo(1L);
    }

    @Test
    void guest_post_answers403() throws Exception {
        mockMvc.perform(in(tenant, post(MAIL_TEMPLATES_PATH), guest.jwt(), mailTemplateBody("By guest")))
                .andExpect(status().isForbidden());

        assertThat(templatesOfTenant()).isEqualTo(1L);
    }

    @Test
    void admin_post_answers201WithTheTrimmedNameAndTheFullContent() throws Exception {
        MvcResult result = mockMvc.perform(in(tenant, post(MAIL_TEMPLATES_PATH), admin.jwt(),
                        body("  Intro  ", "EN", "Hello", "Dear reader")))
                .andExpect(status().isCreated()).andReturn();

        assertThat(read(result, "$.data.name")).isEqualTo("Intro");
        assertThat(read(result, "$.data.language")).isEqualTo("EN");
        assertThat(read(result, "$.data.subject")).isEqualTo("Hello");
        assertThat(read(result, "$.data.body")).isEqualTo("Dear reader");
        assertThat(templatesOfTenant()).isEqualTo(2L);
    }

    @Test
    void admin_putAndDelete_answer200() throws Exception {
        MvcResult updated = mockMvc.perform(in(tenant, put(MAIL_TEMPLATES_PATH + "/" + templateId), admin.jwt(),
                        body("Renamed " + uniqueSuffix(), "EN", "New subject", "New body")))
                .andExpect(status().isOk()).andReturn();
        mockMvc.perform(in(tenant, delete(MAIL_TEMPLATES_PATH + "/" + templateId), admin.jwt(), null))
                .andExpect(status().isOk());

        assertThat(read(updated, "$.data.name")).startsWith("Renamed");
        assertThat(read(updated, "$.data.subject")).isEqualTo("New subject");
        assertThat(templatesOfTenant()).isZero();
    }

    @Test
    void admin_putKeepingItsOwnName_answers200() throws Exception {
        mockMvc.perform(in(tenant, put(MAIL_TEMPLATES_PATH + "/" + templateId), admin.jwt(),
                body(templateName.toUpperCase(), "NL", "Other", "Other"))).andExpect(status().isOk());
    }

    @Test
    void admin_postWithoutSubjectOrBody_answers422AndStoresNothing() throws Exception {
        mockMvc.perform(in(tenant, post(MAIL_TEMPLATES_PATH), admin.jwt(),
                "{\"name\":\"No subject\",\"language\":\"NL\",\"body\":\"Body\"}"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(in(tenant, post(MAIL_TEMPLATES_PATH), admin.jwt(),
                "{\"name\":\"No body\",\"language\":\"NL\",\"subject\":\"Subject\"}"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(in(tenant, post(MAIL_TEMPLATES_PATH), admin.jwt(), body("Blank", "NL", " ", "Body")))
                .andExpect(status().isUnprocessableEntity());

        assertThat(templatesOfTenant()).isEqualTo(1L);
    }

    @Test
    void admin_postWithBlankName_answers422AndStoresNothing() throws Exception {
        mockMvc.perform(in(tenant, post(MAIL_TEMPLATES_PATH), admin.jwt(), mailTemplateBody(" ")))
                .andExpect(status().isUnprocessableEntity());

        assertThat(templatesOfTenant()).isEqualTo(1L);
    }

    @Test
    void admin_postWithTakenNameDifferingInCase_answers409() throws Exception {
        MvcResult result = mockMvc.perform(in(tenant, post(MAIL_TEMPLATES_PATH), admin.jwt(),
                mailTemplateBody(templateName.toUpperCase()))).andExpect(status().isConflict()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("OTK_STA_014");
        assertThat(templatesOfTenant()).isEqualTo(1L);
    }

    @Test
    void admin_postWithTheSameNameInAnotherLanguage_answers201() throws Exception {
        mockMvc.perform(in(tenant, post(MAIL_TEMPLATES_PATH), admin.jwt(),
                body(templateName, "EN", "Subject", "Body"))).andExpect(status().isCreated());
    }

    @Test
    void admin_putToTheNameOfAnotherTemplate_answers409AndChangesNothing() throws Exception {
        String otherName = "Other " + uniqueSuffix();
        createMailTemplate(tenant, admin, otherName);

        MvcResult result = mockMvc.perform(in(tenant, put(MAIL_TEMPLATES_PATH + "/" + templateId), admin.jwt(),
                mailTemplateBody(otherName))).andExpect(status().isConflict()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("OTK_STA_014");
        assertThat(count("SELECT count(*) FROM otak.mail_templates WHERE id = ?::uuid AND name = ?",
                templateId, templateName)).isEqualTo(1L);
    }

    @Test
    void admin_getUnknownId_answers404WithTheMailTemplateCode() throws Exception {
        MvcResult result = mockMvc.perform(in(tenant, get(MAIL_TEMPLATES_PATH + "/" + java.util.UUID.randomUUID()),
                admin.jwt(), null)).andExpect(status().isNotFound()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("OTK_NOT_010");
    }

    @Test
    void admin_deleteOfATemplateUsedByAMail_keepsTheMailAndClearsItsLink() throws Exception {
        String relationId = createRelation(tenant, admin, "Relation " + uniqueSuffix());
        String contactId = createContact(tenant, admin, relationId, "jan" + uniqueSuffix() + "@fabrikam.example");
        MvcResult outreach = mockMvc.perform(in(tenant, post(OUTREACHES_PATH), admin.jwt(),
                        "{\"relationId\":\"" + relationId + "\",\"contactId\":\"" + contactId
                                + "\",\"type\":\"MAIL\",\"title\":\"From template\",\"mailTemplateId\":\""
                                + templateId + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        String mailId = read(outreach, "$.data.mail.id");

        mockMvc.perform(in(tenant, delete(MAIL_TEMPLATES_PATH + "/" + templateId), admin.jwt(), null))
                .andExpect(status().isOk());

        assertThat(count("SELECT count(*) FROM otak.mails WHERE id = ?::uuid AND mail_template_id IS NULL "
                + "AND subject = 'Subject' AND body = 'Body'", mailId)).isEqualTo(1L);
    }
}
