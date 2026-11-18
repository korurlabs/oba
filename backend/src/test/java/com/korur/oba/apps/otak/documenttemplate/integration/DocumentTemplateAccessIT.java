package com.korur.oba.apps.otak.documenttemplate.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 4. Internal
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * Proves, through the real security chain, that a MEMBER reads document templates but cannot
 * write them, that a GUEST reads nothing, and that an ADMIN writes them under the name and
 * content rules.
 */
class DocumentTemplateAccessIT extends AbstractOtakDocumentsIT {

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
        templateName = "Template " + uniqueSuffix();
        templateId = createFileTemplate(tenant, admin, templateName);
    }

    private long templatesOfTenant() {
        return count("SELECT count(*) FROM otak.document_templates WHERE organization_id = ?",
                tenant.organization().getId());
    }

    @Test
    void member_listAndDetail_answer200() throws Exception {
        MvcResult list = mockMvc.perform(in(tenant, get(TEMPLATES_PATH), member.jwt(), null))
                .andExpect(status().isOk()).andReturn();
        MvcResult detail = mockMvc.perform(in(tenant, get(TEMPLATES_PATH + "/" + templateId), member.jwt(), null))
                .andExpect(status().isOk()).andReturn();

        assertThat(list.getResponse().getContentAsString()).contains(templateId).contains(templateName);
        assertThat(read(detail, "$.data.name")).isEqualTo(templateName);
        assertThat(read(detail, "$.data.ready")).isEqualTo("false");
    }

    @Test
    void member_listWithFilters_returnsOnlyTheMatchingTemplates() throws Exception {
        MvcResult matching = mockMvc.perform(in(tenant, get(TEMPLATES_PATH)
                        .param("type", "NDA").param("language", "EN").param("search", templateName.toLowerCase()),
                member.jwt(), null)).andExpect(status().isOk()).andReturn();
        MvcResult otherType = mockMvc.perform(in(tenant, get(TEMPLATES_PATH).param("type", "PROPOSAL"),
                member.jwt(), null)).andExpect(status().isOk()).andReturn();

        assertThat(matching.getResponse().getContentAsString()).contains(templateId);
        assertThat(otherType.getResponse().getContentAsString()).doesNotContain(templateId);
    }

    @Test
    void guest_listAndDetail_answer403() throws Exception {
        mockMvc.perform(in(tenant, get(TEMPLATES_PATH), guest.jwt(), null)).andExpect(status().isForbidden());
        mockMvc.perform(in(tenant, get(TEMPLATES_PATH + "/" + templateId), guest.jwt(), null))
                .andExpect(status().isForbidden());
    }

    @Test
    void member_post_answers403AndStoresNothing() throws Exception {
        mockMvc.perform(in(tenant, post(TEMPLATES_PATH), member.jwt(), fileTemplateBody("By member")))
                .andExpect(status().isForbidden());

        assertThat(templatesOfTenant()).isEqualTo(1L);
    }

    @Test
    void member_put_answers403AndChangesNothing() throws Exception {
        mockMvc.perform(in(tenant, put(TEMPLATES_PATH + "/" + templateId), member.jwt(),
                fileTemplateBody("Renamed"))).andExpect(status().isForbidden());

        assertThat(count("SELECT count(*) FROM otak.document_templates WHERE id = ?::uuid AND name = ?",
                templateId, templateName)).isEqualTo(1L);
    }

    @Test
    void member_filesAndDelete_answer403AndChangeNothing() throws Exception {
        MockMultipartFile html = new MockMultipartFile("html", "a.html", "text/html", "<p/>".getBytes());
        MockMultipartFile pdf = new MockMultipartFile("pdf", "a.pdf", "application/pdf", "%PDF-1".getBytes());

        mockMvc.perform(in(tenant, multipart(HttpMethod.PUT, TEMPLATES_PATH + "/" + templateId + "/files")
                .file(html).file(pdf), member.jwt(), null)).andExpect(status().isForbidden());
        mockMvc.perform(in(tenant, delete(TEMPLATES_PATH + "/" + templateId), member.jwt(), null))
                .andExpect(status().isForbidden());

        assertThat(count("SELECT count(*) FROM otak.document_templates WHERE id = ?::uuid", templateId))
                .isEqualTo(1L);
    }

    @Test
    void admin_putAndDelete_answer200() throws Exception {
        MvcResult updated = mockMvc.perform(in(tenant, put(TEMPLATES_PATH + "/" + templateId), admin.jwt(),
                fileTemplateBody("Renamed " + uniqueSuffix()))).andExpect(status().isOk()).andReturn();
        mockMvc.perform(in(tenant, delete(TEMPLATES_PATH + "/" + templateId), admin.jwt(), null))
                .andExpect(status().isOk());

        assertThat(read(updated, "$.data.name")).startsWith("Renamed");
        assertThat(templatesOfTenant()).isZero();
    }

    @Test
    void admin_postWithBlankName_answers422AndStoresNothing() throws Exception {
        mockMvc.perform(in(tenant, post(TEMPLATES_PATH), admin.jwt(), fileTemplateBody(" ")))
                .andExpect(status().isUnprocessableEntity());

        assertThat(templatesOfTenant()).isEqualTo(1L);
    }

    @Test
    void admin_postWithTakenNameDifferingInCase_answers409() throws Exception {
        MvcResult result = mockMvc.perform(in(tenant, post(TEMPLATES_PATH), admin.jwt(),
                fileTemplateBody(templateName.toUpperCase()))).andExpect(status().isConflict()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("OTK_STA_009");
        assertThat(templatesOfTenant()).isEqualTo(1L);
    }

    @Test
    void admin_filesThatAreNotAPdf_answers422BeforeAnythingIsStored() throws Exception {
        MockMultipartFile html = new MockMultipartFile("html", "a.html", "text/html", "<p/>".getBytes());
        MockMultipartFile notPdf = new MockMultipartFile("pdf", "a.pdf", "application/pdf", "plain".getBytes());

        MvcResult result = mockMvc.perform(in(tenant,
                multipart(HttpMethod.PUT, TEMPLATES_PATH + "/" + templateId + "/files").file(html).file(notPdf),
                admin.jwt(), null)).andExpect(status().isUnprocessableEntity()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("OTK_VAL_009");
    }
}
