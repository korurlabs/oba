package com.korur.oba.apps.otak.outreach.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.support.AbstractOtakDocumentsIT;
import com.korur.oba.connect.notification.client.MsGraphClient;
import com.korur.oba.connect.notification.dto.Mail;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * Proves, against the real blob store, the paths that need stored files: the PDF of a ready
 * file template copied onto a new MAIL outreach's mail, the refusal to snapshot a document
 * without files, the send of those attachments, and their removal with the outreach. The blob store is Azurite
 * of the local stack; the Graph client is replaced, so no mail leaves.
 */
class OutreachBlobIT extends AbstractOtakDocumentsIT {

    private static final String PDF_CONTENT = "%PDF-1.4 test";

    @MockitoBean
    private MsGraphClient msGraphClient;

    private Tenant tenant;
    private Person admin;
    private String relationId;
    private String contactId;

    @BeforeEach
    void setUpTenant() throws Exception {
        tenant = newTenant();
        admin = signInAs(tenant, OrganizationMemberRole.ADMIN, "otakadmin");
        relationId = createRelation(tenant, admin, "Relation " + uniqueSuffix());
        contactId = createContact(tenant, admin, relationId, "jan" + uniqueSuffix() + "@fabrikam.example");
    }

    // Each template gets PDF bytes of its own: storage refuses the same content twice on one mail.
    private static String pdfContent(String templateName) {
        return PDF_CONTENT + " " + templateName;
    }

    private void uploadFiles(String path, String pdfContent) throws Exception {
        MockMultipartFile html = new MockMultipartFile("html", "a.html", "text/html",
                "<p>Hello</p>".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile pdf = new MockMultipartFile("pdf", "a.pdf", "application/pdf",
                pdfContent.getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(in(tenant, multipart(HttpMethod.PUT, path + "/files").file(html).file(pdf), admin.jwt(), null))
                .andExpect(status().isOk());
    }

    private String createReadyTemplate(String name) throws Exception {
        String templateId = createFileTemplate(tenant, admin, name);
        uploadFiles(TEMPLATES_PATH + "/" + templateId, pdfContent(name));
        return templateId;
    }

    private String createMailSender() {
        return jdbcTemplate.queryForObject("INSERT INTO otak.mail_senders (mail_address, added_by, organization_id, type) "
                + "VALUES (?, ?::uuid, ?::uuid, 'PERSONAL') RETURNING id::text", String.class,
                "sales" + uniqueSuffix() + "@" + tenant.domain(), admin.userId(), tenant.id());
    }

    private static String idArray(String... ids) {
        return "[" + String.join(",", java.util.Arrays.stream(ids).map((String id) -> "\"" + id + "\"").toList()) + "]";
    }

    private long mailAttachments(String mailId) {
        return count("SELECT count(*) FROM shrine.attachments WHERE entity_type = 'MAIL'::shrine_storage_entity_type_enum "
                + "AND entity_id = ?::uuid", mailId);
    }

    private MvcResult createMailOutreachWithTemplates(String title, String... documentTemplateIds) throws Exception {
        return mockMvc.perform(in(tenant, post(OUTREACHES_PATH), admin.jwt(),
                        "{\"relationId\":\"" + relationId + "\",\"contactId\":\"" + contactId
                                + "\",\"type\":\"MAIL\",\"title\":\"" + title + "\","
                                + "\"mail\":{\"language\":\"NL\",\"subject\":\"Subject\",\"body\":\"Body\"},"
                                + "\"documentTemplateIds\":" + idArray(documentTemplateIds) + "}"))
                .andExpect(status().isCreated()).andReturn();
    }

    @Test
    void admin_snapshotOfADocumentWithoutFiles_answers422DocVal003AndStoresNothing() throws Exception {
        String legalEntityId = createLegalEntity(tenant, admin, "Korur " + uniqueSuffix().substring(0, 6));
        String documentId = read(mockMvc.perform(in(tenant, post("/documents"), admin.jwt(),
                        "{\"entityType\":\"RELATION\",\"entityId\":\"" + relationId + "\",\"legalEntityId\":\""
                                + legalEntityId + "\",\"title\":\"Empty\",\"type\":\"PROPOSAL\",\"language\":\"EN\"}"))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
        String mailId = read(createOutreachResult(tenant, admin, relationId, contactId, "Empty " + uniqueSuffix()),
                "$.data.mail.id");

        MvcResult result = mockMvc.perform(in(tenant, post("/documents/" + documentId + "/attachments"),
                        admin.jwt(), "{\"entityType\":\"MAIL\",\"entityId\":\"" + mailId + "\"}"))
                .andExpect(status().isUnprocessableEntity()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("DOC_VAL_003");
        assertThat(mailAttachments(mailId)).isZero();
    }

    @Test
    void admin_createWithDocumentTemplates_copiesEachPdfOntoTheMailOnce() throws Exception {
        String beta = createReadyTemplate("Beta " + uniqueSuffix());
        String alpha = createReadyTemplate("Alpha " + uniqueSuffix());

        MvcResult result = createMailOutreachWithTemplates("With attachments", beta, alpha, beta);

        assertThat(mailAttachments(read(result, "$.data.mail.id"))).isEqualTo(2L);
    }

    @Test
    void admin_deleteOfAnOutreach_removesTheAttachmentsOfItsMail() throws Exception {
        String templateId = createReadyTemplate("Kept " + uniqueSuffix());
        MvcResult created = createMailOutreachWithTemplates("Delete", templateId);
        String mailId = read(created, "$.data.mail.id");

        mockMvc.perform(in(tenant, delete(OUTREACHES_PATH + "/" + read(created, "$.data.id")), admin.jwt(), null))
                .andExpect(status().isOk());

        assertThat(mailAttachments(mailId)).isZero();
        assertThat(count("SELECT count(*) FROM shrine.document_templates WHERE id = ?::uuid", templateId))
                .isEqualTo(1L);
    }

    @Test
    void admin_sendWithAttachments_sendsThePdfsInUploadOrder() throws Exception {
        String betaName = "Beta " + uniqueSuffix();
        String beta = createReadyTemplate(betaName);
        String alpha = createReadyTemplate("Alpha " + uniqueSuffix());
        MvcResult created = createMailOutreachWithTemplates("Send", beta, alpha);
        String outreachId = read(created, "$.data.id");
        String senderId = createMailSender();

        MvcResult sent = mockMvc.perform(in(tenant, post(OUTREACHES_PATH + "/" + outreachId + "/send"), admin.jwt(),
                        "{\"mailSenderId\":\"" + senderId + "\"}"))
                .andExpect(status().isOk()).andReturn();

        assertThat(read(sent, "$.data.status")).isEqualTo("SENT");
        ArgumentCaptor<Mail> captor = ArgumentCaptor.forClass(Mail.class);
        verify(msGraphClient).sendEmail(captor.capture());
        Mail message = captor.getValue();
        assertThat(message.subject()).isEqualTo("Subject");
        assertThat(message.attachments()).hasSize(2);
        assertThat(message.attachments().get(0).filename()).isEqualTo(betaName + ".pdf");
        assertThat(message.attachments().get(0).contentType()).isEqualTo("application/pdf");
        assertThat(new String(message.attachments().get(0).content(), StandardCharsets.UTF_8))
                .isEqualTo(pdfContent(betaName));
        assertThat(count("SELECT count(*) FROM otak.outreaches WHERE id = ?::uuid AND status::text = 'SENT' "
                + "AND mail_sender_id = ?::uuid", outreachId, senderId)).isEqualTo(1L);
    }

    @Test
    void admin_sendOfACallOutreach_isRefusedBeforeAnyMailIsSent() throws Exception {
        String callId = read(mockMvc.perform(in(tenant, post(OUTREACHES_PATH), admin.jwt(),
                        "{\"relationId\":\"" + relationId + "\",\"contactId\":\"" + contactId
                                + "\",\"type\":\"CALL\",\"title\":\"Call\"}"))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");

        mockMvc.perform(in(tenant, post(OUTREACHES_PATH + "/" + callId + "/send"), admin.jwt(),
                "{\"mailSenderId\":\"" + UUID.randomUUID() + "\"}")).andExpect(status().isUnprocessableEntity());

        verifyNoInteractions(msGraphClient);
    }
}
