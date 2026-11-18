package com.korur.oba.apps.otak.documenttemplate.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 4. Internal
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves, against the migrated PostgreSQL schema and the real security chain, that the document
 * templates of an organization are closed to every other organization: a user who sits in two
 * organizations reaches a template only with the header of the organization that owns it.
 */
class DocumentTemplateOrganizationIsolationIT extends AbstractOtakDocumentsIT {

    private static final String TEMPLATE_NOT_FOUND_CODE = "OTK_NOT_007";

    private Tenant tenantA;
    private Tenant tenantB;
    private Person user;
    private String templateName;
    private String templateId;

    @BeforeEach
    void setUpTenantsAndTemplateOfA() throws Exception {
        tenantA = newTenant();
        tenantB = newTenant();
        user = signIn("kc-" + uniqueSuffix(), "otaktemplate" + uniqueSuffix().substring(0, 6) + "@" + tenantA.domain());
        join(tenantA, user, OrganizationMemberRole.ADMIN);
        join(tenantB, user, OrganizationMemberRole.ADMIN);

        templateName = "Template of A " + uniqueSuffix();
        templateId = createFileTemplate(tenantA, user, templateName);
    }

    private void assertAnswersNotFound(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isNotFound()).andReturn();
        assertThat(result.getResponse().getContentAsString())
                .contains(TEMPLATE_NOT_FOUND_CODE)
                .doesNotContain(templateName);
    }

    @Test
    void getWithHeaderOfAnotherOrganization_answers404WithoutData() throws Exception {
        assertAnswersNotFound(in(tenantB, get(TEMPLATES_PATH + "/" + templateId), user.jwt(), null));
    }

    @Test
    void putWithHeaderOfAnotherOrganization_answers404AndChangesNothing() throws Exception {
        assertAnswersNotFound(in(tenantB, put(TEMPLATES_PATH + "/" + templateId), user.jwt(),
                fileTemplateBody("Hijacked")));

        assertThat(count("SELECT count(*) FROM otak.document_templates WHERE id = ?::uuid AND name = ?",
                templateId, templateName)).isEqualTo(1L);
    }

    @Test
    void deleteWithHeaderOfAnotherOrganization_answers404AndDeletesNothing() throws Exception {
        assertAnswersNotFound(in(tenantB, delete(TEMPLATES_PATH + "/" + templateId), user.jwt(), null));

        assertThat(count("SELECT count(*) FROM otak.document_templates WHERE id = ?::uuid", templateId))
                .isEqualTo(1L);
    }

    @Test
    void listWithHeaderOfAnotherOrganization_showsNothingOfTheTemplateOfA() throws Exception {
        MvcResult other = mockMvc.perform(in(tenantB, get(TEMPLATES_PATH), user.jwt(), null))
                .andExpect(status().isOk()).andReturn();
        MvcResult own = mockMvc.perform(in(tenantA, get(TEMPLATES_PATH), user.jwt(), null))
                .andExpect(status().isOk()).andReturn();

        assertThat(other.getResponse().getContentAsString()).doesNotContain(templateId).doesNotContain(templateName);
        assertThat(own.getResponse().getContentAsString()).contains(templateId).contains(templateName);
    }

    @Test
    void sameNameInAnotherOrganization_isNotATakenName() throws Exception {
        mockMvc.perform(in(tenantB, post(TEMPLATES_PATH), user.jwt(), fileTemplateBody(templateName)))
                .andExpect(status().isCreated());
    }

    @Test
    void getWithHeaderOfTheOwningOrganization_sameUser_answers200WithData() throws Exception {
        MvcResult result = mockMvc.perform(in(tenantA, get(TEMPLATES_PATH + "/" + templateId), user.jwt(), null))
                .andExpect(status().isOk()).andReturn();

        assertThat(read(result, "$.data.name")).isEqualTo(templateName);
    }
}
