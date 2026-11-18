package com.korur.oba.apps.otak.mailtemplate.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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
 * Proves, against the migrated PostgreSQL schema and the real security chain, that the mail
 * templates of an organization are closed to every other organization, also through the
 * outreach copy and the mail of an outreach: a user who sits in two organizations reaches a
 * template only with the header of the organization that owns it.
 */
class MailTemplateOrganizationIsolationIT extends AbstractOtakDocumentsIT {

    private static final String MAIL_TEMPLATE_NOT_FOUND_CODE = "OTK_NOT_010";

    private Tenant tenantA;
    private Tenant tenantB;
    private Person user;
    private String templateName;
    private String templateId;

    @BeforeEach
    void setUpTenantsAndTemplateOfA() throws Exception {
        tenantA = newTenant();
        tenantB = newTenant();
        user = signIn("kc-" + uniqueSuffix(), "otakmail" + uniqueSuffix().substring(0, 6) + "@" + tenantA.domain());
        join(tenantA, user, OrganizationMemberRole.ADMIN);
        join(tenantB, user, OrganizationMemberRole.ADMIN);

        templateName = "Template of A " + uniqueSuffix();
        templateId = createMailTemplate(tenantA, user, templateName);
    }

    private void assertAnswersNotFound(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isNotFound()).andReturn();
        assertThat(result.getResponse().getContentAsString())
                .contains(MAIL_TEMPLATE_NOT_FOUND_CODE)
                .doesNotContain(templateName);
    }

    @Test
    void getWithHeaderOfAnotherOrganization_answers404WithoutData() throws Exception {
        assertAnswersNotFound(in(tenantB, get(MAIL_TEMPLATES_PATH + "/" + templateId), user.jwt(), null));
    }

    @Test
    void putWithHeaderOfAnotherOrganization_answers404AndChangesNothing() throws Exception {
        assertAnswersNotFound(in(tenantB, put(MAIL_TEMPLATES_PATH + "/" + templateId), user.jwt(),
                mailTemplateBody("Hijacked")));

        assertThat(count("SELECT count(*) FROM otak.mail_templates WHERE id = ?::uuid AND name = ?",
                templateId, templateName)).isEqualTo(1L);
    }

    @Test
    void deleteWithHeaderOfAnotherOrganization_answers404AndDeletesNothing() throws Exception {
        assertAnswersNotFound(in(tenantB, delete(MAIL_TEMPLATES_PATH + "/" + templateId), user.jwt(), null));

        assertThat(count("SELECT count(*) FROM otak.mail_templates WHERE id = ?::uuid", templateId)).isEqualTo(1L);
    }

    @Test
    void listWithHeaderOfAnotherOrganization_showsNothingOfTheTemplateOfA() throws Exception {
        MvcResult other = mockMvc.perform(in(tenantB, get(MAIL_TEMPLATES_PATH), user.jwt(), null))
                .andExpect(status().isOk()).andReturn();
        MvcResult own = mockMvc.perform(in(tenantA, get(MAIL_TEMPLATES_PATH), user.jwt(), null))
                .andExpect(status().isOk()).andReturn();

        assertThat(other.getResponse().getContentAsString()).doesNotContain(templateId).doesNotContain(templateName);
        assertThat(own.getResponse().getContentAsString()).contains(templateId).contains(templateName);
    }

    @Test
    void sameNameInAnotherOrganization_isNotATakenName() throws Exception {
        mockMvc.perform(in(tenantB, post(MAIL_TEMPLATES_PATH), user.jwt(), mailTemplateBody(templateName)))
                .andExpect(status().isCreated());
    }

    @Test
    void getWithHeaderOfTheOwningOrganization_sameUser_answers200WithData() throws Exception {
        MvcResult result = mockMvc.perform(in(tenantA, get(MAIL_TEMPLATES_PATH + "/" + templateId), user.jwt(), null))
                .andExpect(status().isOk()).andReturn();

        assertThat(read(result, "$.data.name")).isEqualTo(templateName);
    }

    @Test
    void outreachOfBWithTemplateOfA_answers404OTK_NOT_010AndWritesNothing() throws Exception {
        String relationId = createRelation(tenantB, user, "Relation " + uniqueSuffix());
        String contactId = createContact(tenantB, user, relationId, "jan" + uniqueSuffix() + "@fabrikam.example");

        MvcResult result = mockMvc.perform(in(tenantB, post(OUTREACHES_PATH), user.jwt(),
                        "{\"relationId\":\"" + relationId + "\",\"contactId\":\"" + contactId
                                + "\",\"type\":\"MAIL\",\"title\":\"Foreign template\",\"mailTemplateId\":\""
                                + templateId + "\"}"))
                .andExpect(status().isNotFound()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains(MAIL_TEMPLATE_NOT_FOUND_CODE);
        assertThat(count("SELECT count(*) FROM otak.outreaches WHERE organization_id = ?::uuid", tenantB.id()))
                .isZero();
        assertThat(count("SELECT count(*) FROM otak.mails WHERE organization_id = ?::uuid", tenantB.id())).isZero();
    }

    @Test
    void mailOfAnOutreachOfA_isNotReachableThroughTheOutreachWithTheHeaderOfB() throws Exception {
        String relationId = createRelation(tenantA, user, "Relation " + uniqueSuffix());
        String contactId = createContact(tenantA, user, relationId, "jan" + uniqueSuffix() + "@fabrikam.example");
        MvcResult created = createOutreachResult(tenantA, user, relationId, contactId, "Outreach " + uniqueSuffix());
        String outreachId = read(created, "$.data.id");
        String mailId = read(created, "$.data.mail.id");

        MvcResult detail = mockMvc.perform(in(tenantB, get(OUTREACHES_PATH + "/" + outreachId), user.jwt(), null))
                .andExpect(status().isNotFound()).andReturn();
        mockMvc.perform(in(tenantB, put(OUTREACHES_PATH + "/" + outreachId), user.jwt(),
                outreachUpdateBody(contactId, "Hijacked"))).andExpect(status().isNotFound());
        mockMvc.perform(in(tenantB, delete(OUTREACHES_PATH + "/" + outreachId), user.jwt(), null))
                .andExpect(status().isNotFound());

        assertThat(detail.getResponse().getContentAsString()).doesNotContain(mailId).doesNotContain("Subject");
        assertThat(count("SELECT count(*) FROM otak.mails WHERE id = ?::uuid AND organization_id = ?::uuid "
                + "AND subject = 'Subject'", mailId, tenantA.id())).isEqualTo(1L);
    }
}
